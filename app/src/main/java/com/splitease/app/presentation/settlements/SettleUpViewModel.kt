package com.splitease.app.presentation.settlements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.payment.RecordPaymentInput
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SettleUpUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettleUpViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val paymentInteractor: PaymentInteractor,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _uiState = MutableStateFlow(SettleUpUiState())
        val uiState: StateFlow<SettleUpUiState> = _uiState.asStateFlow()

        fun currentUserId(): String? = userId.value

        fun recordSettlement(
            fromUserId: String,
            toUserId: String,
            amountText: String,
            currencyCode: String,
            groupId: String?,
            note: String?,
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
                val currency =
                    currencyCode.ifBlank { appSettingsRepository.getCurrencyCode() }
                val result =
                    paymentInteractor.recordPayment(
                        RecordPaymentInput(
                            fromUserId = fromUserId,
                            toUserId = toUserId,
                            amount = amount,
                            currencyCode = currency,
                            groupId = groupId,
                            note = note,
                        ),
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }
    }
