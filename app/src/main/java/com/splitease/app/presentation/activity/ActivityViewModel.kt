package com.splitease.app.presentation.activity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

data class ActivityUiItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val sortEpochMs: Long,
    /** When set and the expense still exists, Activity can open expense details. */
    val relatedExpenseId: String? = null,
    /** Display name of who added/updated/deleted (expense rows only). */
    val actorDisplayName: String? = null,
    /** Optional avatar URL for [actorDisplayName]. */
    val actorPhotoUrl: String? = null,
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
                                expenseRepository.observeInvolvingUser(me),
                                paymentRepository.observeInvolvingUser(me),
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
                            activityEventRepository.observeForUser(me),
                        ) { sources, events ->
                            val groupNames = sources.groups.associate { it.id to it.name }
                            val userNames = sources.users.associate { it.id to it.displayName }
                            val userPhotos = sources.users.associate { it.id to it.photoUrl }
                            val friendNames =
                                sources.friends.associate { it.friendUserId to it.displayNameSnapshot }

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
                                        expense.toUi(groupNames, ::nameOf, ::photoOf)
                                    }
                            val eventItems = events.map { it.toUi(::nameOf, ::photoOf) }
                            val paymentItems =
                                sources.payments.map { payment ->
                                    payment.toUi(me, groupNames, ::nameOf)
                                }
                            val groupCreatedItems =
                                sources.groups
                                    .filter { it.createdByUserId == me }
                                    .map { it.toCreatedUi() }
                            (legacyExpenseItems + eventItems + paymentItems + groupCreatedItems)
                                .sortedByDescending { it.sortEpochMs }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            viewModelScope.launch {
                userId.collect { me ->
                    if (me != null) {
                        runCatching { syncInteractor.syncForUser(me) }
                    }
                }
            }
        }

        private fun ActivityEvent.toUi(
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
            val byLabel =
                when (kind) {
                    ActivityEventKind.EXPENSE_ADDED ->
                        appContext.getString(R.string.activity_added_by, actorName)
                    ActivityEventKind.EXPENSE_UPDATED ->
                        appContext.getString(R.string.activity_updated_by, actorName)
                    ActivityEventKind.EXPENSE_DELETED ->
                        appContext.getString(R.string.activity_deleted_by, actorName)
                }
            return ActivityUiItem(
                id = "event-$id",
                kind = uiKind,
                title = title,
                subtitle = subtitleWithActor(subtitle, byLabel),
                amountLabel = amountLabel,
                sortEpochMs = sortEpochMs,
                relatedExpenseId =
                    relatedExpenseId.takeIf { uiKind != ActivityKind.EXPENSE_DELETED },
                actorDisplayName = actorName,
                actorPhotoUrl = photoOf(actorUserId),
            )
        }

        private fun Group.toCreatedUi(): ActivityUiItem {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAtEpochMs))
            return ActivityUiItem(
                id = "group-created-$id",
                kind = ActivityKind.GROUP_CREATED,
                title = appContext.getString(R.string.activity_you_created, name),
                subtitle = date,
                amountLabel = "",
                sortEpochMs = createdAtEpochMs,
            )
        }

        private fun Expense.toUi(
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
            photoOf: (String) -> String?,
        ): ActivityUiItem {
            val context =
                groupId?.let { groupNames[it] } ?: appContext.getString(R.string.non_group_expenses)
            val actorName = nameOf(paidByUserId)
            val addedBy = appContext.getString(R.string.activity_added_by, actorName)
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expenseDateEpochMs))
            return ActivityUiItem(
                id = "expense-$id",
                kind = ActivityKind.EXPENSE,
                title = description,
                subtitle = "$context · $addedBy · $date",
                amountLabel = "$currencyCode ${amount.toPlainString()}",
                sortEpochMs = expenseDateEpochMs.coerceAtLeast(createdAtEpochMs),
                relatedExpenseId = id,
                actorDisplayName = actorName,
                actorPhotoUrl = photoOf(paidByUserId),
            )
        }

        /** Inserts the actor label before the trailing date segment when present. */
        private fun subtitleWithActor(
            subtitle: String,
            byLabel: String,
        ): String {
            if (subtitle.isBlank()) return byLabel
            val parts = subtitle.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
            return if (parts.size >= 2) {
                (parts.dropLast(1) + byLabel + parts.last()).joinToString(" · ")
            } else {
                "$subtitle · $byLabel"
            }
        }

        private fun Payment.toUi(
            me: String,
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
        ): ActivityUiItem {
            val title =
                when {
                    fromUserId == me -> appContext.getString(R.string.payment_completed_you_paid, nameOf(toUserId))
                    toUserId == me -> appContext.getString(R.string.payment_completed_they_paid, nameOf(fromUserId))
                    else -> appContext.getString(R.string.payment_completed_other, nameOf(fromUserId), nameOf(toUserId))
                }
            val context = groupId?.let { groupNames[it] }
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(paidAtEpochMs))
            val parts =
                listOfNotNull(
                    context,
                    note?.takeIf { it.isNotBlank() },
                    date,
                )
            return ActivityUiItem(
                id = "payment-$id",
                kind = ActivityKind.PAYMENT,
                title = title,
                subtitle = parts.joinToString(" · "),
                amountLabel = "$currencyCode ${amount.toPlainString()}",
                sortEpochMs = paidAtEpochMs.coerceAtLeast(createdAtEpochMs),
            )
        }
    }

private data class ActivitySources(
    val expenses: List<Expense>,
    val payments: List<Payment>,
    val groups: List<Group>,
    val users: List<User>,
    val friends: List<Friend>,
)
