package com.splitease.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        val currencyCode: StateFlow<String> =
            appSettingsRepository
                .observeCurrencyCode()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrencies.DEFAULT)

        val themeMode: StateFlow<ThemeMode> =
            appSettingsRepository
                .observeThemeMode()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

        val biometricLockEnabled: StateFlow<Boolean> =
            appSettingsRepository
                .observeBiometricLockEnabled()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val authTimeout: StateFlow<AuthTimeout> =
            appSettingsRepository
                .observeAuthTimeout()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthTimeout.DEFAULT)

        fun setCurrency(code: String) {
            viewModelScope.launch {
                appSettingsRepository.setCurrencyCode(code)
            }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch {
                appSettingsRepository.setThemeMode(mode)
            }
        }

        fun setBiometricLockEnabled(enabled: Boolean) {
            viewModelScope.launch {
                appSettingsRepository.setBiometricLockEnabled(enabled)
            }
        }

        fun setAuthTimeout(timeout: AuthTimeout) {
            viewModelScope.launch {
                appSettingsRepository.setAuthTimeout(timeout)
            }
        }
    }
