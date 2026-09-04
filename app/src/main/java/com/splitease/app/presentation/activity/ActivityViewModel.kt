package com.splitease.app.presentation.activity

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.data.sync.SyncState
import com.splitease.app.data.sync.shouldFreezeBalances
import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Payment
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.system.measureTimeMillis

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

enum class ActivityListFilter {
    ALL,
    EXPENSE,
    SETTLEMENTS,
    GROUPS,
}

@Immutable
data class ActivityUiItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    /**
     * Newest-first sort key. May be bumped 1ms past the parent group's created
     * time so an expense cannot sort as if it predates the group; [timeLabel]
     * still uses the real event time.
     */
    val sortEpochMs: Long,
    /** Pre-formatted time shown at the end of each row. */
    val timeLabel: String,
    /** Balance line under the title (expenses / some payments). */
    val balanceLabel: String? = null,
    val balanceTone: ActivityBalanceTone? = null,
    /** When set and the expense still exists, Activity can open expense details. */
    val relatedExpenseId: String? = null,
    /** Expense description to render semibold inside [title]. */
    val expenseTitle: String? = null,
)

@Immutable
sealed interface ActivityListEntry {
    data class DayHeader(val day: java.time.LocalDate) : ActivityListEntry

    data class Row(val item: ActivityUiItem) : ActivityListEntry
}

@Immutable
data class ActivityFeedState(
    val entries: List<ActivityListEntry> = emptyList(),
    val hasAnyItems: Boolean = false,
    val isFiltered: Boolean = false,
    /** First-login full hydrate phase; subsequent opens stay [SyncState.IDLE]. */
    val syncState: SyncState = SyncState.IDLE,
)

