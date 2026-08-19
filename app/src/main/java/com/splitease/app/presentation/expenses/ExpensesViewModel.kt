package com.splitease.app.presentation.expenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.expense.CreateExpenseInput
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.expense.resolvedDisplayUri
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.sync.GroupLiveSync
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseComment
import com.splitease.app.domain.model.ExpenseCommentKind
import com.splitease.app.domain.model.ExpensePhoto
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseCommentRepository
import com.splitease.app.domain.repository.ExpensePhotoRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.domain.spending.GroupMonthSpending
import com.splitease.app.domain.spending.GroupSpendingCalculator
import com.splitease.app.presentation.common.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ExpensesUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val isAttachingPhotos: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

/**
 * Viewer-facing balance tone for a ledger row.
 */
enum class LedgerBalanceSide {
    /** Expense: others owe you. */
    LENT,

    /** Expense: you owe the payer. */
    BORROWED,

    /** Payment: you received money. */
    RECEIVED,

    /** Payment: you paid money. */
    PAID,
}

/**
 * Unified expense/payment row for group and friend timelines.
 *
 * Layout:
 * 1. Date from [sortEpochMs] (month + day)
 * 2. [title] — expense description (or payment summary)
 * 3. [subtitle] — e.g. "You paid ₹550.00"
 * 4. balance from [balanceSide]/[balanceAmount] — lent / borrowed / received / paid
 */
data class LedgerListItem(
    val id: String,
    val isPayment: Boolean,
    val title: String,
    val subtitle: String,
    val sortEpochMs: Long,
    val categoryIconKey: String? = null,
    val currencyCode: String = AppCurrencies.DEFAULT,
    val balanceSide: LedgerBalanceSide? = null,
    val balanceAmount: BigDecimal? = null,
    /** True when the row is still waiting on a cloud push. */
    val pendingSync: Boolean = false,
)

data class ParticipantOption(
    val userId: String,
    val label: String,
    val isPendingInvite: Boolean = false,
    val photoUrl: String? = null,
)

data class ExpenseSplitLineUi(
    val userId: String,
    val participantLabel: String,
    val owedAmount: BigDecimal,
    val paidAmount: BigDecimal? = null,
    val percentage: BigDecimal? = null,
    val shares: Int? = null,
    val adjustmentAmount: BigDecimal? = null,
    val photoUrl: String? = null,
)

data class ExpenseDetailUi(
    val expense: Expense,
    val splits: List<ExpenseSplitLineUi>,
    val payerLabel: String,
    val payerPhotoUrl: String? = null,
    val groupName: String?,
    val categoryName: String,
    val categoryIconKey: String,
    /** Viewer's owed share on this expense (null when not a participant). */
    val viewerOwedAmount: BigDecimal? = null,
    val viewerBalanceSide: LedgerBalanceSide? = null,
    val viewerBalanceAmount: BigDecimal? = null,
    /** Last 3 calendar months of group spending (empty when not in a group). */
    val spendingTrendMonths: List<GroupMonthSpending> = emptyList(),
)

data class ExpenseCommentUi(
    val id: String,
    val authorLabel: String,
    val authorPhotoUrl: String?,
    val body: String,
    val kind: ExpenseCommentKind,
    val createdAtEpochMs: Long,
)

data class ExpensePhotoUi(
    val id: String,
    val displayUri: String,
    val createdAtEpochMs: Long,
    val createdByUserId: String,
    val authorLabel: String,
)

private data class LedgerSource(
    val expenses: List<Expense>,
    val payments: List<Payment>,
    val splitsByExpenseId: Map<String, List<ExpenseSplit>>,
    val users: List<User>,
    val friends: List<Friend>,
)

