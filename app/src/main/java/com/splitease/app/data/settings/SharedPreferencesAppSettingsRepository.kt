package com.splitease.app.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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
        private val pendingInviteTokenFlow = MutableStateFlow(readPendingInviteToken())
        private val pendingInviteOpenTargetFlow = MutableStateFlow(readPendingInviteOpenTarget())
        private val pendingNotificationGroupIdFlow =
            MutableStateFlow(readPendingNotificationGroupId())
        private val muteAllFlow = MutableStateFlow(readMuteAll())
        private val mutedGroupIdsFlow = MutableStateFlow(readMutedGroupIds())

        override fun observeCurrencyCode(): Flow<String> = currencyFlow.asStateFlow()

        override suspend fun getCurrencyCode(): String =
            withContext(Dispatchers.IO) {
                readCurrency()
            }

        override suspend fun setCurrencyCode(code: String) {
            val normalized = AppCurrencies.normalizeOrDefault(code)
            withContext(Dispatchers.IO) {
                prefs.edit { putString(KEY_CURRENCY, normalized) }
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
                prefs.edit { putBoolean(simplifyKey(groupId), enabled) }
            }
            simplifyMapFlow.value += (groupId to enabled)
        }

        override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow.asStateFlow()

        override suspend fun getThemeMode(): ThemeMode =
            withContext(Dispatchers.IO) {
                readThemeMode()
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            withContext(Dispatchers.IO) {
                prefs.edit { putString(KEY_THEME_MODE, mode.name) }
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
                prefs.edit { putBoolean(KEY_BIOMETRIC_LOCK, enabled) }
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
                prefs.edit { putString(KEY_AUTH_TIMEOUT, timeout.name) }
            }
            authTimeoutFlow.value = timeout
        }

        override suspend fun getOnboardingEmailSent(userId: String): Boolean =
            withContext(Dispatchers.IO) {
                prefs.getBoolean(onboardingEmailSentKey(userId), false)
            }

        override suspend fun setOnboardingEmailSent(userId: String, sent: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit { putBoolean(onboardingEmailSentKey(userId), sent) }
            }
        }

        override suspend fun getPendingWelcomeEmailUserId(): String? =
            withContext(Dispatchers.IO) {
                prefs.getString(KEY_PENDING_WELCOME_EMAIL_USER_ID, null)?.takeIf { it.isNotBlank() }
            }

        override suspend fun setPendingWelcomeEmailUserId(userId: String?) {
            withContext(Dispatchers.IO) {
                prefs.edit {
                    if (userId.isNullOrBlank()) {
                        remove(KEY_PENDING_WELCOME_EMAIL_USER_ID)
                    } else {
                        putString(KEY_PENDING_WELCOME_EMAIL_USER_ID, userId)
                    }
                }
            }
        }

        override fun observePendingInviteToken(): Flow<String?> = pendingInviteTokenFlow.asStateFlow()

        override suspend fun getPendingInviteToken(): String? {
            // Prefer in-memory (published before disk I/O) so signed-in deep-link
            // claim does not race the prefs write from MainActivity.
            pendingInviteTokenFlow.value?.let { return it }
            return withContext(Dispatchers.IO) { readPendingInviteToken() }
        }

        override suspend fun setPendingInviteToken(token: String?) {
            val normalized = token?.trim()?.takeIf { it.isNotEmpty() }
            // Publish in-memory first so signed-in deep-link claim can run without
            // waiting on disk I/O (MainActivity stores the token asynchronously).
            if (normalized != null && pendingInviteTokenFlow.value == normalized) {
                // Same link opened again — force collectors to re-run claim.
                pendingInviteTokenFlow.value = null
            }
            pendingInviteTokenFlow.value = normalized
            if (normalized != null) {
                pendingInviteOpenTargetFlow.value = null
            }
            withContext(Dispatchers.IO) {
                prefs.edit {
                    if (normalized == null) {
                        remove(KEY_PENDING_INVITE_TOKEN)
                    } else {
                        putString(KEY_PENDING_INVITE_TOKEN, normalized)
                        // Reset until preview / sync captures the destination again.
                        remove(KEY_PENDING_INVITE_OPEN_TARGET)
                    }
                }
            }
        }

        override fun observePendingInviteOpenTarget(): Flow<String?> =
            pendingInviteOpenTargetFlow.asStateFlow()

        override suspend fun getPendingInviteOpenTarget(): String? =
            withContext(Dispatchers.IO) {
                readPendingInviteOpenTarget()
            }

        override suspend fun setPendingInviteOpenTarget(target: String?) {
            val normalized = target?.trim()?.takeIf { it.isNotEmpty() }
            withContext(Dispatchers.IO) {
                prefs.edit {
                    if (normalized == null) {
                        remove(KEY_PENDING_INVITE_OPEN_TARGET)
                    } else {
                        putString(KEY_PENDING_INVITE_OPEN_TARGET, normalized)
                    }
                }
            }
            pendingInviteOpenTargetFlow.value = normalized
        }

        override fun observePendingNotificationGroupId(): Flow<String?> =
            pendingNotificationGroupIdFlow.asStateFlow()

        override suspend fun getPendingNotificationGroupId(): String? {
            pendingNotificationGroupIdFlow.value?.let { return it }
            return withContext(Dispatchers.IO) { readPendingNotificationGroupId() }
        }

        override suspend fun setPendingNotificationGroupId(groupId: String?) {
            val normalized = groupId?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized != null && pendingNotificationGroupIdFlow.value == normalized) {
                pendingNotificationGroupIdFlow.value = null
            }
            pendingNotificationGroupIdFlow.value = normalized
            withContext(Dispatchers.IO) {
                prefs.edit {
                    if (normalized == null) {
                        remove(KEY_PENDING_NOTIFICATION_GROUP_ID)
                    } else {
                        putString(KEY_PENDING_NOTIFICATION_GROUP_ID, normalized)
                    }
                }
            }
        }

        override suspend fun getInstallReferrerChecked(): Boolean =
            withContext(Dispatchers.IO) {
                prefs.getBoolean(KEY_INSTALL_REFERRER_CHECKED, false)
            }

        override suspend fun setInstallReferrerChecked(checked: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit { putBoolean(KEY_INSTALL_REFERRER_CHECKED, checked) }
            }
        }

        override fun observeAppLocale(): Flow<AppLocale> = appLocaleFlow.asStateFlow()

        override suspend fun getAppLocale(): AppLocale =
            withContext(Dispatchers.IO) {
                readAppLocale()
            }

        override suspend fun setAppLocale(locale: AppLocale) {
            withContext(Dispatchers.IO) {
                prefs.edit { putString(KEY_APP_LOCALE, locale.name) }
            }
            appLocaleFlow.value = locale
            applyAppLocale(locale)
        }

        override fun observeNotificationsMutedAll(): Flow<Boolean> = muteAllFlow.asStateFlow()

        override suspend fun getNotificationsMutedAll(): Boolean =
            withContext(Dispatchers.IO) {
                readMuteAll()
            }

        override suspend fun setNotificationsMutedAll(muted: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit {
                    putBoolean(KEY_MUTE_ALL, muted)
                    putLong(KEY_NOTIFICATION_PREFS_UPDATED_AT, System.currentTimeMillis())
                }
            }
            muteAllFlow.value = muted
        }

        override fun observeGroupNotificationsMuted(groupId: String): Flow<Boolean> =
            mutedGroupIdsFlow.map { ids -> groupId in ids }

        override suspend fun getGroupNotificationsMuted(groupId: String): Boolean =
            withContext(Dispatchers.IO) {
                groupId in readMutedGroupIds()
            }

        override suspend fun setGroupNotificationsMuted(groupId: String, muted: Boolean) {
            val normalized = groupId.trim().takeIf { it.isNotEmpty() } ?: return
            val next =
                withContext(Dispatchers.IO) {
                    val updated =
                        readMutedGroupIds().toMutableSet().apply {
                            if (muted) add(normalized) else remove(normalized)
                        }
                    prefs.edit {
                        putStringSet(KEY_MUTED_GROUP_IDS, HashSet(updated))
                        putLong(KEY_NOTIFICATION_PREFS_UPDATED_AT, System.currentTimeMillis())
                    }
                    updated
                }
            mutedGroupIdsFlow.value = next
        }

        override suspend fun getMutedGroupIds(): Set<String> =
            withContext(Dispatchers.IO) {
                readMutedGroupIds()
            }

        override suspend fun getNotificationPrefsUpdatedAtEpochMs(): Long =
            withContext(Dispatchers.IO) {
                prefs.getLong(KEY_NOTIFICATION_PREFS_UPDATED_AT, 0L)
            }

        override suspend fun applyRemoteNotificationPrefs(
            muteAll: Boolean,
            mutedGroupIds: Set<String>,
            updatedAtEpochMs: Long,
        ) {
            val ids = mutedGroupIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            withContext(Dispatchers.IO) {
                prefs.edit {
                    putBoolean(KEY_MUTE_ALL, muteAll)
                    putStringSet(KEY_MUTED_GROUP_IDS, HashSet(ids))
                    putLong(KEY_NOTIFICATION_PREFS_UPDATED_AT, updatedAtEpochMs)
                }
            }
            muteAllFlow.value = muteAll
            mutedGroupIdsFlow.value = ids
        }

        override suspend fun getNotificationPermissionPrompted(): Boolean =
            withContext(Dispatchers.IO) {
                prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)
            }

        override suspend fun setNotificationPermissionPrompted(prompted: Boolean) {
            withContext(Dispatchers.IO) {
                prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, prompted) }
            }
        }

        override suspend fun clearSessionData() {
            val keepTheme = themeModeFlow.value
            val keepLocale = appLocaleFlow.value
            val keepInviteToken = pendingInviteTokenFlow.value
            val keepInviteOpenTarget = pendingInviteOpenTargetFlow.value
            val keepPendingWelcomeUserId =
                withContext(Dispatchers.IO) {
                    prefs.getString(KEY_PENDING_WELCOME_EMAIL_USER_ID, null)?.takeIf { it.isNotBlank() }
                }
            val keepReferrerChecked =
                withContext(Dispatchers.IO) {
                    prefs.getBoolean(KEY_INSTALL_REFERRER_CHECKED, false)
                }
            val keepPermissionPrompted =
                withContext(Dispatchers.IO) {
                    prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)
                }
            withContext(Dispatchers.IO) {
                prefs.edit {
                    clear()
                    putString(KEY_THEME_MODE, keepTheme.name)
                    putString(KEY_APP_LOCALE, keepLocale.name)
                    putBoolean(KEY_INSTALL_REFERRER_CHECKED, keepReferrerChecked)
                    putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, keepPermissionPrompted)
                    if (!keepInviteToken.isNullOrBlank()) {
                        putString(KEY_PENDING_INVITE_TOKEN, keepInviteToken)
                    }
                    if (!keepInviteOpenTarget.isNullOrBlank()) {
                        putString(KEY_PENDING_INVITE_OPEN_TARGET, keepInviteOpenTarget)
                    }
                    if (!keepPendingWelcomeUserId.isNullOrBlank()) {
                        putString(KEY_PENDING_WELCOME_EMAIL_USER_ID, keepPendingWelcomeUserId)
                    }
                }
            }
            currencyFlow.value = AppCurrencies.DEFAULT
            simplifyMapFlow.value = emptyMap()
            themeModeFlow.value = keepTheme
            biometricLockFlow.value = false
            authTimeoutFlow.value = AuthTimeout.DEFAULT
            appLocaleFlow.value = keepLocale
            pendingInviteTokenFlow.value = keepInviteToken
            pendingInviteOpenTargetFlow.value = keepInviteOpenTarget
            pendingNotificationGroupIdFlow.value = null
            muteAllFlow.value = false
            mutedGroupIdsFlow.value = emptySet()
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
            AppCurrencies.normalizeOrDefault(prefs.getString(KEY_CURRENCY, AppCurrencies.DEFAULT))

        private fun readThemeMode(): ThemeMode =
            ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, ThemeMode.DEFAULT.name))

        private fun readBiometricLock(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

        private fun readAuthTimeout(): AuthTimeout =
            AuthTimeout.fromStorage(prefs.getString(KEY_AUTH_TIMEOUT, AuthTimeout.DEFAULT.name))

        private fun readAppLocale(): AppLocale =
            AppLocale.fromStorage(prefs.getString(KEY_APP_LOCALE, AppLocale.DEFAULT.name))

        private fun readPendingInviteToken(): String? =
            prefs.getString(KEY_PENDING_INVITE_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

        private fun readPendingInviteOpenTarget(): String? =
            prefs.getString(KEY_PENDING_INVITE_OPEN_TARGET, null)?.trim()?.takeIf { it.isNotEmpty() }

        private fun readPendingNotificationGroupId(): String? =
            prefs.getString(KEY_PENDING_NOTIFICATION_GROUP_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

        private fun readSimplifyMap(): Map<String, Boolean> =
            prefs.all
                .mapNotNull { (key, value) ->
                    if (!key.startsWith(KEY_SIMPLIFY_PREFIX) || value !is Boolean) return@mapNotNull null
                    key.removePrefix(KEY_SIMPLIFY_PREFIX) to value
                }.toMap()

        private fun readMuteAll(): Boolean = prefs.getBoolean(KEY_MUTE_ALL, false)

        private fun readMutedGroupIds(): Set<String> =
            prefs.getStringSet(KEY_MUTED_GROUP_IDS, emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()

        companion object {
            private const val PREFS_NAME = "splitease_settings"
            private const val KEY_CURRENCY = "currency_code"
            private const val KEY_THEME_MODE = "theme_mode"
            private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
            private const val KEY_AUTH_TIMEOUT = "auth_timeout"
            private const val KEY_APP_LOCALE = "app_locale"
            private const val KEY_PENDING_INVITE_TOKEN = "pending_invite_token"
            private const val KEY_PENDING_INVITE_OPEN_TARGET = "pending_invite_open_target"
            private const val KEY_PENDING_NOTIFICATION_GROUP_ID = "pending_notification_group_id"
            private const val KEY_MUTE_ALL = "notifications_mute_all"
            private const val KEY_MUTED_GROUP_IDS = "notifications_muted_group_ids"
            private const val KEY_NOTIFICATION_PREFS_UPDATED_AT = "notifications_prefs_updated_at"
            private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notifications_permission_prompted"
            private const val KEY_INSTALL_REFERRER_CHECKED = "install_referrer_checked"
            private const val KEY_SIMPLIFY_PREFIX = "simplify_debts_"
            private const val KEY_ONBOARDING_EMAIL_SENT_PREFIX = "onboarding_email_sent_"
            private const val KEY_PENDING_WELCOME_EMAIL_USER_ID = "pending_welcome_email_user_id"

            private fun simplifyKey(groupId: String) = KEY_SIMPLIFY_PREFIX + groupId

            private fun onboardingEmailSentKey(userId: String) = KEY_ONBOARDING_EMAIL_SENT_PREFIX + userId
        }
    }