@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
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

        init {
            viewModelScope.launch {
                userId.collect { id ->
                    if (id == null) {
                        return@collect
                    }
                    withContext(Dispatchers.IO) {
                        if (!syncInteractor.hasCompletedInitialHydrate(id)) {
                            syncInteractor.markInitialHydrateStarted(id)
                        }
                    }
                    launch(Dispatchers.IO) {
                        runCatching { syncInteractor.syncForUser(id) }
                    }
                }
            }
        }

        private val searchQueryFlow =
            savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")

        /** Immediate query for the search field. */
        val searchQuery: StateFlow<String> = searchQueryFlow

        val listFilter: StateFlow<ActivityListFilter> =
            savedStateHandle
                .getStateFlow(KEY_LIST_FILTER, ActivityListFilter.ALL.name)
                .map(::parseListFilter)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    parseListFilter(savedStateHandle[KEY_LIST_FILTER] ?: ActivityListFilter.ALL.name),
                )

        private val debouncedSearchQuery: StateFlow<String> =
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    savedStateHandle.get<String>(KEY_SEARCH_QUERY).orEmpty(),
                )

        @OptIn(ExperimentalCoroutinesApi::class)
        private val items: StateFlow<List<ActivityUiItem>> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(emptyList())
                    } else {
                        syncInteractor.syncState.flatMapLatest { sync ->
                            if (sync.shouldFreezeBalances) {
                                flowOf(emptyList())
                            } else {
                                observeLiveFeed(me)
                            }
                        }
                    }
                }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val feed: StateFlow<ActivityFeedState> =
            combine(
                items,
                listFilter,
                debouncedSearchQuery,
                syncInteractor.syncState,
            ) { allItems, filter, query, sync ->
                val visible =
                    allItems.filter { item ->
                        item.matches(filter) && item.matchesQuery(query)
                    }
                ActivityFeedState(
                    entries = buildActivityListEntries(visible),
                    hasAnyItems = allItems.isNotEmpty(),
                    isFiltered = filter != ActivityListFilter.ALL || query.isNotBlank(),
                    syncState = sync,
                )
            }.flowOn(Dispatchers.Default)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    ActivityFeedState(syncState = syncInteractor.syncState.value),
                )

        fun setListFilter(filter: ActivityListFilter) {
            savedStateHandle[KEY_LIST_FILTER] = filter.name
        }

        fun setSearchQuery(query: String) {
            savedStateHandle[KEY_SEARCH_QUERY] = query
        }

        /**
         * Retries a failed first-login hydrate. The list stays on skeleton until COMPLETE.
         */
        fun retryInitialHydrate() {
            val id = userId.value ?: return
            if (syncInteractor.syncState.value == SyncState.IN_PROGRESS) return
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    syncInteractor.markInitialHydrateStarted(id)
                    runCatching { syncInteractor.syncForUser(id, force = true) }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeLiveFeed(me: String): Flow<List<ActivityUiItem>> {
            val sourcesFlow =
                combine(
                    expenseRepository
                        .observeRecentInvolvingUser(me)
                        .onEach { rows ->
                            ActivityPerfLog.emit("expenses", "count=${rows.size}")
                        },
                    paymentRepository
                        .observeRecentInvolvingUser(me)
                        .onEach { rows ->
                            ActivityPerfLog.emit("payments", "count=${rows.size}")
                        },
                    groupRepository
                        .observeGroupsForUser(me)
                        .onEach { rows ->
                            ActivityPerfLog.emit("groups", "count=${rows.size}")
                        },
                    userRepository
                        .observeUsers()
                        .map { users -> users.associate { it.id to it.displayName } }
                        .distinctUntilChanged()
                        .onEach { map ->
                            ActivityPerfLog.emit("users", "count=${map.size}")
                        },
                    friendRepository
                        .observeFriends(me)
                        .onEach { rows ->
                            ActivityPerfLog.emit("friends", "count=${rows.size}")
                        },
                ) { expenses, payments, groups, userNames, friends ->
                    ActivitySources(
                        expenses = expenses,
                        payments = payments,
                        groups = groups,
                        userNames = userNames,
                        friends = friends,
                    )
                }

            val eventsFlow =
                activityEventRepository
                    .observeRecentForUser(me)
                    .onEach { rows ->
                        ActivityPerfLog.emit("events", "count=${rows.size}")
                    }

            val feedInputsFlow =
                combine(sourcesFlow, eventsFlow) { sources, events ->
                    ActivityFeedInputs(
                        sources = sources,
                        events = events,
                        expenseIds = expenseIdsForFeed(sources, events),
                    )
                }

            val expenseIdsFlow =
                feedInputsFlow
                    .map { it.expenseIds }
                    .distinctUntilChanged()

            val splitsFlow =
                expenseIdsFlow.flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        expenseRepository
                            .observeSplitsForExpenses(ids)
                            .onEach { splits ->
                                ActivityPerfLog.emit(
                                    "splits",
                                    "expenses=${ids.size} loaded=${splits.size}",
                                )
                            }
                    }
                }

            return combine(feedInputsFlow, splitsFlow) { inputs, splitsByExpenseId ->
                inputs to splitsByExpenseId
            }.distinctUntilChanged { old, new ->
                old.first.rebuildKey(old.second) == new.first.rebuildKey(new.second)
            }.map { (inputs, splitsByExpenseId) ->
                var built: List<ActivityUiItem>
                val elapsed =
                    measureTimeMillis {
                        built =
                            buildItems(
                                me = me,
                                sources = inputs.sources,
                                events = inputs.events,
                                splitsByExpenseId = splitsByExpenseId,
                            ).take(FeedQueryLimits.UI_FEED)
                    }
                ActivityPerfLog.rebuild(
                    reason = "feed-combine",
                    itemCount = built.size,
                    elapsedMs = elapsed,
                    signatureChanged = true,
                )
                built
            }.distinctUntilChanged { old, new ->
                val same = old.contentSignature() == new.contentSignature()
                if (!same) {
                    ActivityPerfLog.scrollJumpSuspect(
                        previousCount = old.size,
                        newCount = new.size,
                        previousTopId = old.firstOrNull()?.id,
                        newTopId = new.firstOrNull()?.id,
                    )
                }
                same
            }
        }

        private fun expenseIdsForFeed(
            sources: ActivitySources,
            events: List<ActivityEvent>,
        ): List<String> =
            (
                sources.expenses.map { it.id } +
                    events.mapNotNull { it.relatedExpenseId }
            ).distinct().sorted()

        private fun buildItems(
            me: String,
            sources: ActivitySources,
            events: List<ActivityEvent>,
            splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        ): List<ActivityUiItem> {
            val groupNames = sources.groups.associate { it.id to it.name }
            val friendNames =
                sources.friends.associate { it.friendUserId to it.displayNameSnapshot }
            val expensesById = sources.expenses.associateBy { it.id }

            fun nameOf(id: String): String =
                when (id) {
                    me -> "You"
                    else ->
                        friendNames[id]
                            ?: sources.userNames[id]
                            ?: id.take(8)
                }

            val groupCreatedEpochs = sources.groups.associate { it.id to it.createdAtEpochMs }
            val expenseIdsWithEvents =
                events.mapNotNull { it.relatedExpenseId }.toSet()
            val legacyExpenseItems =
                sources.expenses
                    .filter { it.id !in expenseIdsWithEvents }
                    .map { expense ->
                        expense.toUi(
                            me = me,
                            groupNames = groupNames,
                            groupCreatedEpochs = groupCreatedEpochs,
                            nameOf = ::nameOf,
                            splits = splitsByExpenseId[expense.id].orEmpty(),
                        )
                    }
            val eventItems =
                events.map { event ->
                    event.toUi(
                        me = me,
                        groupNames = groupNames,
                        groupCreatedEpochs = groupCreatedEpochs,
                        expensesById = expensesById,
                        splitsByExpenseId = splitsByExpenseId,
                        nameOf = ::nameOf,
                    )
                }
            val paymentItems =
                sources.payments.map { payment ->
                    payment.toUi(me, ::nameOf)
                }
            val groupCreatedItems =
                sources.groups
                    .filter { it.createdByUserId == me }
                    .sortedByDescending { it.createdAtEpochMs }
                    .take(FeedQueryLimits.UI_FEED)
                    .map { it.toCreatedUi() }
            return (legacyExpenseItems + eventItems + paymentItems + groupCreatedItems)
                .sortedWith(
                    compareByDescending<ActivityUiItem> { it.sortEpochMs }
                        .thenByDescending { if (it.kind == ActivityKind.GROUP_CREATED) 0 else 1 },
                )
        }

        private fun ActivityEvent.toUi(
            me: String,
            groupNames: Map<String, String>,
            groupCreatedEpochs: Map<String, Long>,
            expensesById: Map<String, Expense>,
            splitsByExpenseId: Map<String, List<ExpenseSplit>>,
            nameOf: (String) -> String,
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
            val groupCreatedAt = liveExpense?.groupId?.let { groupCreatedEpochs[it] } ?: 0L
            val displayEpochMs = sortEpochMs
            val effectiveSortMs =
                if (groupCreatedAt > 0L) maxOf(sortEpochMs, groupCreatedAt + 1L) else sortEpochMs
            return ActivityUiItem(
                id = "event-$id",
                kind = uiKind,
                title = titleLine,
                subtitle = formatDateTime(displayEpochMs),
                amountLabel = "",
                timeLabel = formatTimeLabel(displayEpochMs),
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = effectiveSortMs,
                relatedExpenseId =
                    relatedExpenseId.takeIf { uiKind != ActivityKind.EXPENSE_DELETED },
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
                timeLabel = formatTimeLabel(createdAtEpochMs),
                sortEpochMs = createdAtEpochMs,
            )

        private fun Expense.toUi(
            me: String,
            groupNames: Map<String, String>,
            groupCreatedEpochs: Map<String, Long>,
            nameOf: (String) -> String,
            splits: List<ExpenseSplit>,
        ): ActivityUiItem {
            val contextLabel =
                groupId?.let { groupNames[it] } ?: appContext.getString(R.string.non_group_expenses)
            val actorName = nameOf(paidByUserId)
            val groupCreatedAt = groupId?.let { groupCreatedEpochs[it] } ?: 0L
            val baseEpochMs = createdAtEpochMs.takeIf { it > 0L } ?: expenseDateEpochMs
            val displayEpochMs = baseEpochMs
            val effectiveSortMs =
                if (groupCreatedAt > 0L) maxOf(baseEpochMs, groupCreatedAt + 1L) else baseEpochMs
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
                timeLabel = formatTimeLabel(displayEpochMs),
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = effectiveSortMs,
                relatedExpenseId = id,
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
            val sortMs = paidAtEpochMs.coerceAtLeast(createdAtEpochMs)
            return ActivityUiItem(
                id = "payment-$id",
                kind = ActivityKind.PAYMENT,
                title = title,
                subtitle = formatDateTime(sortMs),
                amountLabel = "",
                timeLabel = formatTimeLabel(sortMs),
                balanceLabel = balanceLabel,
                balanceTone = balanceTone,
                sortEpochMs = sortMs,
            )
        }

        private fun formatDateTime(epochMs: Long): String =
            DATE_TIME_FORMAT.get().format(Date(epochMs))

        private fun formatTimeLabel(epochMs: Long): String =
            timeFormatter().format(
                Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
            )

        private fun timeFormatter(): DateTimeFormatter {
            val locale = appContext.resources.configuration.locales[0]
            if (cachedTimeLocale != locale) {
                cachedTimeLocale = locale
                cachedTimeFormatter =
                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            }
            return cachedTimeFormatter!!
        }

        private companion object {
            private const val KEY_LIST_FILTER = "activity_list_filter"
            private const val KEY_SEARCH_QUERY = "activity_search_query"
            private const val SEARCH_DEBOUNCE_MS = 200L

            private val DATE_TIME_FORMAT =
                ThreadLocal.withInitial {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                }
        }

        private var cachedTimeLocale: Locale? = null
        private var cachedTimeFormatter: DateTimeFormatter? = null
    }

