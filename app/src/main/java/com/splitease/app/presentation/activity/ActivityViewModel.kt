package com.splitease.app.presentation.activity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FeedQueryLimits
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.presentation.common.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

enum class ActivityKind {
    EXPENSE,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
    PAYMENT,
    GROUP_CREATED,
}

enum class ActivityBalanceTone {
    POSITIVE,
    NEGATIVE,
}

data class ActivityUiItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val sortEpochMs: Long,
    /** Balance line under the title (expenses / some payments). */
    val balanceLabel: String? = null,
    val balanceTone: ActivityBalanceTone? = null,
    /** When set and the expense still exists, Activity can open expense details. */
    val relatedExpenseId: String? = null,
    /** Display name of who added/updated/deleted (expense rows only). */
    val actorDisplayName: String? = null,
    /** Optional avatar URL for [actorDisplayName]. */
    val actorPhotoUrl: String? = null,
    /** Expense description to render semibold inside [title]. */
    val expenseTitle: String? = null,
)

@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository,
        private val friendRepository: FriendRepository,
        private val activityEventRepository: ActivityEventRepository,
        private val syncInteractor: SyncInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val items: StateFlow<List<ActivityUiItem>> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            combine(
                                expenseRepository.observeRecentInvolvingUser(me),
                                paymentRepository.observeRecentInvolvingUser(me),
                                groupRepository.observeGroupsForUser(me),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { expenses, payments, groups, users, friends ->
                                ActivitySources(
                                    expenses = expenses,
                                    payments = payments,
                                    groups = groups,
                                    users = users,
                                    friends = friends,
                                )
                            },
                            activityEventRepository.observeRecentForUser(me),
                        ) { sources, events -> sources to events }
                            .flatMapLatest { (sources, events) ->
                                val expenseIds =
                                    (
                                        sources.expenses.map { it.id } +
                                            events.mapNotNull { it.relatedExpenseId }
                                    ).distinct()
                                expenseRepository.observeSplitsForExpenses(expenseIds).map { splitsByExpenseId ->
                                    buildItems(
                                        me = me,
                                        sources = sources,
                                        events = events,
                                        splitsByExpenseId = splitsByExpenseId,
                                    ).take(FeedQueryLimits.UI_FEED)
                                }.flowOn(Dispatchers.Default)
                            }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            viewModelScope.launch {
                userId.collect { me ->
                    // Soft sync only when Home/other screens have not hydrated recently.
                    if (me != null && !syncInteractor.wasSyncedRecently()) {
                        runCatching { syncInteractor.syncForUser(me) }
                    }
                }
            }
        }

        private fun buildItems(
            me: String,
            sources: ActivitySources,
            events: List<ActivityEvent>,
            splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        ): List<ActivityUiItem> {
            val groupNames = sources.groups.associate { it.id to it.name }
            val userNames = sources.users.associate { it.id to it.displayName }
            val userPhotos = sources.users.associate { it.id to it.photoUrl }
            val friendNames =
                sources.friends.associate { it.friendUserId to it.displayNameSnapshot }
            val expensesById = sources.expenses.associateBy { it.id }

            fun nameOf(id: String): String =
                when (id) {
                    me -> "You"
                    else ->
                        friendNames[id]
                            ?: userNames[id]
                            ?: id.take(8)
                }

            fun photoOf(id: String): String? = userPhotos[id]

            val expenseIdsWithEvents =
                events.mapNotNull { it.relatedExpenseId }.toSet()
            val legacyExpenseItems =
                sources.expenses
                    .filter { it.id !in expenseIdsWithEvents }
                    .map { expense ->
                        expense.toUi(
                            me = me,
                            groupNames = groupNames,
                            nameOf = ::nameOf,
                            photoOf = ::photoOf,
                            splits = splitsByExpenseId[expense.id].orEmpty(),
                        )
                    }
            val eventItems =
                events.map { event ->
                    event.toUi(
                        me = me,
                        groupNames = groupNames,
                        expensesById = expensesById,
                        splitsByExpenseId = splitsByExpenseId,
                        nameOf = ::nameOf,
                        photoOf = ::photoOf,
                    )
                }
            val paymentItems =
                sources.payments.map { payment ->
                    payment.toUi(me, ::nameOf)
                }
            val groupCreatedItems =
                sources.groups
                    .filter { it.createdByUserId == me }
                    .map { it.toCreatedUi() }
            return (legacyExpenseItems + eventItems + paymentItems + groupCreatedItems)
                .sortedByDescending { it.sortEpochMs }
        }

        private fun ActivityEvent.toUi(
            me: String,
            groupNames: Map<String, String>,
            expensesById: Map<String, Expense>,
            splitsByExpenseId: Map<String, List<ExpenseSplit>>,
            nameOf: (String) -> String,
            photoOf: (String) -> String?,
        ): ActivityUiItem {
            val uiKind =
                when (kind) {
                    ActivityEventKind.EXPENSE_ADDED -> ActivityKind.EXPENSE
                    ActivityEventKind.EXPENSE_UPDATED -> ActivityKind.EXPENSE_UPDATED
                    ActivityEventKind.EXPENSE_DELETED -> ActivityKind.EXPENSE_DELETED
                }
            val actorName = nameOf(actorUserId)
            val liveExpense = relatedExpenseId?.let { expensesById[it] }
            val description =
                liveExpense?.description
                    ?: title.removePrefix("Updated: ").removePrefix("Deleted: ").trim()
            val contextLabel =
                liveExpense?.groupId?.let { groupNames[it] }
                    ?: subtitle.split(" · ").firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.non_group_expenses)
            val titleLine =
                when (kind) {
                    ActivityEventKind.EXPENSE_ADDED ->
                        appContext.getString(R.string.activity_added_in, actorName, description, contextLabel)
                    ActivityEventKind.EXPENSE_UPDATED ->
                        appContext.getString(R.string.activity_updated_in, actorName, description, contextLabel)
                    ActivityEventKind.EXPENSE_DELETED ->
                        appContext.getString(R.string.activity_deleted_in, actorName, description, contextLabel)
                }
            val (balanceLabel, balanceTone) =
                if (liveExpense != null) {
                    balanceLine(
                        me = me,
                        expense = liveExpense,
                        splits = splitsByExpenseId[liveExpense.id].orEmpty(),
                    )
                } else {
                    null to null
                }
            return ActivityUiItem(
                id = "event-$id",
                kind = uiKind,
                title = titleLine,
                subtitle = formatDateTime(sortEpochMs),
                amountLabel = "",
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = sortEpochMs,
                relatedExpenseId =
                    relatedExpenseId.takeIf { uiKind != ActivityKind.EXPENSE_DELETED },
                actorDisplayName = actorName,
                actorPhotoUrl = photoOf(actorUserId),
                expenseTitle = description,
            )
        }

        private fun Group.toCreatedUi(): ActivityUiItem =
            ActivityUiItem(
                id = "group-created-$id",
                kind = ActivityKind.GROUP_CREATED,
                title = appContext.getString(R.string.activity_you_created, name),
                subtitle = formatDateTime(createdAtEpochMs),
                amountLabel = "",
                sortEpochMs = createdAtEpochMs,
            )

        private fun Expense.toUi(
            me: String,
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
            photoOf: (String) -> String?,
            splits: List<ExpenseSplit>,
        ): ActivityUiItem {
            val contextLabel =
                groupId?.let { groupNames[it] } ?: appContext.getString(R.string.non_group_expenses)
            val actorName = nameOf(paidByUserId)
            val displayEpochMs = expenseDateEpochMs.takeIf { it > 0L } ?: createdAtEpochMs
            val (balanceLabel, balanceTone) = balanceLine(me, this, splits)
            return ActivityUiItem(
                id = "expense-$id",
                kind = ActivityKind.EXPENSE,
                title =
                    appContext.getString(
                        R.string.activity_added_in,
                        actorName,
                        description,
                        contextLabel,
                    ),
                subtitle = formatDateTime(displayEpochMs),
                amountLabel = "",
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = displayEpochMs,
                relatedExpenseId = id,
                actorDisplayName = actorName,
                actorPhotoUrl = photoOf(paidByUserId),
                expenseTitle = description,
            )
        }

        private fun balanceLine(
            me: String,
            expense: Expense,
            splits: List<ExpenseSplit>,
        ): Pair<String?, ActivityBalanceTone?> {
            val zero = BigDecimal.ZERO.setScale(2)
            val net = BalanceCalculator.viewerNetForExpense(me, expense, splits)
            val money = MoneyFormat.format(net.abs(), expense.currencyCode)
            return when {
                net > zero ->
                    appContext.getString(R.string.activity_you_get_back, money) to
                        ActivityBalanceTone.POSITIVE
                net < zero ->
                    appContext.getString(R.string.activity_you_owe, money) to
                        ActivityBalanceTone.NEGATIVE
                else -> null to null
            }
        }

        private fun Payment.toUi(
            me: String,
            nameOf: (String) -> String,
        ): ActivityUiItem {
            val title =
                when {
                    fromUserId == me -> appContext.getString(R.string.payment_completed_you_paid, nameOf(toUserId))
                    toUserId == me -> appContext.getString(R.string.payment_completed_they_paid, nameOf(fromUserId))
                    else -> appContext.getString(R.string.payment_completed_other, nameOf(fromUserId), nameOf(toUserId))
                }
            val money = MoneyFormat.format(amount, currencyCode)
            val balanceLabel =
                when {
                    toUserId == me ->
                        appContext.getString(R.string.activity_you_received, money)
                    fromUserId == me ->
                        appContext.getString(R.string.activity_you_paid, money)
                    else -> null
                }
            val balanceTone =
                when {
                    toUserId == me -> ActivityBalanceTone.POSITIVE
                    else -> null
                }
            return ActivityUiItem(
                id = "payment-$id",
                kind = ActivityKind.PAYMENT,
                title = title,
                subtitle = formatDateTime(paidAtEpochMs),
                amountLabel = "",
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = paidAtEpochMs.coerceAtLeast(createdAtEpochMs),
            )
        }

        private fun formatDateTime(epochMs: Long): String =
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(epochMs))
    }

private data class ActivitySources(
    val expenses: List<Expense>,
    val payments: List<Payment>,
    val groups: List<Group>,
    val users: List<User>,
    val friends: List<Friend>,
)
