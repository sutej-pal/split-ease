package com.splitease.app.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.expense.CreateExpenseInput
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class ExpensesUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

data class ParticipantOption(
    val userId: String,
    val label: String,
    val isPendingInvite: Boolean = false,
)

@HiltViewModel
class ExpensesViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
        private val expenseInteractor: ExpenseInteractor,
        private val groupRepository: GroupRepository,
        private val friendRepository: FriendRepository,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _uiState = MutableStateFlow(ExpensesUiState())
        val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

        val categories: StateFlow<List<Category>> =
            categoryRepository
                .observeCategories()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun currentUserId(): String? = userId.value

        fun observeGroupExpenses(groupId: String): StateFlow<List<Expense>> =
            expenseRepository
                .observeExpenses(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeFriendExpenses(friendUserId: String): StateFlow<List<Expense>> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(emptyList())
                    } else {
                        expenseRepository.observeBetweenUsers(me, friendUserId)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun refreshGroupExpenses(groupId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching { expenseInteractor.refreshGroupExpenses(groupId) }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.message ?: "Could not refresh expenses.")
                        }
                    }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun refreshMyExpenses() {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching { expenseInteractor.refreshExpensesForUser(id) }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(errorMessage = err.message ?: "Could not refresh expenses.")
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
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val amount =
                    runCatching { BigDecimal(amountText.trim()) }.getOrElse {
                        _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = "Enter a valid amount.")
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
                        ),
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) "Expense added." else null,
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