private data class ActivitySources(
    val expenses: List<Expense>,
    val payments: List<Payment>,
    val groups: List<Group>,
    val userNames: Map<String, String>,
    val friends: List<Friend>,
)

private data class ActivityFeedInputs(
    val sources: ActivitySources,
    val events: List<ActivityEvent>,
    val expenseIds: List<String>,
)

private fun ActivityFeedInputs.relevantActorIds(): Set<String> {
    val ids = linkedSetOf<String>()
    sources.expenses.forEach { ids.add(it.paidByUserId) }
    sources.payments.forEach {
        ids.add(it.fromUserId)
        ids.add(it.toUserId)
    }
    events.forEach { ids.add(it.actorUserId) }
    return ids
}

private fun ActivityFeedInputs.rebuildKey(splitsByExpenseId: Map<String, List<ExpenseSplit>>): String {
    val sb = StringBuilder(4096)
    val actorIds = relevantActorIds()
    sources.expenses.forEach { expense ->
        sb.append('E')
            .append(expense.id)
            .append(expense.updatedAtEpochMs)
            .append(expense.amount)
            .append(expense.description)
            .append(expense.paidByUserId)
            .append(expense.groupId)
            .append(expense.expenseDateEpochMs)
            .append(expense.createdAtEpochMs)
            .append(expense.currencyCode)
    }
    sources.payments.forEach { payment ->
        sb.append('P')
            .append(payment.id)
            .append(payment.paidAtEpochMs)
            .append(payment.amount)
            .append(payment.fromUserId)
            .append(payment.toUserId)
            .append(payment.currencyCode)
            .append(payment.createdAtEpochMs)
    }
    events.forEach { event ->
        sb.append('V')
            .append(event.id)
            .append(event.sortEpochMs)
            .append(event.kind)
            .append(event.actorUserId)
            .append(event.relatedExpenseId)
            .append(event.title)
            .append(event.subtitle)
    }
    sources.groups.forEach { group ->
        sb.append('G')
            .append(group.id)
            .append(group.name)
            .append(group.createdAtEpochMs)
            .append(group.createdByUserId)
    }
    sources.friends
        .filter { it.friendUserId in actorIds }
        .forEach { friend ->
            sb.append('F')
                .append(friend.friendUserId)
                .append(friend.displayNameSnapshot)
        }
    sources.userNames
        .filterKeys { it in actorIds }
        .entries
        .sortedBy { it.key }
        .forEach { (id, name) ->
            sb.append('U').append(id).append(name)
        }
    expenseIds.forEach { id ->
        splitsByExpenseId[id]?.sortedBy { it.userId }?.forEach { split ->
            sb.append('S')
                .append(id)
                .append(split.userId)
                .append(split.owedAmount)
        }
    }
    return sb.toString()
}

