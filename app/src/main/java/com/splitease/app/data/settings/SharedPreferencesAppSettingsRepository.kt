package com.splitease.app.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.domain.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SharedPreferences]-backed [AppSettingsRepository].
 *
 * @property context Application context.
 */
@Singleton
class SharedPreferencesAppSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppSettingsRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val currencyFlow = MutableStateFlow(readCurrency())
        private val simplifyMapFlow = MutableStateFlow(readSimplifyMap())
        private val themeModeFlow = MutableStateFlow(readThemeMode())
        private val biometricLockFlow = MutableStateFlow(readBiometricLock())
        private val authTimeoutFlow = MutableStateFlow(readAuthTimeout())
        private val appLocaleFlow = MutableStateFlow(readAppLocale())

        override fun observeCurrencyCode(): Flow<String> = currencyFlow.asStateFlow()

        override suspend fun getCurrencyCode(): String =
            withContext(Dispatchers.IO) {
                readCurrency()
            }

        override suspend fun setCurrencyCode(code: String) {
            val normalized = code.trim().uppercase().ifBlank { AppCurrencies.DEFAULT }
            withContext(Dispatchers.IO) {
                prefs.edit().putString(KEY_CURRENCY, normalized).apply()
            }
            currencyFlow.value = normalized
        }

        override fun observeSimplifyGroupDebts(groupId: String): Flow<Boolean> =
            simplifyMapFlow.map { map -> map[groupId] ?: true }

        override fun observeSimplifyGroupDebtsMap(): Flow<Map<String, Boolean>> =
            simplifyMapFlow.asStateFlow()

        override suspend fun getSimplifyGroupDebts(groupId: String): Boolean =
            withContext(Dispatchers.IO) {
                if (!prefs.contains(simplifyKey(groupId))) {
                    true
                } else {
                    prefs.getBoolean(simplifyKey(groupId), true)
                }
            }

        override suspend fun setSimplifyGroupDebts(groupId: String, enabled: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit().putBoolean(simplifyKey(groupId), enabled).apply()
            }
            simplifyMapFlow.value = simplifyMapFlow.value + (groupId to enabled)
        }

        override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow.asStateFlow()

        override suspend fun getThemeMode(): ThemeMode =
            withContext(Dispatchers.IO) {
                readThemeMode()
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            withContext(Dispatchers.IO) {
                prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
            }
            themeModeFlow.value = mode
        }

        override fun observeBiometricLockEnabled(): Flow<Boolean> = biometricLockFlow.asStateFlow()

        override suspend fun getBiometricLockEnabled(): Boolean =
            withContext(Dispatchers.IO) {
                readBiometricLock()
            }

        override suspend fun setBiometricLockEnabled(enabled: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
            }
            biometricLockFlow.value = enabled
        }

        override fun observeAuthTimeout(): Flow<AuthTimeout> = authTimeoutFlow.asStateFlow()

        override suspend fun getAuthTimeout(): AuthTimeout =
            withContext(Dispatchers.IO) {
                readAuthTimeout()
            }

        override suspend fun setAuthTimeout(timeout: AuthTimeout) {
            withContext(Dispatchers.IO) {
                prefs.edit().putString(KEY_AUTH_TIMEOUT, timeout.name).apply()
            }
            authTimeoutFlow.value = timeout
        }

        override fun observeAppLocale(): Flow<AppLocale> = appLocaleFlow.asStateFlow()

        override suspend fun getAppLocale(): AppLocale =
            withContext(Dispatchers.IO) {
                readAppLocale()
            }

        override suspend fun setAppLocale(locale: AppLocale) {
            withContext(Dispatchers.IO) {
                prefs.edit().putString(KEY_APP_LOCALE, locale.name).apply()
            }
            appLocaleFlow.value = locale
            applyAppLocale(locale)
        }

        /** Applies the stored locale at process start (before Compose). */
        fun applyStoredLocale() {
            applyAppLocale(readAppLocale())
        }

        private fun applyAppLocale(locale: AppLocale) {
            val locales =
                if (locale == AppLocale.SYSTEM || locale.tag.isBlank()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(locale.tag)
                }
            AppCompatDelegate.setApplicationLocales(locales)
        }

        private fun readCurrency(): String =
            prefs.getString(KEY_CURRENCY, AppCurrencies.DEFAULT)
                ?.trim()
                ?.uppercase()
                ?.ifBlank { AppCurrencies.DEFAULT }
                ?: AppCurrencies.DEFAULT

        private fun readThemeMode(): ThemeMode =
            ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name))

        private fun readBiometricLock(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

        private fun readAuthTimeout(): AuthTimeout =
            AuthTimeout.fromStorage(prefs.getString(KEY_AUTH_TIMEOUT, AuthTimeout.DEFAULT.name))

        private fun readAppLocale(): AppLocale =
            AppLocale.fromStorage(prefs.getString(KEY_APP_LOCALE, AppLocale.DEFAULT.name))

        private fun readSimplifyMap(): Map<String, Boolean> =
            prefs.all
                .mapNotNull { (key, value) ->
                    if (!key.startsWith(KEY_SIMPLIFY_PREFIX) || value !is Boolean) return@mapNotNull null
                    key.removePrefix(KEY_SIMPLIFY_PREFIX) to value
                }.toMap()

        companion object {
            private const val PREFS_NAME = "splitease_settings"
            private const val KEY_CURRENCY = "currency_code"
            private const val KEY_THEME_MODE = "theme_mode"
            private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
            private const val KEY_AUTH_TIMEOUT = "auth_timeout"
            private const val KEY_APP_LOCALE = "app_locale"
            private const val KEY_SIMPLIFY_PREFIX = "simplify_debts_"

            private fun simplifyKey(groupId: String) = KEY_SIMPLIFY_PREFIX + groupId
        }
    }
