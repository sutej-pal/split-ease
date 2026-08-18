package com.splitease.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * App-wide user preferences (local only).
 *
 * Currency is chosen in Settings and applied to new expenses and groups.
 */
interface AppSettingsRepository {
    companion object {
        /**
         * Sentinel for [getPendingInviteOpenTarget] / [setPendingInviteOpenTarget]:
         * open the Friends tab after invite accept (friend invite, no group).
         */
        const val PENDING_INVITE_OPEN_FRIENDS = "__friends__"
    }

    /**
     * Observes the active ISO 4217 currency code (e.g. `"INR"`).
     *
     * @return Cold [Flow]; always emits at least the default.
     */
    fun observeCurrencyCode(): Flow<String>

    /**
     * Reads the current currency once.
     *
     * @return ISO 4217 code.
     */
    suspend fun getCurrencyCode(): String

    /**
     * Persists the app-wide currency.
     *
     * @param code ISO 4217 code (normalized to uppercase).
     */
    suspend fun setCurrencyCode(code: String)

    /**
     * Observes whether debt simplification is enabled for [groupId].
     * Defaults to `true` when unset.
     *
     * @param groupId Local group id.
     * @return Cold [Flow] of the preference.
     */
    fun observeSimplifyGroupDebts(groupId: String): Flow<Boolean>

    /**
     * Observes all per-group simplify-debt preferences (missing keys ⇒ enabled).
     *
     * @return Cold [Flow] of groupId → enabled.
     */
    fun observeSimplifyGroupDebtsMap(): Flow<Map<String, Boolean>>

    /**
     * Reads whether debt simplification is enabled for [groupId].
     *
     * @param groupId Local group id.
     * @return `true` when simplification is on (default).
     */
    suspend fun getSimplifyGroupDebts(groupId: String): Boolean

    /**
     * Persists the per-group debt-simplification preference.
     *
     * @param groupId Local group id.
     * @param enabled When true, balances use minimized who-owes-whom transfers.
     */
    suspend fun setSimplifyGroupDebts(groupId: String, enabled: Boolean)

    /**
     * Observes the appearance preference.
     *
     * @return Cold [Flow]; defaults to [ThemeMode.SYSTEM].
     */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Reads the appearance preference once.
     *
     * @return Current [ThemeMode].
     */
    suspend fun getThemeMode(): ThemeMode

    /**
     * Persists the appearance preference.
     *
     * @param mode Light, dark, or follow system.
     */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Observes whether opening the app requires biometrics / device credential.
     *
     * @return Cold [Flow]; defaults to `false`.
     */
    fun observeBiometricLockEnabled(): Flow<Boolean>

    /**
     * Reads the biometric app-lock preference once.
     *
     * @return `true` when lock is enabled.
     */
    suspend fun getBiometricLockEnabled(): Boolean

    /**
     * Persists the biometric app-lock preference.
     *
     * @param enabled When true, require auth after the configured timeout.
     */
    suspend fun setBiometricLockEnabled(enabled: Boolean)

    /**
     * Observes the idle timeout before re-auth is required.
     *
     * @return Cold [Flow]; defaults to [AuthTimeout.DEFAULT].
     */
    fun observeAuthTimeout(): Flow<AuthTimeout>

    /**
     * Reads the auth timeout once.
     *
     * @return Current [AuthTimeout].
     */
    suspend fun getAuthTimeout(): AuthTimeout

    /**
     * Persists the auth timeout.
     *
     * @param timeout Grace period before re-auth when returning to the app.
     */
    suspend fun setAuthTimeout(timeout: AuthTimeout)

    /**
     * Reads whether onboarding-start email has already been sent for a user.
     *
     * @param userId Signed-in user id.
     * @return `true` when onboarding-start email was already sent.
     */
    suspend fun getOnboardingEmailSent(userId: String): Boolean

    /**
     * Persists onboarding-start email sent state for a user.
     *
     * @param userId Signed-in user id.
     * @param sent `true` after the welcome email send succeeds.
     */
    suspend fun setOnboardingEmailSent(userId: String, sent: Boolean)

    /**
     * Reads the user id queued for a post-signup welcome email.
     *
     * Set after signup OTP verification; cleared only after the welcome email send succeeds
     * (or when already marked sent). Survives sign-out so a failed send can retry on next login.
     *
     * @return User id awaiting welcome mail, or null.
     */
    suspend fun getPendingWelcomeEmailUserId(): String?

    /**
     * Queues or clears the post-signup welcome email for [userId].
     *
     * @param userId Signed-in user id, or null to clear.
     */
    suspend fun setPendingWelcomeEmailUserId(userId: String?)

    /**
     * Observes a pending invite token from a deep link (awaiting signup / OTP / accept).
     *
     * @return Cold [Flow]; emits `null` when none is stored.
     */
    fun observePendingInviteToken(): Flow<String?>

    /**
     * Reads the pending invite token once.
     *
     * @return Token string, or null.
     */
    suspend fun getPendingInviteToken(): String?

    /**
     * Persists or clears the pending invite token.
     *
     * @param token Opaque invite token, or null to clear.
     */
    suspend fun setPendingInviteToken(token: String?)