private fun buildActivityListEntries(items: List<ActivityUiItem>): List<ActivityListEntry> =
    items
        .groupBy { dayKey(it.sortEpochMs) }
        .entries
        .sortedByDescending { (day, _) -> day }
        .flatMap { (day, dayItems) ->
            buildList {
                add(ActivityListEntry.DayHeader(day))
                dayItems.forEach { add(ActivityListEntry.Row(it)) }
            }
        }

private fun dayKey(epochMs: Long): java.time.LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun ActivityListEntry.stableKey(): String =
    when (this) {
        is ActivityListEntry.DayHeader -> "day-$day"
        is ActivityListEntry.Row -> item.id
    }

private fun parseListFilter(name: String): ActivityListFilter =
    runCatching { ActivityListFilter.valueOf(name) }.getOrDefault(ActivityListFilter.ALL)

private fun ActivityUiItem.matches(filter: ActivityListFilter): Boolean =
    when (filter) {
        ActivityListFilter.ALL -> true
        ActivityListFilter.EXPENSE ->
            kind == ActivityKind.EXPENSE ||
                kind == ActivityKind.EXPENSE_UPDATED ||
                kind == ActivityKind.EXPENSE_DELETED
        ActivityListFilter.SETTLEMENTS -> kind == ActivityKind.PAYMENT
        ActivityListFilter.GROUPS -> kind == ActivityKind.GROUP_CREATED
    }

private fun ActivityUiItem.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        subtitle.contains(needle, ignoreCase = true) ||
        amountLabel.contains(needle, ignoreCase = true) ||
        (balanceLabel?.contains(needle, ignoreCase = true) == true) ||
        (expenseTitle?.contains(needle, ignoreCase = true) == true)
}

/** Stable fingerprint so identical UI rows do not trigger LazyColumn relayout. */
private fun List<ActivityUiItem>.contentSignature(): List<String> =
    map { item ->
        buildString {
            append(item.id)
            append('|')
            append(item.sortEpochMs)
            append('|')
            append(item.title)
            append('|')
            append(item.balanceLabel.orEmpty())
            append('|')
            append(item.timeLabel)
        }
    }