@HiltViewModel
class ExpensesViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
        private val expenseCommentRepository: ExpenseCommentRepository,
        private val expensePhotoRepository: ExpensePhotoRepository,
        private val expenseInteractor: ExpenseInteractor,
        private val paymentInteractor: PaymentInteractor,
        private val syncInteractor: SyncInteractor,
        private val groupRepository: GroupRepository,
        private val friendRepository: FriendRepository,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val paymentRepository: PaymentRepository,
        appSettingsRepository: AppSettingsRepository,
        private val groupLiveSync: GroupLiveSync,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _uiState = MutableStateFlow(ExpensesUiState())
        val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

        val categories: StateFlow<List<Category>> =
            categoryRepository
                .observeCategories()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val currencyCode: StateFlow<String> =
            appSettingsRepository
                .observeCurrencyCode()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrencies.DEFAULT)

        fun currentUserId(): String? = userId.value

        /** Reactive signed-in user id for Compose collectors. */
        val signedInUserId: StateFlow<String?> = userId

        /** Signed-in display name for share/remind copy (never the localized "You"). */
        val currentUserDisplayName: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { session ->
                    (session as? AuthSession.SignedIn)
                        ?.user
                        ?.displayName
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Seeds any missing built-in categories (e.g. Bus/Train added in a later release). */
        fun ensureDefaultCategories() {
            viewModelScope.launch {
                runCatching { categoryRepository.ensureDefaults() }
            }
        }

        suspend fun getGroupName(groupId: String): String? =
            groupRepository.getGroupById(groupId)?.name

        /**
         * True when [groupId] already has at least one expense or settlement payment.
         * Used to skip the first-expense members confirm dialog on non-empty groups.
         */
        suspend fun groupHasLedgerEntries(groupId: String): Boolean {
            val expenses = expenseRepository.observeExpenses(groupId).first()
            if (expenses.isNotEmpty()) return true
            return paymentRepository.observePayments(groupId).first().isNotEmpty()
        }

        private val groupLedgerFlows = ConcurrentHashMap<String, StateFlow<List<LedgerListItem>>>()
        private val friendLedgerFlows = ConcurrentHashMap<String, StateFlow<List<LedgerListItem>>>()
        private val expenseDetailFlows = ConcurrentHashMap<String, StateFlow<ExpenseDetailUi?>>()
        private val expenseCommentFlows = ConcurrentHashMap<String, StateFlow<List<ExpenseCommentUi>>>()
        private val expensePhotoFlows = ConcurrentHashMap<String, StateFlow<List<ExpensePhotoUi>>>()
        private val emptyExpenseDetail = MutableStateFlow<ExpenseDetailUi?>(null)
        private val emptyExpenseComments = MutableStateFlow<List<ExpenseCommentUi>>(emptyList())
        private val emptyExpensePhotos = MutableStateFlow<List<ExpensePhotoUi>>(emptyList())
        private var nonGroupLedgerFlow: StateFlow<List<LedgerListItem>>? = null

        /**
         * Expenses and settlement payments for a group, newest first.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeGroupLedger(groupId: String): StateFlow<List<LedgerListItem>> =
            groupLedgerFlows.getOrPut(groupId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            combine(
                                expenseRepository.observeExpenses(groupId),
                                paymentRepository.observePayments(groupId),
                                expenseRepository.observeSplitsByGroup(groupId),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { expenses, payments, splitsByExpenseId, users, friends ->
                                LedgerSource(
                                    expenses = expenses,
                                    payments = payments,
                                    splitsByExpenseId = splitsByExpenseId,
                                    users = users,
                                    friends = friends,
                                )
                            }.combine(categoryRepository.observeCategories()) { source, categories ->
                                source to categories
                            }.map { (source, categories) ->
                                buildLedger(
                                    expenses = source.expenses,
                                    payments = source.payments,
                                    me = me,
                                    categoryById = categories.associateBy { it.id },
                                    friendNames =
                                        source.friends.associateBy(
                                            { it.friendUserId },
                                            { it.displayNameSnapshot },
                                        ),
                                    userNames = source.users.associateBy({ it.id }, { it.displayName }),
                                    splitsByExpenseId = source.splitsByExpenseId,
                                )
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }

        /**
         * Shared expenses and payments between the signed-in user and [friendUserId]
         * (non-group and shared-group activity).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeFriendLedger(friendUserId: String): StateFlow<List<LedgerListItem>> =
            friendLedgerFlows.getOrPut(friendUserId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            combine(
                                expenseRepository.observeInvolvingUser(me),
                                paymentRepository.observeInvolvingUser(me),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { expenses, payments, users, friends ->
                                FriendLedgerSource(
                                    expenses = expenses,
                                    payments = payments,
                                    users = users,
                                    friends = friends,
                                )
                            }.flatMapLatest { source ->
                                expenseRepository
                                    .observeSplitsForExpenses(source.expenses.map { it.id })
                                    .combine(categoryRepository.observeCategories()) { allSplits, categories ->
                                        val sharedExpenses =
                                            source.expenses.filter { expense ->
                                                val splits = allSplits[expense.id].orEmpty()
                                                val participants =
                                                    (splits.map { it.userId } + expense.paidByUserId)
                                                        .toSet()
                                                me in participants && friendUserId in participants
                                            }
                                        val sharedPayments =
                                            source.payments.filter { payment ->
                                                (payment.fromUserId == me && payment.toUserId == friendUserId) ||
                                                    (payment.fromUserId == friendUserId && payment.toUserId == me)
                                            }
                                        buildLedger(
                                            expenses = sharedExpenses,
                                            payments = sharedPayments,
                                            me = me,
                                            categoryById = categories.associateBy { it.id },
                                            friendNames =
                                                source.friends.associateBy(
                                                    { it.friendUserId },
                                                    { it.displayNameSnapshot },
                                                ),
                                            userNames =
                                                source.users.associateBy({ it.id }, { it.displayName }),
                                            splitsByExpenseId = allSplits,
                                        )
                                    }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }

        private data class FriendLedgerSource(
            val expenses: List<Expense>,
            val payments: List<Payment>,
            val users: List<com.splitease.app.domain.model.User>,
            val friends: List<Friend>,
        )

        /**
         * All non-group expenses and payments involving the signed-in user.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeNonGroupLedger(): StateFlow<List<LedgerListItem>> {
            nonGroupLedgerFlow?.let { return it }
            val flow =
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            combine(
                                expenseRepository.observeInvolvingUser(me),
                                paymentRepository.observeInvolvingUser(me),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { expenses, payments, users, friends ->
                                val nonGroupExpenses = expenses.filter { it.groupId == null }
                                val nonGroupPayments = payments.filter { it.groupId == null }
                                LedgerSource(
                                    expenses = nonGroupExpenses,
                                    payments = nonGroupPayments,
                                    splitsByExpenseId = emptyMap(),
                                    users = users,
                                    friends = friends,
                                )
                            }.flatMapLatest { source ->
                                expenseRepository
                                    .observeSplitsForExpenses(source.expenses.map { it.id })
                                    .combine(categoryRepository.observeCategories()) { splits, categories ->
                                        buildLedger(
                                            expenses = source.expenses,
                                            payments = source.payments,
                                            me = me,
                                            categoryById = categories.associateBy { it.id },
                                            friendNames =
                                                source.friends.associateBy(
                                                    { it.friendUserId },
                                                    { it.displayNameSnapshot },
                                                ),
                                            userNames =
                                                source.users.associateBy({ it.id }, { it.displayName }),
                                            splitsByExpenseId = splits,
                                        )
                                    }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            nonGroupLedgerFlow = flow
            return flow
        }

        private fun buildLedger(
            expenses: List<Expense>,
            payments: List<Payment>,
            me: String?,
            categoryById: Map<String, Category>,
            friendNames: Map<String, String>,
            userNames: Map<String, String>,
            splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        ): List<LedgerListItem> {
            fun nameOf(id: String): String =
                nameOf(id, me, friendNames, userNames)

            val expenseItems =
                expenses.map { expense ->
                    val category = expense.categoryId?.let { categoryById[it] }
                    val payerLabel = nameOf(expense.paidByUserId)
                    val sortEpochMs =
                        if (expense.expenseDateEpochMs > 0L) {
                            expense.expenseDateEpochMs
                        } else {
                            expense.createdAtEpochMs
                        }
                    val (balanceSide, balanceAmount) =
                        viewerBalanceForExpense(
                            me = me,
                            expense = expense,
                            splits = splitsByExpenseId[expense.id].orEmpty(),
                        )
                    LedgerListItem(
                        id = "expense-${expense.id}",
                        isPayment = false,
                        title = expense.description,
                        subtitle =
                            appContext.getString(
                                R.string.ledger_paid_by,
                                payerLabel,
                                MoneyFormat.format(expense.amount, expense.currencyCode),
                            ),
                        sortEpochMs = sortEpochMs,
                        categoryIconKey = category?.iconKey ?: "category_general",
                        currencyCode = expense.currencyCode,
                        balanceSide = balanceSide,
                        balanceAmount = balanceAmount,
                        pendingSync = expense.syncStatus == SyncStatus.PENDING,
                    )
                }
            val paymentItems =
                payments.map { payment ->
                    val fromLabel = nameOf(payment.fromUserId)
                    val toLabel = nameOf(payment.toUserId)
                    val money = MoneyFormat.format(payment.amount, payment.currencyCode)
                    val title =
                        when (me) {
                            payment.fromUserId ->
                                appContext.getString(R.string.payment_to_person, toLabel)
                            payment.toUserId ->
                                appContext.getString(R.string.payment_from_person, fromLabel)
                            else ->
                                appContext.getString(
                                    R.string.payment_completed_other,
                                    fromLabel,
                                    toLabel,
                                )
                        }
                    val sortEpochMs =
                        payment.paidAtEpochMs.coerceAtLeast(payment.createdAtEpochMs)
                    val (balanceSide, balanceAmount) =
                        when (me) {
                            payment.toUserId -> LedgerBalanceSide.RECEIVED to payment.amount
                            payment.fromUserId -> LedgerBalanceSide.PAID to payment.amount
                            else -> null to payment.amount
                        }
                    LedgerListItem(
                        id = "payment-${payment.id}",
                        isPayment = true,
                        title = title,
                        subtitle =
                            appContext.getString(
                                R.string.ledger_paid_by,
                                fromLabel,
                                money,
                            ),
                        sortEpochMs = sortEpochMs,
                        categoryIconKey = "category_payment",
                        currencyCode = payment.currencyCode,
                        balanceSide = balanceSide,
                        balanceAmount = balanceAmount,
                    )
                }
            return (expenseItems + paymentItems).sortedByDescending { it.sortEpochMs }
        }

        /**
         * Net for [me] on a single expense: positive ⇒ lent, negative ⇒ borrowed.
         */
        private fun viewerBalanceForExpense(
            me: String?,
            expense: Expense,
            splits: List<ExpenseSplit>,
        ): Pair<LedgerBalanceSide?, BigDecimal?> {
            if (me == null) return null to null
            val zero = BigDecimal.ZERO.setScale(2)
            val net = BalanceCalculator.viewerNetForExpense(me, expense, splits)
            return when {
                net.compareTo(zero) > 0 -> LedgerBalanceSide.LENT to net
                net.compareTo(zero) < 0 -> LedgerBalanceSide.BORROWED to net.abs()
                else -> null to null
            }
        }

        private fun nameOf(
            userId: String,
            me: String?,
            friendNames: Map<String, String>,
            userNames: Map<String, String>,
        ): String =
            when (userId) {
                me -> "You"
                else -> friendNames[userId] ?: userNames[userId] ?: userId.take(8)
            }

        /**
         * Flushes local PENDING writes, pulls cloud data for the signed-in user, then
         * re-fetches expenses for [groupId] so the open group shows other members' changes.
         *
         * @param groupId Group being viewed.
         */
        fun refreshGroupFromCloud(groupId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching { syncInteractor.syncForUser(userId.value) }
                val refreshError =
                    runCatching {
                        expenseInteractor.refreshGroupExpenses(groupId)
                        paymentInteractor.refreshGroupPayments(groupId)
                    }.exceptionOrNull()
                if (refreshError != null) {
                    _uiState.update {
                        it.copy(errorMessage = userFacingRefreshError(refreshError))
                    }
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        /**
         * Subscribes to Realtime changes for [groupId] until the caller cancels
         * (e.g. [androidx.lifecycle.repeatOnLifecycle] leaves RESUMED).
         */
        suspend fun observeGroupLiveUpdates(groupId: String) {
            try {
                groupLiveSync.start(groupId, viewModelScope)
                kotlinx.coroutines.awaitCancellation()
            } finally {
                groupLiveSync.stop()
            }
        }

        fun refreshMyExpenses() {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching { syncInteractor.syncForUser(id, force = true) }
                runCatching {
                    expenseInteractor.refreshExpensesForUser(id)
                    paymentInteractor.refreshPaymentsForUser(id)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = userFacingRefreshError(err))
                    }
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun createExpense(
            description: String,
            amountText: String,
            currencyCode: String,
            paidByUserId: String,
            participantIds: List<String>,
            splitType: SplitType,
            groupId: String?,
            unequalAmounts: Map<String, BigDecimal> = emptyMap(),
            percentages: Map<String, BigDecimal> = emptyMap(),
            shares: Map<String, Int> = emptyMap(),
            adjustments: Map<String, BigDecimal> = emptyMap(),
            paidAmounts: Map<String, BigDecimal> = emptyMap(),
            recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
            categoryId: String? = null,
            notes: String? = null,
            expenseDateEpochMs: Long? = null,
            onSuccess: () -> Unit,
        ) {
            if (_uiState.value.isSubmitting) return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                runCatching { categoryRepository.ensureDefaults() }
                if (paidByUserId.isBlank() || participantIds.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = appContext.getString(R.string.msg_wait_for_account),
                        )
                    }
                    return@launch
                }
                val amount =
                    runCatching { BigDecimal(amountText.trim()) }.getOrElse {
                        _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = appContext.getString(R.string.msg_enter_valid_amount))
                        }
                        return@launch
                    }
                val result =
                    expenseInteractor.createExpense(
                        input =
                            CreateExpenseInput(
                                description = description,
                                amount = amount,
                                currencyCode = currencyCode,
                                paidByUserId = paidByUserId,
                                participantIds = participantIds,
                                splitType = splitType,
                                groupId = groupId,
                                unequalAmounts = unequalAmounts,
                                percentages = percentages,
                                shares = shares,
                                adjustments = adjustments,
                                paidAmounts = paidAmounts,
                                recurrenceFrequency = recurrenceFrequency,
                                categoryId = categoryId,
                                notes = notes,
                                expenseDateEpochMs = expenseDateEpochMs,
                            ),
                        actorUserId = userId.value,
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = mapExpenseSaveError(result.exceptionOrNull()),
                        infoMessage =
                            if (result.isSuccess) {
                                appContext.getString(R.string.msg_expense_added)
                            } else {
                                null
                            },
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }

        /**
         * Saves a new expense without blocking navigation (local write runs in the background).
         */
        fun createExpenseInBackground(
            description: String,
            amountText: String,
            currencyCode: String,
            paidByUserId: String,
            participantIds: List<String>,
            splitType: SplitType,
            groupId: String?,
            unequalAmounts: Map<String, BigDecimal> = emptyMap(),
            percentages: Map<String, BigDecimal> = emptyMap(),
            shares: Map<String, Int> = emptyMap(),
            adjustments: Map<String, BigDecimal> = emptyMap(),
            paidAmounts: Map<String, BigDecimal> = emptyMap(),
            recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
            categoryId: String? = null,
            notes: String? = null,
            expenseDateEpochMs: Long? = null,
        ) {
            if (paidByUserId.isBlank() || participantIds.isEmpty()) return
            val amount = runCatching { BigDecimal(amountText.trim()) }.getOrNull() ?: return
            expenseInteractor.enqueueCreateExpense(
                input =
                    CreateExpenseInput(
                        description = description,
                        amount = amount,
                        currencyCode = currencyCode,
                        paidByUserId = paidByUserId,
                        participantIds = participantIds,
                        splitType = splitType,
                        groupId = groupId,
                        unequalAmounts = unequalAmounts,
                        percentages = percentages,
                        shares = shares,
                        adjustments = adjustments,
                        paidAmounts = paidAmounts,
                        recurrenceFrequency = recurrenceFrequency,
                        categoryId = categoryId,
                        notes = notes,
                        expenseDateEpochMs = expenseDateEpochMs,
                    ),
                actorUserId = userId.value,
            )
        }

        /**
         * Observes a single expense with labeled splits for the detail screen.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeExpenseDetail(expenseId: String): StateFlow<ExpenseDetailUi?> {
            if (expenseId.isBlank()) return emptyExpenseDetail
            return expenseDetailFlows.getOrPut(expenseId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(null)
                        } else {
                            expenseRepository.observeExpenseById(expenseId).flatMapLatest { expense ->
                                if (expense == null) {
                                    flowOf(null)
                                } else {
                                    val groupId = expense.groupId
                                    val groupExpensesFlow =
                                        if (groupId != null) {
                                            expenseRepository.observeExpenses(groupId)
                                        } else {
                                            flowOf(emptyList())
                                        }
                                    val groupSplitsFlow =
                                        if (groupId != null) {
                                            expenseRepository.observeSplitsByGroup(groupId)
                                        } else {
                                            flowOf(emptyMap())
                                        }
                                    combine(
                                        expenseRepository.observeSplits(expenseId),
                                        userRepository.observeUsers(),
                                        friendRepository.observeFriends(me),
                                        groupRepository.observeGroupsForUser(me),
                                        categoryRepository.observeCategories(),
                                    ) { splits, users, friends, groups, categories ->
                                        DetailCore(
                                            expense = expense,
                                            splits = splits,
                                            users = users,
                                            friends = friends,
                                            groups = groups,
                                            categories = categories,
                                        )
                                    }.combine(
                                        combine(groupExpensesFlow, groupSplitsFlow) { expenses, splitsByExpense ->
                                            expenses to splitsByExpense
                                        },
                                    ) { core, groupLedger ->
                                        val (groupExpenses, splitsByExpense) = groupLedger
                                        val userNames =
                                            core.users.associateBy({ it.id }, { it.displayName })
                                        val userPhotos =
                                            core.users.associateBy({ it.id }, { it.photoUrl })
                                        val friendNames =
                                            core.friends.associateBy(
                                                { it.friendUserId },
                                                { it.displayNameSnapshot },
                                            )

                                        fun nameOf(id: String): String =
                                            nameOf(id, me, friendNames, userNames)
                                        val (balanceSide, balanceAmount) =
                                            viewerBalanceForExpense(
                                                me = me,
                                                expense = core.expense,
                                                splits = core.splits,
                                            )
                                        val category =
                                            core.expense.categoryId?.let { cid ->
                                                core.categories.firstOrNull { it.id == cid }
                                            }
                                        val now = Calendar.getInstance(TimeZone.getDefault())
                                        val trendMonths =
                                            if (groupId == null) {
                                                emptyList()
                                            } else {
                                                GroupSpendingCalculator.monthlyBuckets(
                                                    viewerUserId = me,
                                                    expenses = groupExpenses,
                                                    splitsByExpenseId = splitsByExpense,
                                                    currencyCode = core.expense.currencyCode,
                                                    endYear = now.get(Calendar.YEAR),
                                                    endMonth = now.get(Calendar.MONTH),
                                                    monthCount = 3,
                                                )
                                            }
                                        val payerIds =
                                            run {
                                                val payers =
                                                    core.splits.filter {
                                                        (it.paidAmount ?: BigDecimal.ZERO) >
                                                            BigDecimal.ZERO
                                                    }
                                                when {
                                                    payers.size > 1 -> emptyList()
                                                    payers.size == 1 -> listOf(payers.first().userId)
                                                    else -> listOf(core.expense.paidByUserId)
                                                }
                                            }
                                        ExpenseDetailUi(
                                            expense = core.expense,
                                            splits =
                                                core.splits.map { split ->
                                                    ExpenseSplitLineUi(
                                                        userId = split.userId,
                                                        participantLabel = nameOf(split.userId),
                                                        owedAmount = split.owedAmount,
                                                        paidAmount = split.paidAmount,
                                                        percentage = split.percentage,
                                                        shares = split.shares,
                                                        adjustmentAmount = split.adjustmentAmount,
                                                        photoUrl = userPhotos[split.userId],
                                                    )
                                                },
                                            payerLabel =
                                                if (payerIds.size == 1) {
                                                    nameOf(payerIds.first())
                                                } else {
                                                    appContext.getString(
                                                        R.string.expense_multiple_people,
                                                    )
                                                },
                                            payerPhotoUrl =
                                                payerIds.singleOrNull()?.let { userPhotos[it] },
                                            groupName =
                                                groupId?.let { gid ->
                                                    core.groups.firstOrNull { it.id == gid }?.name
                                                },
                                            categoryName = category?.name ?: "General",
                                            categoryIconKey =
                                                category?.iconKey ?: "category_general",
                                            viewerOwedAmount =
                                                core.splits
                                                    .firstOrNull { it.userId == me }
                                                    ?.owedAmount,
                                            viewerBalanceSide = balanceSide,
                                            viewerBalanceAmount = balanceAmount,
                                            spendingTrendMonths = trendMonths,
                                        )
                                    }
                                }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
            }
        }

        /**
         * Observes the comment thread for an expense (user + SplitEase system updates).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeExpenseComments(expenseId: String): StateFlow<List<ExpenseCommentUi>> {
            if (expenseId.isBlank()) return emptyExpenseComments
            return expenseCommentFlows.getOrPut(expenseId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            combine(
                                expenseCommentRepository.observeForExpense(expenseId),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { comments, users, friends ->
                                val userById = users.associateBy { it.id }
                                val friendNames =
                                    friends.associateBy({ it.friendUserId }, { it.displayNameSnapshot })
                                comments.map { comment ->
                                    val isSystem = comment.kind == ExpenseCommentKind.SYSTEM
                                    val authorLabel =
                                        if (isSystem) {
                                            appContext.getString(R.string.expense_comment_system_author)
                                        } else {
                                            nameOf(
                                                comment.authorUserId,
                                                me,
                                                friendNames,
                                                userById.mapValues { it.value.displayName },
                                            )
                                        }
                                    ExpenseCommentUi(
                                        id = comment.id,
                                        authorLabel = authorLabel,
                                        authorPhotoUrl =
                                            if (isSystem) {
                                                null
                                            } else {
                                                userById[comment.authorUserId]?.photoUrl
                                            },
                                        body = comment.body,
                                        kind = comment.kind,
                                        createdAtEpochMs = comment.createdAtEpochMs,
                                    )
                                }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }
        }

        /** Observes attachments (receipt images) on an expense. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeExpensePhotos(expenseId: String): StateFlow<List<ExpensePhotoUi>> {
            if (expenseId.isBlank()) return emptyExpensePhotos
            return expensePhotoFlows.getOrPut(expenseId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            combine(
                                expensePhotoRepository.observeForExpense(expenseId),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { photos, users, friends ->
                                val userNames = users.associateBy({ it.id }, { it.displayName })
                                val friendNames =
                                    friends.associateBy({ it.friendUserId }, { it.displayNameSnapshot })
                                photos.mapNotNull { photo ->
                                    val uri = photo.resolvedDisplayUri() ?: return@mapNotNull null
                                    ExpensePhotoUi(
                                        id = photo.id,
                                        displayUri = uri,
                                        createdAtEpochMs = photo.createdAtEpochMs,
                                        createdByUserId = photo.createdByUserId,
                                        authorLabel =
                                            nameOf(
                                                photo.createdByUserId,
                                                me,
                                                friendNames,
                                                userNames,
                                            ),
                                    )
                                }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }
        }

        /**
         * Pulls comments and attachments for [expenseId] from the cloud so photos added
         * by other group members appear on this device.
         */
        fun refreshExpenseSideData(expenseId: String) {
            val id = expenseId.trim()
            if (id.isEmpty()) return
            viewModelScope.launch {
                runCatching { expenseInteractor.refreshExpenseSideData(id) }
            }
        }

        fun addExpenseComment(
            expenseId: String,
            body: String,
            onSuccess: () -> Unit,
        ) {
            val actor = userId.value
            if (actor.isNullOrBlank()) {
                _uiState.update {
                    it.copy(errorMessage = appContext.getString(R.string.msg_wait_for_account))
                }
                return
            }
            val trimmed = body.trim()
            if (trimmed.isEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = appContext.getString(R.string.msg_comment_empty))
                }
                return
            }
            viewModelScope.launch {
                val result =
                    expenseInteractor.addComment(
                        expenseId = expenseId,
                        body = trimmed,
                        actorUserId = actor,
                    )
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                result.exceptionOrNull()?.message
                                    ?: appContext.getString(R.string.msg_comment_failed),
                        )
                    }
                }
            }
        }

        fun addExpenseAttachments(
            expenseId: String,
            photoUris: List<String>,
        ) {
            val actor = userId.value
            if (actor.isNullOrBlank()) {
                _uiState.update {
                    it.copy(errorMessage = appContext.getString(R.string.msg_wait_for_account))
                }
                return
            }
            if (photoUris.isEmpty()) return
            if (_uiState.value.isAttachingPhotos) return
            viewModelScope.launch {
                _uiState.update { it.copy(isAttachingPhotos = true, errorMessage = null) }
                val result =
                    expenseInteractor.addExpenseAttachments(
                        expenseId = expenseId,
                        photoUris = photoUris,
                        actorUserId = actor,
                    )
                val payload = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isAttachingPhotos = false,
                        errorMessage =
                            when {
                                result.isFailure ->
                                    result.exceptionOrNull()?.message
                                        ?: appContext.getString(R.string.msg_photo_failed)
                                payload != null && payload.failedCount > 0 ->
                                    appContext.getString(
                                        R.string.msg_attachments_partial,
                                        payload.addedCount,
                                        payload.addedCount + payload.failedCount,
                                    )
                                else -> it.errorMessage
                            },
                    )
                }
            }
        }

        private data class DetailCore(
            val expense: Expense,
            val splits: List<ExpenseSplit>,
            val users: List<User>,
            val friends: List<Friend>,
            val groups: List<Group>,
            val categories: List<Category>,
        )

        fun updateExpense(
            expenseId: String,
            description: String,
            amountText: String,
            paidByUserId: String,
            participantIds: List<String>,
            splitType: SplitType,
            groupId: String?,
            unequalAmounts: Map<String, BigDecimal> = emptyMap(),
            percentages: Map<String, BigDecimal> = emptyMap(),
            shares: Map<String, Int> = emptyMap(),
            adjustments: Map<String, BigDecimal> = emptyMap(),
            paidAmounts: Map<String, BigDecimal> = emptyMap(),
            categoryId: String? = null,
            notes: String? = null,
            expenseDateEpochMs: Long? = null,
            onSuccess: () -> Unit,
        ) {
            if (_uiState.value.isSubmitting) return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                runCatching { categoryRepository.ensureDefaults() }
                if (paidByUserId.isBlank() || participantIds.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = appContext.getString(R.string.msg_wait_for_account),
                        )
                    }
                    return@launch
                }
                val amount =
                    runCatching { BigDecimal(amountText.trim()) }.getOrElse {
                        _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = appContext.getString(R.string.msg_enter_valid_amount))
                        }
                        return@launch
                    }
                val existing = expenseRepository.getExpenseById(expenseId)
                if (existing == null) {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = appContext.getString(R.string.expense_not_found))
                    }
                    return@launch
                }
                val result =
                    expenseInteractor.updateExpense(
                        expenseId = expenseId,
                        input =
                            CreateExpenseInput(
                                description = description,
                                amount = amount,
                                currencyCode = existing.currencyCode,
                                paidByUserId = paidByUserId,
                                participantIds = participantIds,
                                splitType = splitType,
                                groupId = groupId ?: existing.groupId,
                                unequalAmounts = unequalAmounts,
                                percentages = percentages,
                                shares = shares,
                                adjustments = adjustments,
                                paidAmounts = paidAmounts,
                                recurrenceFrequency = existing.recurrenceFrequency,
                                categoryId = categoryId ?: existing.categoryId,
                                notes = notes,
                                expenseDateEpochMs = expenseDateEpochMs ?: existing.expenseDateEpochMs,
                                recurringTemplateId = existing.recurringTemplateId,
                            ),
                        actorUserId = userId.value,
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = mapExpenseSaveError(result.exceptionOrNull()),
                        infoMessage =
                            if (result.isSuccess) {
                                appContext.getString(R.string.msg_expense_updated)
                            } else {
                                null
                            },
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }

        fun deleteExpense(
            expenseId: String,
            onSuccess: () -> Unit,
        ) {
            if (_uiState.value.isSubmitting) return
            val actor = userId.value.orEmpty()
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = expenseInteractor.deleteExpense(expenseId, actorUserId = actor)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) appContext.getString(R.string.msg_expense_deleted) else null,
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }

        suspend fun resolveGroupParticipantOptions(groupId: String): List<ParticipantOption> {
            val me = userId.value ?: return emptyList()
            val myUser = userRepository.getUserById(me)
            val myName = myUser?.displayName ?: "You"
            val members = groupRepository.observeMembers(groupId).first()
            val friends = friendRepository.observeFriends(me).first()
            val friendById = friends.associateBy { it.friendUserId }

            // Group expenses include only membership rows (not unrelated invited friends).
            val options = linkedMapOf<String, ParticipantOption>()
            options[me] =
                ParticipantOption(me, myName, isPendingInvite = false, photoUrl = myUser?.photoUrl)
            members.forEach { member ->
                if (member.userId == me) return@forEach
                val friend = friendById[member.userId]
                val memberUser = userRepository.getUserById(member.userId)
                val label =
                    friend?.displayNameSnapshot
                        ?: memberUser?.displayName
                        ?: member.userId.take(8)
                val pending =
                    friend?.displayNameSnapshot?.contains("(invited)", ignoreCase = true) == true ||
                        label.contains("(invited)", ignoreCase = true)
                options[member.userId] =
                    ParticipantOption(
                        member.userId,
                        label,
                        isPendingInvite = pending,
                        photoUrl = memberUser?.photoUrl,
                    )
            }
            return options.values.toList()
        }

        suspend fun resolveFriendParticipantOptions(friendUserId: String): List<ParticipantOption> {
            val me = userId.value ?: return emptyList()
            val myUser = userRepository.getUserById(me)
            val myName = myUser?.displayName ?: "You"
            val friend =
                friendRepository
                    .observeFriends(me)
                    .first()
                    .firstOrNull { it.friendUserId == friendUserId }
                    ?: return listOf(
                        ParticipantOption(me, myName, photoUrl = myUser?.photoUrl),
                    )
            val friendUser = userRepository.getUserById(friend.friendUserId)
            return listOf(
                ParticipantOption(me, myName, isPendingInvite = false, photoUrl = myUser?.photoUrl),
                ParticipantOption(
                    friend.friendUserId,
                    friend.displayNameSnapshot,
                    isPendingInvite =
                        friend.displayNameSnapshot.contains("(invited)", ignoreCase = true),
                    photoUrl = friendUser?.photoUrl,
                ),
            )
        }

        suspend fun friendLabel(friendUserId: String): String {
            val me = userId.value ?: return friendUserId.take(8)
            return friendRepository
                .observeFriends(me)
                .first()
                .firstOrNull { it.friendUserId == friendUserId }
                ?.displayNameSnapshot
                ?: userRepository.getUserById(friendUserId)?.displayName
                ?: friendUserId.take(8)
        }

        private fun mapExpenseSaveError(throwable: Throwable?): String? {
            val raw = throwable?.message ?: return null
            val lower = raw.lowercase()
            return when {
                lower.contains("row-level security") && lower.contains("expenses") ->
                    appContext.getString(R.string.msg_expense_cloud_rls)
                lower.contains("violates row-level security policy") ->
                    appContext.getString(R.string.msg_expense_cloud_rls)
                lower.contains("schema cache") &&
                    (lower.contains("paid_amount") || lower.contains("adjustment_amount")) ->
                    appContext.getString(R.string.msg_expense_cloud_schema)
                isNetworkError(raw) ->
                    appContext.getString(R.string.msg_cloud_unreachable)
                else -> raw
            }
        }

        private fun userFacingRefreshError(error: Throwable): String {
            val raw = error.message.orEmpty()
            return when {
                isNetworkError(raw) ->
                    appContext.getString(R.string.msg_cloud_unreachable)
                raw.isNotBlank() &&
                    raw.length <= 120 &&
                    !raw.contains("http", ignoreCase = true) ->
                    raw
                else ->
                    appContext.getString(R.string.msg_could_not_refresh_expenses)
            }
        }

        private fun isNetworkError(raw: String): Boolean {
            val lower = raw.lowercase()
            return lower.contains("unable to resolve host") ||
                lower.contains("unknownhost") ||
                lower.contains("failed to connect") ||
                lower.contains("timeout") ||
                lower.contains("network is unreachable") ||
                lower.contains("no address associated with hostname")
        }
    }
