package com.splitease.app.presentation.settlements

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.payment.RecordPaymentInput
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

data class SettlePartyUi(
    val userId: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
)

data class SettleUpUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val payer: SettlePartyUi? = null,
    val payee: SettlePartyUi? = null,
    val isReady: Boolean = false,
)

@HiltViewModel
class SettleUpViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val paymentInteractor: PaymentInteractor,
        private val appSettingsRepository: AppSettingsRepository,
        private val userRepository: UserRepository,
        private val friendRepository: FriendRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        val currentUserId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _uiState = MutableStateFlow(SettleUpUiState())
        val uiState: StateFlow<SettleUpUiState> = _uiState.asStateFlow()

        fun prepare(
            fromUserId: String,
            toUserId: String,
            fromLabel: String,
            toLabel: String,
        ) {
            viewModelScope.launch {
                val youLabel = appContext.getString(R.string.you_label)
                val payer =
                    resolveParty(
                        userId = fromUserId,
                        fallbackName = fromLabel,
                        youLabel = youLabel,
                    )
                val payee =
                    resolveParty(
                        userId = toUserId,
                        fallbackName = toLabel,
                        youLabel = youLabel,
                    )
                _uiState.update {
                    it.copy(payer = payer, payee = payee, isReady = true, errorMessage = null)
                }
            }
        }

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
                            it.copy(
                                isSubmitting = false,
                                errorMessage = appContext.getString(R.string.msg_enter_valid_amount),
                            )
                        }
                        return@launch
                    }
                val currency =
                    currencyCode.ifBlank { appSettingsRepository.getCurrencyCode() }
                val resolvedNote =
                    note?.trim()?.ifBlank { null }
                        ?: appContext.getString(R.string.payment_completed_note)
                val result =
                    paymentInteractor.recordPayment(
                        RecordPaymentInput(
                            fromUserId = fromUserId,
                            toUserId = toUserId,
                            amount = amount,
                            currencyCode = currency,
                            groupId = groupId,
                            note = resolvedNote,
                        ),
                    )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                    )
                }
                if (result.isSuccess) onSuccess()
            }
        }

        private suspend fun resolveParty(
            userId: String,
            fallbackName: String,
            youLabel: String,
        ): SettlePartyUi {
            val user = userRepository.getUserById(userId)
            val friend = friendRepository.getByFriendUserId(userId)
            val name =
                when {
                    !user?.displayName.isNullOrBlank() -> user!!.displayName.trim()
                    !friend?.displayNameSnapshot.isNullOrBlank() ->
                        friend!!.displayNameSnapshot.trim()
                    fallbackName.isNotBlank() && !fallbackName.equals(youLabel, ignoreCase = true) ->
                        fallbackName.trim()
                    else -> userId.take(8)
                }
            val email =
                listOfNotNull(user?.email, friend?.emailSnapshot)
                    .map { it.trim() }
                    .firstOrNull { it.contains("@") && !it.endsWith("@splitease.invalid", true) }
            val photo = user?.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
            return SettlePartyUi(
                userId = userId,
                displayName = name,
                email = email,
                photoUrl = photo,
            )
        }

        private companion object {
            const val TAG = "SettleUpViewModel"
        }
    }
