package com.splitease.app.presentation.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class CurrencyConversionUi(
    val groupId: String? = null,
    val friendUserId: String? = null,
    val targetCurrency: String = AppCurrencies.DEFAULT,
    val expensesToConvert: List<ConvertibleExpenseUi> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

data class ConvertibleExpenseUi(
    val id: String,
    val description: String,
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val convertedAmount: BigDecimal,
    val rate: BigDecimal,
    val dateEpochMs: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CurrencyConversionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val groupRepository: GroupRepository,
        private val expenseRepository: ExpenseRepository,
        private val expenseInteractor: ExpenseInteractor,
        appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val groupId: String? = savedStateHandle.get<String>("groupId")
        private val friendUserId: String? = savedStateHandle.get<String>("friendUserId")

        private val userId =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _isSubmitting = MutableStateFlow(false)
        private val _error = MutableStateFlow<String?>(null)
        private val _success = MutableStateFlow(false)

        val ui: StateFlow<CurrencyConversionUi> =
            combine(
                userId.flatMapLatest { me ->
                    when {
                        me == null -> flowOf(emptyList<Expense>())
                        groupId != null -> expenseRepository.observeExpenses(groupId)
                        friendUserId != null -> expenseRepository.observeBetweenUsers(me, friendUserId)
                        else -> flowOf(emptyList())
                    }
                },
                if (groupId != null) groupRepository.observeGroupById(groupId) else flowOf(null),
                appSettingsRepository.observeCurrencyCode(),
                _isSubmitting,
                _error,
                _success,
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                val expenses = args[0] as List<Expense>
                val group = args[1] as Group?
                val userCurrency = args[2] as String
                val submitting = args[3] as Boolean
                val error = args[4] as String?
                val success = args[5] as Boolean

                val targetCurrency = group?.defaultCurrencyCode ?: userCurrency
                val convertible =
                    expenses
                        .filter { it.currencyCode != targetCurrency && it.rateToDefaultCurrency != null }
                        .map { expense ->
                            val originalAmount = expense.originalAmount ?: expense.amount
                            val originalCurrency = expense.originalCurrencyCode ?: expense.currencyCode
                            ConvertibleExpenseUi(
                                id = expense.id,
                                description = expense.description,
                                originalAmount = originalAmount,
                                originalCurrency = originalCurrency,
                                convertedAmount =
                                    originalAmount
                                        .multiply(expense.rateToDefaultCurrency!!)
                                        .setScale(2, RoundingMode.HALF_UP),
                                rate = expense.rateToDefaultCurrency,
                                dateEpochMs = expense.expenseDateEpochMs,
                            )
                        }

                CurrencyConversionUi(
                    groupId = groupId,
                    friendUserId = friendUserId,
                    targetCurrency = targetCurrency,
                    expensesToConvert = convertible,
                    isLoading = false,
                    isSubmitting = submitting,
                    error = error,
                    success = success,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyConversionUi())

        fun convert() {
            if (_isSubmitting.value) return
            val currentUi = ui.value
            if (currentUi.expensesToConvert.isEmpty()) return

            viewModelScope.launch {
                _isSubmitting.value = true
                _error.value = null
                val result =
                    expenseInteractor.convertMixedCurrencies(
                        groupId = groupId,
                        friendUserId = friendUserId,
                        targetCurrencyCode = currentUi.targetCurrency,
                        actorUserId = userId.value,
                    )
                if (result.isSuccess) {
                    _success.value = true
                } else {
                    _error.value = "Conversion failed. Please try again."
                }
                _isSubmitting.value = false
            }
        }
    }
