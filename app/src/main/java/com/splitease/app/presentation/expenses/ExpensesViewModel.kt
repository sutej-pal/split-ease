package com.splitease.app.presentation.expenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.expense.CreateExpenseInput
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppSettingsRepository
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
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ExpensesUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

/**
 * Whether the signed-in user lent or borrowed on an expense.
 */
enum class LedgerBalanceSide {
    LENT,
    BORROWED,
}

/**
 * Unified expense/payment row for group and friend timelines.
 */
data class LedgerListItem(
    val id: String,
    val isPayment: Boolean,
    val title: String,
    val subtitle: String,
    val sortEpochMs: Long,
    val categoryIconKey: String? = null,
    /** Display name of who paid (e.g. "You" or a friend). */
    val payerLabel: String? = null,
    val paidAmount: BigDecimal? = null,
    val currencyCode: String = "INR",
    val balanceSide: LedgerBalanceSide? = null,
    val balanceAmount: BigDecimal? = null,
)

data class ParticipantOption(
    val userId: String,
    val label: String,
    val isPendingInvite: Boolean = false,
)

data class ExpenseSplitLineUi(
    val userId: String,
    val participantLabel: String,
    val owedAmount: BigDecimal,
)

data class ExpenseDetailUi(
    val expense: Expense,
    val splits: List<ExpenseSplitLineUi>,
    val payerLabel: String,
    val groupName: String?,
    val viewerBalanceSide: LedgerBalanceSide? = null,
    val viewerBalanceAmount: BigDecimal? = null,
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
        private val expenseInteractor: ExpenseInteractor,
        private val syncInteractor: SyncInteractor,
        private val groupRepository: GroupRepository,
        private val friendRepository: FriendRepository,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val paymentRepository: PaymentRepository,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
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
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "INR")

        fun currentUserId(): String? = userId.value

        /** Reactive signed-in user id for Compose collectors. */
        val signedInUserId: StateFlow<String?> = userId

        /**
         * True when [groupId] already has at least one expense or settlement payment.
         * Used to skip the first-expense members confirm dialog on non-empty groups.
         */
        suspend fun groupHasLedgerEntries(groupId: String): Boolean {
            val expenses = expenseRepository.observeExpenses(groupId).first()
            if (expenses.isNotEmpty()) return true
            return paymentRepository.observePayments(groupId).first().isNotEmpty()
        }

        private val groupExpenseFlows = ConcurrentHashMap<String, StateFlow<List<Expense>>>()
        private val groupLedgerFlows = ConcurrentHashMap<String, StateFlow<List<LedgerListItem>>>()
        private val friendLedgerFlows = ConcurrentHashMap<String, StateFlow<List<LedgerListItem>>>()
        private val expenseDetailFlows = ConcurrentHashMap<String, StateFlow<ExpenseDetailUi?>>()
        private val emptyExpenseDetail = MutableStateFlow<ExpenseDetailUi?>(null)

        fun observeGroupExpenses(groupId: String): StateFlow<List<Expense>> =
            groupExpenseFlows.getOrPut(groupId) {
                expenseRepository
                    .observeExpenses(groupId)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }

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
                                buildLedger(
                                    expenses = source.expenses,
                                    payments = source.payments,
                                    me = me,
                                    categoryById = categories.associateBy { it.id },
                                    friendNames =
                                        source.friends.associate {
                                            it.friendUserId to it.displayNameSnapshot
                                        },
                                    userNames = source.users.associate { it.id to it.displayName },
                                    splitsByExpenseId = source.splitsByExpenseId,
                                )
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            }

        /**
         * Non-group expenses and payments between the signed-in user and [friendUserId].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeFriendLedger(friendUserId: String): StateFlow<List<LedgerListItem>> =
            friendLedgerFlows.getOrPut(friendUserId) {
                userId
                    .flatMapLatest { me ->
                        if (me == null) {
                            flowOf(emptyList())
                        } else {
                            expenseRepository
                                .observeBetweenUsers(me, friendUserId)
                                .flatMapLatest { expenses ->
                                    val expenseIds = expenses.map { it.id }
                                    combine(
                                        flowOf(expenses),
                                        paymentRepository.observeBetweenUsers(me, friendUserId),
                                        expenseRepository.observeSplitsForExpenses(expenseIds),
                                        userRepository.observeUsers(),
                                        friendRepository.observeFriends(me),
                                    ) { exp, payments, splitsByExpenseId, users, friends ->
                                        LedgerSource(
                                            expenses = exp,
                                            payments = payments,
                                            splitsByExpenseId = splitsByExpenseId,
                                            users = users,
                                            friends = friends,
                                        )
                                    }
                                }.combine(categoryRepository.observeCategories()) { source, categories ->
                                    buildLedger(
                                        expenses = source.expenses,
                                        payments = source.payments,
                                        me = me,
                                        categoryById = categories.associateBy { it.id },
                                        friendNames =
                                            source.friends.associate {
                                                it.friendUserId to it.displayNameSnapshot
                                            },
                                        userNames =
                                            source.users.associate { it.id to it.displayName },
                                        splitsByExpenseId = source.splitsByExpenseId,
                                    )
                                }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
                    val categoryLabel = category?.name?.let { " · $it" }.orEmpty()
                    val payerLabel = nameOf(expense.paidByUserId)
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
                            "${expense.currencyCode} ${expense.amount.toPlainString()}" +
                                " · ${expense.splitType.name.lowercase()}$categoryLabel",
                        sortEpochMs = expense.expenseDateEpochMs.coerceAtLeast(expense.createdAtEpochMs),
                        categoryIconKey = category?.iconKey ?: "category_general",
                        payerLabel = payerLabel,
                        paidAmount = expense.amount,
                        currencyCode = expense.currencyCode,
                        balanceSide = balanceSide,
                        balanceAmount = balanceAmount,
                    )
                }
            val paymentItems =
                payments.map { payment ->
                    val title =
                        when (me) {
                            payment.fromUserId ->
                                "Payment completed — you paid ${nameOf(payment.toUserId)}"
                            payment.toUserId ->
                                "Payment completed — ${nameOf(payment.fromUserId)} paid you"
                            else ->
                                "Payment completed — ${nameOf(payment.fromUserId)} paid ${nameOf(payment.toUserId)}"
                        }
                    val date =
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(payment.paidAtEpochMs))
                    val notePart =
                        payment.note
                            ?.takeIf { it.isNotBlank() && !it.equals("Payment completed", ignoreCase = true) }
                            ?.let { " · $it" }
                            .orEmpty()
                    LedgerListItem(
                        id = "payment-${payment.id}",
                        isPayment = true,
                        title = title,
                        subtitle =
                            "${payment.currencyCode} ${payment.amount.toPlainString()} · $date$notePart",
                        sortEpochMs = payment.paidAtEpochMs.coerceAtLeast(payment.createdAtEpochMs),
                        categoryIconKey = "category_payment",
                        payerLabel = null,
                        paidAmount = null,
                        currencyCode = payment.currencyCode,
                        balanceSide = null,
                        balanceAmount = payment.amount,
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
            val myShare =
                splits.firstOrNull { it.userId == me }
                    ?.owedAmount
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?: zero
            val total = expense.amount.setScale(2, RoundingMode.HALF_UP)
            val net =
                if (expense.paidByUserId == me) {
                    total.subtract(myShare)
                } else {
                    myShare.negate()
                }
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
                runCatching { expenseInteractor.refreshGroupExpenses(groupId) }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.message ?: appContext.getString(R.string.msg_could_not_refresh_expenses))
                        }
                    }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun refreshGroupExpenses(groupId: String) {
            refreshGroupFromCloud(groupId)
        }

        fun refreshMyExpenses() {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching { expenseInteractor.refreshExpensesForUser(id) }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.message ?: appContext.getString(R.string.msg_could_not_refresh_expenses))
                        }
                    }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun createExpense(
            description: String,
            amountText: String,
            paidByUserId: String,
            participantIds: List<String>,
            splitType: SplitType,
            groupId: String?,
            unequalAmounts: Map<String, BigDecimal> = emptyMap(),
            percentages: Map<String, BigDecimal> = emptyMap(),
            shares: Map<String, Int> = emptyMap(),
            recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
            categoryId: String? = null,
            notes: String? = null,
            expenseDateEpochMs: Long? = null,
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
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
                val currency = appSettingsRepository.getCurrencyCode()
                val result =
                    expenseInteractor.createExpense(
                        CreateExpenseInput(
                            description = description,
                            amount = amount,
                            currencyCode = currency,
                            paidByUserId = paidByUserId,
                            participantIds = participantIds,
                            splitType = splitType,
                            groupId = groupId,
                            unequalAmounts = unequalAmounts,
                            percentages = percentages,
                            shares = shares,
                            recurrenceFrequency = recurrenceFrequency,
                            categoryId = categoryId,
                            notes = notes,
                            expenseDateEpochMs = expenseDateEpochMs,
                        ),
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) appContext.getString(R.string.msg_expense_added) else null,
                    )
                }
                if (result.isSuccess) onSuccess()
            }
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
                            combine(
                                expenseRepository.observeExpenseById(expenseId),
                                expenseRepository.observeSplits(expenseId),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                                groupRepository.observeGroupsForUser(me),
                            ) { expense, splits, users, friends, groups ->
                                if (expense == null) {
                                    null
                                } else {
                                    val userNames = users.associate { it.id to it.displayName }
                                    val friendNames =
                                        friends.associate { it.friendUserId to it.displayNameSnapshot }
                                    fun nameOf(id: String): String =
                                        nameOf(id, me, friendNames, userNames)
                                    val (balanceSide, balanceAmount) =
                                        viewerBalanceForExpense(
                                            me = me,
                                            expense = expense,
                                            splits = splits,
                                        )
                                    ExpenseDetailUi(
                                        expense = expense,
                                        splits =
                                            splits.map { split ->
                                                ExpenseSplitLineUi(
                                                    userId = split.userId,
                                                    participantLabel = nameOf(split.userId),
                                                    owedAmount = split.owedAmount,
                                                )
                                            },
                                        payerLabel = nameOf(expense.paidByUserId),
                                        groupName =
                                            expense.groupId?.let { gid ->
                                                groups.firstOrNull { it.id == gid }?.name
                                            },
                                        viewerBalanceSide = balanceSide,
                                        viewerBalanceAmount = balanceAmount,
                                    )
                                }
                            }
                        }
                    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
            }
        }

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
            categoryId: String? = null,
            notes: String? = null,
            expenseDateEpochMs: Long? = null,
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
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
                                recurrenceFrequency = existing.recurrenceFrequency,
                                categoryId = categoryId ?: existing.categoryId,
                                notes = notes,
                                expenseDateEpochMs = expenseDateEpochMs ?: existing.expenseDateEpochMs,
                                recurringTemplateId = existing.recurringTemplateId,
                            ),
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) appContext.getString(R.string.msg_expense_updated) else null,
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }

        fun deleteExpense(
            expenseId: String,
            onSuccess: () -> Unit,
        ) {
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

        fun addCustomCategory(name: String, onCreated: (String) -> Unit) {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return
            viewModelScope.launch {
                val id = UUID.randomUUID().toString()
                categoryRepository.upsert(
                    Category(
                        id = id,
                        name = trimmed,
                        iconKey = "category_custom",
                        isDefault = false,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    ),
                )
                onCreated(id)
            }
        }

        suspend fun resolveGroupParticipantOptions(groupId: String): List<ParticipantOption> {
            val me = userId.value ?: return emptyList()
            val myName = userRepository.getUserById(me)?.displayName ?: "You"
            val members = groupRepository.observeMembers(groupId).first()
            val friends = friendRepository.observeFriends(me).first()
            val friendById = friends.associateBy { it.friendUserId }

            val options = linkedMapOf<String, ParticipantOption>()
            options[me] = ParticipantOption(me, myName, false)
            members.forEach { member ->
                if (member.userId == me) return@forEach
                val friend = friendById[member.userId]
                val label =
                    friend?.displayNameSnapshot
                        ?: userRepository.getUserById(member.userId)?.displayName
                        ?: member.userId.take(8)
                val pending =
                    friend?.displayNameSnapshot?.contains("(invited)", true) == true ||
                        label.contains("(invited)", true)
                options[member.userId] = ParticipantOption(member.userId, label, pending)
            }
            friends
                .filter { it.displayNameSnapshot.contains("(invited)", ignoreCase = true) }
                .forEach { friend ->
                    if (!options.containsKey(friend.friendUserId)) {
                        options[friend.friendUserId] =
                            ParticipantOption(friend.friendUserId, friend.displayNameSnapshot, true)
                    }
                }
            return options.values.toList()
        }

        suspend fun resolveFriendParticipantOptions(friendUserId: String): List<ParticipantOption> {
            val me = userId.value ?: return emptyList()
            val myName = userRepository.getUserById(me)?.displayName ?: "You"
            val friend =
                friendRepository.observeFriends(me).first()
                    .firstOrNull { it.friendUserId == friendUserId }
                    ?: return listOf(ParticipantOption(me, myName))
            return listOf(
                ParticipantOption(me, myName, false),
                ParticipantOption(
                    friend.friendUserId,
                    friend.displayNameSnapshot,
                    friend.displayNameSnapshot.contains("(invited)", ignoreCase = true),
                ),
            )
        }

        suspend fun friendLabel(friendUserId: String): String {
            val me = userId.value ?: return friendUserId.take(8)
            return friendRepository.observeFriends(me).first()
                .firstOrNull { it.friendUserId == friendUserId }
                ?.displayNameSnapshot
                ?: userRepository.getUserById(friendUserId)?.displayName
                ?: friendUserId.take(8)
        }
    }
