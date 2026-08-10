package com.splitease.app.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountProfileUi(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
)

data class AccountSettingsUiState(
    val displayNameDraft: String = "",
    val currencyCode: String = AppCurrencies.DEFAULT,
    val appLocale: AppLocale = AppLocale.DEFAULT,
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
                                displayName =
                                    local?.displayName?.takeIf { it.isNotBlank() }
                                        ?: signedIn.user.displayName,
                                email = signedIn.user.email,
                                photoUrl = local?.photoUrl,
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
                        errorMessage = result.exceptionOrNull()?.message,
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
                        errorMessage = result.exceptionOrNull()?.message,
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
    }
