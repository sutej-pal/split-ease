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
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.domain.settings.AppSettingsRepository
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

data class AccountProfileUi(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val emailConfirmed: Boolean = true,
    val phoneCountryCode: String = "+91",
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val isGoogleOnly: Boolean = false,
)

data class AccountSettingsUiState(
    val displayNameDraft: String = "",
    val expandedRow: AccountRow? = null,
    val currencyCode: String = AppCurrencies.DEFAULT,
    val appLocale: AppLocale = AppLocale.DEFAULT,
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

    // Phone row drafts
    val phoneCountryCodeDraft: String = "+91",
    val phoneNumberDraft: String = "",
    val isPhoneSaving: Boolean = false,
    val phoneError: String? = null,

    // Password row drafts
    val currentPasswordDraft: String = "",
    val newPasswordDraft: String = "",
    val isPasswordSaving: Boolean = false,
    val passwordError: String? = null,

    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
        private val appSettingsRepository: AppSettingsRepository,
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

        private val _settings = MutableStateFlow(AccountSettingsUiState())
        val settings: StateFlow<AccountSettingsUiState> = _settings.asStateFlow()

        val currencyCode: StateFlow<String> =
            appSettingsRepository
                .observeCurrencyCode()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrencies.DEFAULT)

        val appLocale: StateFlow<AppLocale> =
            appSettingsRepository
                .observeAppLocale()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLocale.DEFAULT)

        init {
            viewModelScope.launch {
                combine(
                    authRepository.observeSecondaryEmails(),
                    profile,
                ) { secondaries, prof ->
                    _settings.update {
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
            _settings.update { state ->
                val nextExpanded = if (state.expandedRow == row) null else row
                val prof = profile.value
                state.copy(
                    expandedRow = nextExpanded,
                    emailError = null,
                    phoneError = null,
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

        fun syncSettingsDraftFromProfile() {
            val name = profile.value.displayName
            _settings.update {
                it.copy(
                    displayNameDraft = name,
                    currencyCode = currencyCode.value,
                    appLocale = appLocale.value,
                    errorMessage = null,
                    infoMessage = null,
                )
            }
        }

        fun onDisplayNameDraftChange(value: String) {
            _settings.update { it.copy(displayNameDraft = value, errorMessage = null, infoMessage = null) }
        }

        fun clearMessages() {
            _settings.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        fun saveDisplayName() {
            if (_settings.value.isSaving) return
            val name = _settings.value.displayNameDraft.trim()
            if (name.isBlank()) {
                _settings.update {
                    it.copy(errorMessage = appContext.getString(R.string.msg_display_name_required))
                }
                return
            }
            viewModelScope.launch {
                _settings.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
                val result = authRepository.updateDisplayName(name)
                _settings.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                        infoMessage =
                            if (result.isSuccess) {
                                appContext.getString(R.string.msg_profile_name_saved)
                            } else {
                                null
                            },
                        displayNameDraft = if (result.isSuccess) name else it.displayNameDraft,
                    )
                }
            }
        }

        fun updatePhoto(photoUri: String) {
            if (photoUri.isBlank()) return
            viewModelScope.launch {
                _settings.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
                val result = authRepository.updateProfilePhoto(photoUri)
                _settings.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                        infoMessage =
                            if (result.isSuccess) {
                                appContext.getString(R.string.msg_profile_photo_saved)
                            } else {
                                null
                            },
                    )
                }
            }
        }

        // Phone row
        fun onPhoneCountryCodeDraftChange(code: String) {
            _settings.update { it.copy(phoneCountryCodeDraft = code, phoneError = null) }
        }

        fun onPhoneNumberDraftChange(num: String) {
            _settings.update { it.copy(phoneNumberDraft = num, phoneError = null) }
        }

        fun savePhone() {
            if (_settings.value.isPhoneSaving) return
            val code = _settings.value.phoneCountryCodeDraft.ifBlank { "+91" }
            val digits = _settings.value.phoneNumberDraft.filter { it.isDigit() }
            if (digits.length !in 7..15) {
                _settings.update {
                    it.copy(phoneError = appContext.getString(R.string.signup_error_phone_invalid))
                }
                return
            }
            viewModelScope.launch {
                _settings.update { it.copy(isPhoneSaving = true, phoneError = null) }
                val result = authRepository.updatePhone(code, digits)
                if (result.isSuccess) {
                    _settings.update {
                        it.copy(
                            isPhoneSaving = false,
                            expandedRow = null,
                            infoMessage = appContext.getString(R.string.msg_profile_phone_saved),
                        )
                    }
                } else {
                    _settings.update {
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
            _settings.update { it.copy(currentPasswordDraft = pass, passwordError = null) }
        }

        fun onNewPasswordDraftChange(pass: String) {
            _settings.update { it.copy(newPasswordDraft = pass, passwordError = null) }
        }

        fun updatePassword() {
            if (_settings.value.isPasswordSaving) return
            val current = _settings.value.currentPasswordDraft.trim()
            val newPass = _settings.value.newPasswordDraft.trim()
            if (current.isBlank()) {
                _settings.update { it.copy(passwordError = appContext.getString(R.string.error_password_required)) }
                return
            }
            if (newPass.length < 8) {
                _settings.update { it.copy(passwordError = appContext.getString(R.string.signup_error_password_short)) }
                return
            }
            viewModelScope.launch {
                _settings.update { it.copy(isPasswordSaving = true, passwordError = null) }
                val verifyRes = authRepository.verifyCurrentPassword(current)
                if (verifyRes.isFailure) {
                    _settings.update {
                        it.copy(
                            isPasswordSaving = false,
                            passwordError = appContext.getString(R.string.error_invalid_credentials),
                        )
                    }
                    return@launch
                }
                val updateRes = authRepository.updatePassword(newPass, hydrateSession = false)
                if (updateRes.isSuccess) {
                    _settings.update {
                        it.copy(
                            isPasswordSaving = false,
                            expandedRow = null,
                            currentPasswordDraft = "",
                            newPasswordDraft = "",
                            infoMessage = appContext.getString(R.string.account_password_updated_toast),
                        )
                    }
                } else {
                    _settings.update {
                        it.copy(
                            isPasswordSaving = false,
                            passwordError = ErrorMessages.message(appContext, TAG, updateRes.exceptionOrNull() ?: Exception("Failed")),
                        )
                    }
                }
            }
        }

        // Email row
        fun toggleAddEmailForm() {
            _settings.update {
                it.copy(
                    showAddEmailForm = !it.showAddEmailForm,
                    newEmailDraft = "",
                    emailPasswordDraft = "",
                    emailError = null,
                )
            }
        }

        fun onNewEmailDraftChange(email: String) {
            _settings.update { it.copy(newEmailDraft = email, emailError = null) }
        }

        fun onEmailPasswordDraftChange(pass: String) {
            _settings.update { it.copy(emailPasswordDraft = pass, emailError = null) }
        }

        fun addSecondaryEmail() {
            if (_settings.value.isEmailSaving) return
            val email = _settings.value.newEmailDraft.trim()
            val pass = _settings.value.emailPasswordDraft.trim()
            if (email.isBlank() || !email.contains("@")) {
                _settings.update { it.copy(emailError = appContext.getString(R.string.error_invalid_email)) }
                return
            }
            if (!_settings.value.isGoogleOnly && pass.isBlank()) {
                _settings.update { it.copy(emailError = appContext.getString(R.string.error_password_required)) }
                return
            }
            viewModelScope.launch {
                _settings.update { it.copy(isEmailSaving = true, emailError = null) }
                val result = authRepository.addSecondaryEmail(email, pass.takeIf { !_settings.value.isGoogleOnly })
                if (result.isSuccess) {
                    _settings.update {
                        it.copy(
                            isEmailSaving = false,
                            showAddEmailForm = false,
                            newEmailDraft = "",
                            emailPasswordDraft = "",
                            infoMessage = appContext.getString(R.string.verify_email_sent),
                        )
                    }
                } else {
                    _settings.update {
                        it.copy(
                            isEmailSaving = false,
                            emailError = ErrorMessages.message(appContext, TAG, result.exceptionOrNull() ?: Exception("Failed")),
                        )
                    }
                }
            }
        }

        fun resendPrimaryVerification() {
            val email = profile.value.email
            if (email.isBlank()) return
            viewModelScope.launch {
                val res = authRepository.resendSignupConfirmation(email)
                _settings.update {
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
            _settings.update { it.copy(primaryOtpDraft = otp, emailError = null) }
        }

        fun verifyPrimaryEmail() {
            val email = profile.value.email
            val code = _settings.value.primaryOtpDraft.trim()
            if (code.length != 6) return
            viewModelScope.launch {
                _settings.update { it.copy(isEmailSaving = true, emailError = null) }
                val res = authRepository.verifySignupOtp(email, code)
                if (res.isSuccess) {
                    _settings.update {
                        it.copy(
                            isEmailSaving = false,
                            showPrimaryOtpInput = false,
                            primaryOtpDraft = "",
                            infoMessage = appContext.getString(R.string.verify_email_success),
                        )
                    }
                } else {
                    _settings.update {
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
                _settings.update {
                    it.copy(
                        infoMessage = if (res.isSuccess) appContext.getString(R.string.verify_email_sent) else null,
                        emailError = ErrorMessages.messageOrNull(appContext, TAG, res.exceptionOrNull()),
                    )
                }
            }
        }

        fun onSecondaryOtpDraftChange(id: String, otp: String) {
            _settings.update {
                val nextMap = it.secondaryOtpDrafts.toMutableMap().apply { put(id, otp) }
                it.copy(secondaryOtpDrafts = nextMap, emailError = null)
            }
        }

        fun verifySecondaryEmail(id: String) {
            val code = _settings.value.secondaryOtpDrafts[id]?.trim().orEmpty()
            if (code.length != 6) return
            viewModelScope.launch {
                _settings.update { it.copy(isEmailSaving = true, emailError = null) }
                val res = authRepository.verifySecondaryEmail(id, code)
                if (res.isSuccess) {
                    _settings.update {
                        val nextMap = it.secondaryOtpDrafts.toMutableMap().apply { remove(id) }
                        it.copy(
                            isEmailSaving = false,
                            secondaryOtpDrafts = nextMap,
                            infoMessage = appContext.getString(R.string.verify_email_success),
                        )
                    }
                } else {
                    _settings.update {
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
                _settings.update {
                    it.copy(
                        infoMessage = if (res.isSuccess) appContext.getString(R.string.msg_email_removed) else null,
                        emailError = ErrorMessages.messageOrNull(appContext, TAG, res.exceptionOrNull()),
                    )
                }
            }
        }

        fun signOutAllDevices() {
            if (_settings.value.isSaving) return
            viewModelScope.launch {
                _settings.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
                val result = authRepository.signOutAllDevices()
                _settings.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                    )
                }
            }
        }

        private companion object {
            const val TAG = "AccountViewModel"
        }
    }
