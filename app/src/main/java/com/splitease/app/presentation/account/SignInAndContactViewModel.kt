package com.splitease.app.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.ErrorMessages
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SecondaryEmail
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountRow {
    EMAIL,
    PHONE,
    PASSWORD,
}

data class SignInAndContactUiState(
    val expandedRow: AccountRow? = null,
    val secondaryEmails: List<SecondaryEmail> = emptyList(),
    val isGoogleOnly: Boolean = false,

    // Email row drafts
    val newEmailDraft: String = "",
    val emailPasswordDraft: String = "",
    val primaryOtpDraft: String = "",
    val secondaryOtpDrafts: Map<String, String> = emptyMap(),
    val showAddEmailForm: Boolean = false,
    val showPrimaryOtpInput: Boolean = false,
    val isEmailSaving: Boolean = false,
    val emailError: String? = null,
    val emailPasswordError: String? = null,

    // Phone row drafts
    val phoneCountryCodeDraft: String = "+91",
    val phoneNumberDraft: String = "",
    val isPhoneSaving: Boolean = false,
    val phoneError: String? = null,

    // Password row drafts
    val currentPasswordDraft: String = "",
    val newPasswordDraft: String = "",
    val isPasswordSaving: Boolean = false,
    val currentPasswordError: String? = null,
    val newPasswordError: String? = null,
    val passwordError: String? = null,

    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class SignInAndContactViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
    ) : ViewModel() {

        @OptIn(ExperimentalCoroutinesApi::class)
        val profile: StateFlow<AccountProfileUi> =
            authRepository
                .observeSession()
                .flatMapLatest { session ->
                    val signedIn = session as? AuthSession.SignedIn
                    if (signedIn == null) {
                        flowOf(AccountProfileUi())
                    } else {
                        userRepository.observeUsers().map { users ->
                            val local = users.firstOrNull { it.id == signedIn.user.userId }
                            AccountProfileUi(
                                userId = signedIn.user.userId,
                                displayName =
                                    local?.displayName?.takeIf { it.isNotBlank() }
                                        ?: signedIn.user.displayName,
                                email = signedIn.user.email,
                                emailConfirmed = signedIn.user.emailConfirmed,
                                phoneCountryCode = local?.phoneCountryCode ?: "+91",
                                phoneNumber = local?.phoneNumber.orEmpty(),
                                photoUrl = local?.photoUrl,
                                isGoogleOnly = signedIn.user.isGoogleOnly,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountProfileUi())

        private val _uiState = MutableStateFlow(SignInAndContactUiState())
        val uiState: StateFlow<SignInAndContactUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                combine(
                    authRepository.observeSecondaryEmails(),
                    profile,
                ) { secondaries, prof ->
                    _uiState.update {
                        it.copy(
                            secondaryEmails = secondaries,
                            isGoogleOnly = prof.isGoogleOnly,
                            phoneCountryCodeDraft = if (it.expandedRow != AccountRow.PHONE) prof.phoneCountryCode else it.phoneCountryCodeDraft,
                            phoneNumberDraft = if (it.expandedRow != AccountRow.PHONE) prof.phoneNumber else it.phoneNumberDraft,
                        )
                    }
                }.collect {}
            }
        }

        fun toggleRow(row: AccountRow) {
            _uiState.update { state ->
                val nextExpanded = if (state.expandedRow == row) null else row
                val prof = profile.value
                state.copy(
                    expandedRow = nextExpanded,
                    emailError = null,
                    emailPasswordError = null,
                    phoneError = null,
                    currentPasswordError = null,
                    newPasswordError = null,
                    passwordError = null,
                    showAddEmailForm = false,
                    newEmailDraft = "",
                    emailPasswordDraft = "",
                    currentPasswordDraft = "",
                    newPasswordDraft = "",
                    phoneCountryCodeDraft = prof.phoneCountryCode,
                    phoneNumberDraft = prof.phoneNumber,
                )
            }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        // Phone row
        fun onPhoneCountryCodeDraftChange(code: String) {
            _uiState.update { it.copy(phoneCountryCodeDraft = code, phoneError = null) }
        }

        fun onPhoneNumberDraftChange(num: String) {
            _uiState.update { it.copy(phoneNumberDraft = num, phoneError = null) }
        }

        fun savePhone() {
            if (_uiState.value.isPhoneSaving) return
            val code = _uiState.value.phoneCountryCodeDraft.ifBlank { "+91" }
            val digits = _uiState.value.phoneNumberDraft.filter { it.isDigit() }
            if (digits.length !in 7..15) {
                _uiState.update {
                    it.copy(phoneError = appContext.getString(R.string.signup_error_phone_invalid))
                }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isPhoneSaving = true, phoneError = null) }
                val result = authRepository.updatePhone(code, digits)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isPhoneSaving = false,
                            expandedRow = null,
                            infoMessage = appContext.getString(R.string.msg_profile_phone_saved),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isPhoneSaving = false,
                            phoneError = ErrorMessages.message(appContext, TAG, result.exceptionOrNull() ?: Exception("Failed")),
                        )
                    }
                }
            }
        }

        // Password row
        fun onCurrentPasswordDraftChange(pass: String) {
            _uiState.update { it.copy(currentPasswordDraft = pass, currentPasswordError = null, passwordError = null) }
        }

        fun onNewPasswordDraftChange(pass: String) {
            _uiState.update { it.copy(newPasswordDraft = pass, newPasswordError = null, passwordError = null) }
        }

        fun updatePassword() {
            if (_uiState.value.isPasswordSaving) return
            val current = _uiState.value.currentPasswordDraft.trim()
            val newPass = _uiState.value.newPasswordDraft.trim()
            if (current.isBlank()) {
                val err = appContext.getString(R.string.error_password_required)
                _uiState.update {
                    it.copy(
                        currentPasswordError = err,
                        newPasswordError = null,
                        passwordError = err,
                    )
                }
                return
            }
            if (newPass.isBlank()) {
                val err = appContext.getString(R.string.error_new_password_required)
                _uiState.update {
                    it.copy(
                        newPasswordError = err,
                        currentPasswordError = null,
                        passwordError = err,
                    )
                }
                return
            }
            if (newPass.length < 8) {
                val err = appContext.getString(R.string.signup_error_password_short)
                _uiState.update {
                    it.copy(
                        newPasswordError = err,
                        currentPasswordError = null,
                        passwordError = err,
                    )
                }
                return
            }
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isPasswordSaving = true,
                        currentPasswordError = null,
                        newPasswordError = null,
                        passwordError = null,
                    )
                }
                val verifyRes = authRepository.verifyCurrentPassword(current)
                if (verifyRes.isFailure) {
                    val errText = ErrorMessages.message(
                        appContext,
                        TAG,
                        verifyRes.exceptionOrNull() ?: Exception("Failed"),
                    )
                    _uiState.update {
                        it.copy(
                            isPasswordSaving = false,
                            currentPasswordError = errText,
                            newPasswordError = null,
                            passwordError = errText,
                        )
                    }
                    return@launch
                }
                val updateRes = authRepository.updatePassword(newPass, hydrateSession = false)
                if (updateRes.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isPasswordSaving = false,
                            expandedRow = null,
                            currentPasswordDraft = "",
                            newPasswordDraft = "",
                            infoMessage = appContext.getString(R.string.account_password_updated_toast),
                        )
                    }
                } else {
                    val errText = ErrorMessages.message(
                        appContext,
                        TAG,
                        updateRes.exceptionOrNull() ?: Exception("Failed"),
                    )
                    _uiState.update {
                        it.copy(
                            isPasswordSaving = false,
                            newPasswordError = errText,
                            currentPasswordError = null,
                            passwordError = errText,
                        )
                    }
                }
            }
        }

        // Email row
        fun toggleAddEmailForm() {
            _uiState.update {
                it.copy(
                    showAddEmailForm = !it.showAddEmailForm,
                    newEmailDraft = "",
                    emailPasswordDraft = "",
                    emailError = null,
                    emailPasswordError = null,
                )
            }
        }

        fun onNewEmailDraftChange(email: String) {
            _uiState.update { it.copy(newEmailDraft = email, emailError = null) }
        }

        fun onEmailPasswordDraftChange(pass: String) {
            _uiState.update { it.copy(emailPasswordDraft = pass, emailPasswordError = null) }
        }

        fun addSecondaryEmail() {
            if (_uiState.value.isEmailSaving) return
            val email = _uiState.value.newEmailDraft.trim()
            val pass = _uiState.value.emailPasswordDraft.trim()
            if (email.isBlank() || !email.contains("@")) {
                _uiState.update {
                    it.copy(
                        emailError = appContext.getString(R.string.error_invalid_email),
                        emailPasswordError = null,
                    )
                }
                return
            }
            if (!_uiState.value.isGoogleOnly && pass.isBlank()) {
                _uiState.update {
                    it.copy(
                        emailPasswordError = appContext.getString(R.string.error_password_required),
                        emailError = null,
                    )
                }
                return
            }
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isEmailSaving = true,
                        emailError = null,
                        emailPasswordError = null,
                    )
                }
                val result = authRepository.addSecondaryEmail(email, pass.takeIf { !_uiState.value.isGoogleOnly })
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isEmailSaving = false,
                            showAddEmailForm = false,
                            newEmailDraft = "",
                            emailPasswordDraft = "",
                            infoMessage = appContext.getString(R.string.verify_email_sent),
                        )
                    }
                } else {
                    val rawErr = ErrorMessages.message(
                        appContext,
                        TAG,
                        result.exceptionOrNull() ?: Exception("Failed"),
                    )
                    val errText = if (rawErr.equals("Failed to add email address.", ignoreCase = true) ||
                        rawErr.equals("Secondary email operation failed.", ignoreCase = true) ||
                        rawErr.equals("Failed", ignoreCase = true)
                    ) {
                        appContext.getString(R.string.error_email_cannot_be_added)
                    } else {
                        rawErr
                    }
                    val isPassErr = isPasswordRelatedError(errText)
                    _uiState.update {
                        it.copy(
                            isEmailSaving = false,
                            emailError = if (isPassErr) null else errText,
                            emailPasswordError = if (isPassErr) errText else null,
                        )
                    }
                }
            }
        }

        private fun isPasswordRelatedError(msg: String): Boolean {
            val lower = msg.lowercase()
            return lower.contains("password") || lower.contains("credential") || lower.contains("right")
        }

        fun resendPrimaryVerification() {
            val email = profile.value.email
            if (email.isBlank()) return
            viewModelScope.launch {
                val res = authRepository.resendSignupConfirmation(email)
                _uiState.update {
                    if (res.isSuccess) {
                        it.copy(
                            showPrimaryOtpInput = true,
                            emailError = null,
                            infoMessage = appContext.getString(R.string.verify_email_sent),
                        )
                    } else {
                        it.copy(
                            emailError = ErrorMessages.message(
                                appContext,
                                TAG,
                                res.exceptionOrNull() ?: Exception("Failed"),
                            ),
                            infoMessage = null,
                        )
                    }
                }
            }
        }

        fun onPrimaryOtpDraftChange(otp: String) {
            _uiState.update { it.copy(primaryOtpDraft = otp, emailError = null) }
        }

        fun verifyPrimaryEmail() {
            val email = profile.value.email
            val code = _uiState.value.primaryOtpDraft.trim()
            if (code.length != 6) return
            viewModelScope.launch {
                _uiState.update { it.copy(isEmailSaving = true, emailError = null) }
                val res = authRepository.verifySignupOtp(email, code)
                if (res.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isEmailSaving = false,
                            showPrimaryOtpInput = false,
                            primaryOtpDraft = "",
                            infoMessage = appContext.getString(R.string.verify_email_success),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isEmailSaving = false,
                            emailError = ErrorMessages.message(appContext, TAG, res.exceptionOrNull() ?: Exception("Failed")),
                        )
                    }
                }
            }
        }

        fun resendSecondaryEmail(id: String) {
            viewModelScope.launch {
                val res = authRepository.resendSecondaryEmail(id)
                _uiState.update {
                    it.copy(
                        infoMessage = if (res.isSuccess) appContext.getString(R.string.verify_email_sent) else null,
                        emailError = ErrorMessages.messageOrNull(appContext, TAG, res.exceptionOrNull()),
                    )
                }
            }
        }

        fun onSecondaryOtpDraftChange(id: String, otp: String) {
            _uiState.update {
                val nextMap = it.secondaryOtpDrafts.toMutableMap().apply { put(id, otp) }
                it.copy(secondaryOtpDrafts = nextMap, emailError = null)
            }
        }

        fun verifySecondaryEmail(id: String) {
            val code = _uiState.value.secondaryOtpDrafts[id]?.trim().orEmpty()
            if (code.length != 6) return
            viewModelScope.launch {
                _uiState.update { it.copy(isEmailSaving = true, emailError = null) }
                val res = authRepository.verifySecondaryEmail(id, code)
                if (res.isSuccess) {
                    _uiState.update {
                        val nextMap = it.secondaryOtpDrafts.toMutableMap().apply { remove(id) }
                        it.copy(
                            isEmailSaving = false,
                            secondaryOtpDrafts = nextMap,
                            infoMessage = appContext.getString(R.string.verify_email_success),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isEmailSaving = false,
                            emailError = ErrorMessages.message(appContext, TAG, res.exceptionOrNull() ?: Exception("Failed")),
                        )
                    }
                }
            }
        }

        fun removeSecondaryEmail(id: String) {
            viewModelScope.launch {
                val res = authRepository.removeSecondaryEmail(id)
                _uiState.update {
                    it.copy(
                        infoMessage = if (res.isSuccess) appContext.getString(R.string.msg_email_removed) else null,
                        emailError = ErrorMessages.messageOrNull(appContext, TAG, res.exceptionOrNull()),
                    )
                }
            }
        }

        private companion object {
            const val TAG = "SignInAndContactViewModel"
        }
    }