    /**
     * Observes where to navigate after an invite is accepted.
     *
     * Values:
     * - a group id → open that group
     * - [PENDING_INVITE_OPEN_FRIENDS] → Friends tab
     * - `null` → no pending invite navigation
     *
     * Survives token clear after accept until the UI consumes it.
     */
    fun observePendingInviteOpenTarget(): Flow<String?>

    /**
     * Reads the pending post-invite navigation target once.
     *
     * @return Group id, [PENDING_INVITE_OPEN_FRIENDS], or null.
     */
    suspend fun getPendingInviteOpenTarget(): String?

    /**
     * Persists or clears where to navigate after invite accept.
     *
     * @param target Group id, [PENDING_INVITE_OPEN_FRIENDS], or null to clear.
     */
    suspend fun setPendingInviteOpenTarget(target: String?)

    /**
     * Observes a group id to open from a push notification tap.
     *
     * @return Cold [Flow]; emits `null` when none is pending.
     */
    fun observePendingNotificationGroupId(): Flow<String?>

    /**
     * Reads the pending notification group id once.
     *
     * @return Group id, or null.
     */
    suspend fun getPendingNotificationGroupId(): String?

    /**
     * Persists or clears a group id opened from a notification.
     *
     * @param groupId Group id, or null to clear.
     */
    suspend fun setPendingNotificationGroupId(groupId: String?)

    /**
     * Whether Play Install Referrer was already read for this install.
     *
     * @return `true` after a successful or empty referrer attempt.
     */
    suspend fun getInstallReferrerChecked(): Boolean

    /**
     * Marks Play Install Referrer as consumed so it is not read again.
     *
     * @param checked `true` after the one-shot referrer bootstrap finishes.
     */
    suspend fun setInstallReferrerChecked(checked: Boolean)

    /**
     * Observes the in-app language preference.
     *
     * @return Cold [Flow]; defaults to [AppLocale.SYSTEM].
     */
    fun observeAppLocale(): Flow<AppLocale>

    /**
     * Reads the language preference once.
     *
     * @return Current [AppLocale].
     */
    suspend fun getAppLocale(): AppLocale

    /**
     * Persists the language preference and applies it process-wide.
     *
     * @param locale System or pinned BCP-47 language.
     */
    suspend fun setAppLocale(locale: AppLocale)

    /**
     * Observes whether all group push notifications are muted.
     *
     * @return Cold [Flow]; defaults to `false`.
     */
    fun observeNotificationsMutedAll(): Flow<Boolean>

    /**
     * Reads the mute-all preference once.
     *
     * @return `true` when every group push is suppressed.
     */
    suspend fun getNotificationsMutedAll(): Boolean

    /**
     * Persists mute-all. Also bumps [getNotificationPrefsUpdatedAtEpochMs].
     *
     * @param muted When true, no group pushes are shown or delivered.
     */
    suspend fun setNotificationsMutedAll(muted: Boolean)

    /**
     * Observes whether pushes are muted for [groupId].
     *
     * @param groupId Local group id.
     * @return Cold [Flow]; defaults to `false`.
     */
    fun observeGroupNotificationsMuted(groupId: String): Flow<Boolean>

    /**
     * Reads whether pushes are muted for [groupId].
     *
     * @param groupId Local group id.
     * @return `true` when that group is muted.
     */
    suspend fun getGroupNotificationsMuted(groupId: String): Boolean

    /**
     * Mutes or unmutes pushes for one group. Also bumps the prefs timestamp.
     *
     * @param groupId Local group id.
     * @param muted When true, this group is muted.
     */
    suspend fun setGroupNotificationsMuted(groupId: String, muted: Boolean)

    /**
     * Reads the set of muted group ids.
     *
     * @return Group ids currently muted.
     */
    suspend fun getMutedGroupIds(): Set<String>

    /**
     * Last local write time for mute prefs (LWW with the cloud row).
     *
     * @return Epoch millis, or 0 if never written.
     */
    suspend fun getNotificationPrefsUpdatedAtEpochMs(): Long

    /**
     * Replaces local mute prefs from a newer cloud row.
     *
     * @param muteAll Cloud mute-all flag.
     * @param mutedGroupIds Cloud muted group ids.
     * @param updatedAtEpochMs Cloud row timestamp.
     */
    suspend fun applyRemoteNotificationPrefs(
        muteAll: Boolean,
        mutedGroupIds: Set<String>,
        updatedAtEpochMs: Long,
    )

    /**
     * Whether the Android 13+ notification permission dialog was already shown.
     *
     * @return `true` after the first prompt (granted or denied).
     */
    suspend fun getNotificationPermissionPrompted(): Boolean

    /**
     * Marks the OS notification permission prompt as consumed for this install.
     *
     * @param prompted `true` after the runtime request is launched.
     */
    suspend fun setNotificationPermissionPrompted(prompted: Boolean)

    /**
     * Clears user-scoped preferences on sign-out.
     *
     * Keeps device-level choices (theme, locale, install-referrer bootstrap), any
     * pending invite deep-link so account-switch can still claim the invite, and
     * [getPendingWelcomeEmailUserId] so a failed welcome send can retry after re-login.
     */
    suspend fun clearSessionData()
}
