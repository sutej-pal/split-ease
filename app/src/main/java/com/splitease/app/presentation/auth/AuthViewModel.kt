package com.splitease.app.presentation.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.presentation.friends.PendingFriendReviewStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Which email OTP flow is currently gated on the verify screen.
 */
enum class PendingOtpPurpose {
    /** Post-signup confirmation (`OtpType.Email.SIGNUP`). */
    SIGNUP,

    /**
     * Post-signup step-up when Supabase returns a session before email confirm
     * (`OtpType.Email.EMAIL`). Not used for password login.
     */
    LOGIN,

    /** Forgot-password recovery (`OtpType.Email.RECOVERY`). */
    RECOVERY,
}

/**
 * UI state for email/password auth forms.
 *
 * @property isLoading True while a network auth call is in flight.
 * @property errorMessage User-visible error, if any.
 * @property infoMessage User-visible success/info, if any.
 * @property pendingConfirmationEmail When set, show the verify-email OTP screen
 *   (blocks Home even if a session already exists).
 * @property pendingOtpPurpose Which verify/resend API to use while gated.
 * @property holdSignedInForOtp When true, a transient SignedIn session must not open Home
 *   (set before password auth completes so the OTP gate cannot race).
 * @property recoveryOtpVerified True after recovery OTP succeeded but before password update
 *   finishes (allows retrying password-only if update fails).
 */
data class AuthFormState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingConfirmationEmail: String? = null,
    val pendingOtpPurpose: PendingOtpPurpose? = null,
    val holdSignedInForOtp: Boolean = false,
    val recoveryOtpVerified: Boolean = false,
)

/**
 * Session-aware auth ViewModel for login, signup, reset, and sign-out.
 *
 * @property authRepository Supabase-backed auth operations.
 * @property appContext Application context for resolving auth string resources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val pendingFriendReviewStore: PendingFriendReviewStore,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val authRateLimiter = AuthRateLimiter()

        private fun msg(@StringRes id: Int): String = appContext.getString(id)

        /** Live session used to gate navigation. */
        val session: StateFlow<AuthSession> =
            authRepository
                .observeSession()
                .transformLatest { current ->
                    emit(current)
                    if (current is AuthSession.Loading) {
                        // Auth init can hang (stale refresh token / network). Unblock UI.
                        delay(AUTH_LOADING_TIMEOUT_MS.milliseconds)
                        emit(AuthSession.SignedOut)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = AuthSession.Loading,
                )

        private val _formState = MutableStateFlow(AuthFormState())

        /** Form loading / error / info for auth screens. */
        val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

        /** Pending invite token from a deep link (null when none). */
        val pendingInviteToken: StateFlow<String?> =
            appSettingsRepository
                .observePendingInviteToken()
                .stateIn(
                    scope = viewModelScope,
                    // Eager so a cold-start deep link is visible before first frame.
                    started = SharingStarted.Eagerly,
                    initialValue = null,
                )

        /** Observes a group id queued from an FCM notification tap. */
        fun observePendingNotificationGroupId() =
            appSettingsRepository.observePendingNotificationGroupId()

        /**
         * Returns and clears the pending notification group id.
         *
         * @return Group id, or null.
         */
        suspend fun consumePendingNotificationGroupId(): String? {
            val id = appSettingsRepository.getPendingNotificationGroupId()?.takeIf { it.isNotBlank() }
            if (id != null) {
                appSettingsRepository.setPendingNotificationGroupId(null)
            }
            return id
        }

        init {
            viewModelScope.launch {
                session.collect { current ->
                    // Hydrate profile only after OTP onboarding is done.
                    val form = _formState.value
                    if ((current is AuthSession.SignedIn) &&
                        (form.pendingConfirmationEmail == null) &&
                        (!form.holdSignedInForOtp)
                    ) {
                        authRepository.ensureLocalProfile()
                    }
                }
            }
        }

        /** Clears transient form messages when navigating between auth screens. */
        fun clearMessages() {
            _formState.update {
                it.copy(errorMessage = null, infoMessage = null)
            }
        }

        /**
         * Parses pasted invite share text / URI / bare token and stores it so the
         * signed-out nav graph opens the invite landing screen.
         *
         * Needed on emulators where Chrome/email do not open `splitease://` links.
         *
         * @param pasted Text from the clipboard or a text field.
         * @return True when a token was recognized and stored.
         */
        fun openInviteFromPastedText(pasted: String): Boolean {
            val token = InviteLinks.tokenFromPastedText(pasted) ?: return false
            viewModelScope.launch {
                appSettingsRepository.setPendingInviteToken(token)
            }
            return true
        }

        /** Leaves the pending-confirmation screen without completing OTP. */
        fun clearPendingConfirmation() {
            _formState.update {
                it.copy(
                    pendingConfirmationEmail = null,
                    pendingOtpPurpose = null,
                    holdSignedInForOtp = false,
                    recoveryOtpVerified = false,
                )
            }
            viewModelScope.launch {
                // Abandoning OTP must not leave a half-created session into the app.
                runCatching { authRepository.signOut() }
            }
        }

        /**
         * Validates email/password and opens the app (no login OTP step).
         *
         * @param email Account email.
         * @param password Account password.
         */
        fun signIn(email: String, password: String) {
            val trimmedEmail = email.trim()
            if (trimmedEmail.isEmpty() || password.isBlank()) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.LOGIN_FIELDS_REQUIRED),
                        infoMessage = null,
                    )
                }
                return
            }
            val lockRemainingMs =
                authRateLimiter.remainingLockMs(AuthRateAction.LOGIN, trimmedEmail)
            if (lockRemainingMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage = rateLimitMessage(AuthRateAction.LOGIN, lockRemainingMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            viewModelScope.launch {
                _formState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        infoMessage = null,
                        pendingConfirmationEmail = null,
                        pendingOtpPurpose = null,
                        holdSignedInForOtp = false,
                    )
                }
                val result = authRepository.signIn(trimmedEmail, password)
                if (result.isFailure) {
                    runCatching { authRepository.signOut() }
                    val err = result.exceptionOrNull()
                    // Count credential / auth failures toward lockout (not transient network noise).
                    if (isInvalidCredentials(err) || countsTowardLoginRateLimit(err)) {
                        authRateLimiter.recordFailure(AuthRateAction.LOGIN, trimmedEmail)
                    }
                    val postFailLockMs =
                        authRateLimiter.remainingLockMs(AuthRateAction.LOGIN, trimmedEmail)
                    val message =
                        if (postFailLockMs != null) {
                            rateLimitMessage(AuthRateAction.LOGIN, postFailLockMs)
                        } else if (isInvalidCredentials(err)) {
                            val registered =
                                authRepository.isEmailRegistered(trimmedEmail).getOrDefault(defaultValue = true)
                            if (!registered) {
                                msg(AuthMessages.NOT_REGISTERED)
                            } else {
                                msg(AuthMessages.INVALID_CREDENTIALS)
                            }
                        } else {
                            friendlyAuthError(err)
                        }
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                            holdSignedInForOtp = false,
                            pendingConfirmationEmail = null,
                            pendingOtpPurpose = null,
                        )
                    }
                    return@launch
                }
                authRateLimiter.recordSuccess(AuthRateAction.LOGIN, trimmedEmail)
                // Open Home immediately — hydrate Room in the background so login is not
                // blocked on friends/groups/expenses/payments pulls.
                _formState.update { AuthFormState(isLoading = false) }
                launch {
                    runCatching { authRepository.ensureLocalProfile() }
                }
            }
        }

        /**
         * Completes Google Sign-In after Credential Manager returns an ID token.
         * Skips email OTP (Google already verified the address).
         *
         * @param idToken Google ID token.
         * @param rawNonce Unhashed nonce paired with the Google request.
         */
        fun signInWithGoogle(
            idToken: String,
            rawNonce: String,
        ) {
            if (idToken.isBlank()) {
                _formState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = msg(AuthMessages.GOOGLE_FAILED),
                        infoMessage = null,
                    )
                }
                return
            }
            viewModelScope.launch {
                _formState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        infoMessage = null,
                        pendingConfirmationEmail = null,
                        pendingOtpPurpose = null,
                        holdSignedInForOtp = false,
                    )
                }
                val result = authRepository.signInWithGoogle(idToken, rawNonce)
                if (result.isFailure) {
                    runCatching { authRepository.signOut() }
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyAuthError(result.exceptionOrNull()),
                            holdSignedInForOtp = false,
                            pendingConfirmationEmail = null,
                            pendingOtpPurpose = null,
                        )
                    }
                    return@launch
                }
                if (result.getOrNull()?.isNewUser == true) {
                    authRepository.getSignedInUserOrNull()?.userId?.takeIf { it.isNotBlank() }?.let { userId ->
                        appSettingsRepository.setPendingWelcomeEmailUserId(userId)
                    }
                }
                _formState.update { AuthFormState(isLoading = false) }
                launch {
                    runCatching { authRepository.ensureLocalProfile() }
                }
            }
        }

        /** Clears loading after the Google account picker is dismissed. */
        fun onGoogleSignInCancelled() {
            _formState.update { it.copy(isLoading = false) }
        }

        /**
         * Surfaces a Google picker / configuration failure on the auth form.
         *
         * @param outcome Non-success picker outcome.
         */
        fun onGoogleSignInFailed(outcome: GoogleIdTokenOutcome) {
            val message =
                when (outcome) {
                    GoogleIdTokenOutcome.NotConfigured -> msg(AuthMessages.GOOGLE_NOT_CONFIGURED)
                    GoogleIdTokenOutcome.NoAccount -> msg(AuthMessages.GOOGLE_NO_ACCOUNT)
                    GoogleIdTokenOutcome.Failed,
                    GoogleIdTokenOutcome.Cancelled,
                    is GoogleIdTokenOutcome.Success,
                    -> msg(AuthMessages.GOOGLE_FAILED)
                }
            _formState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = message,
                    infoMessage = null,
                )
            }
        }

        /** Greys the auth form while the Google account picker is visible. */
        fun onGoogleSignInStarted() {
            _formState.update {
                it.copy(isLoading = true, errorMessage = null, infoMessage = null)
            }
        }

        /**
         * Creates an account and, when required by Supabase settings, opens the OTP gate.
         *
         * @param email Account email.
         * @param password Account password (minimum [MIN_SIGNUP_PASSWORD_LENGTH] characters).
         * @param displayName Preferred display name.
         * @param phoneCountryCode Dialing code (e.g. `+91`).
         * @param phoneNumber National phone number digits.
         * @param currencyCode Preferred ISO 4217 currency.
         * @param photoUri Optional local avatar URI string.
         */
        fun signUp(
            email: String,
            password: String,
            displayName: String,
            phoneCountryCode: String = "+91",
            phoneNumber: String = "",
            currencyCode: String = AppCurrencies.DEFAULT,
            photoUri: String? = null,
        ) {
            val trimmedName = displayName.trim()
            if (trimmedName.isBlank()) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.NAME_REQUIRED),
                        infoMessage = null,
                    )
                }
                return
            }
            if (password.length < MIN_SIGNUP_PASSWORD_LENGTH) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.PASSWORD_SHORT),
                        infoMessage = null,
                    )
                }
                return
            }
            val trimmedEmail = email.trim()
            val signupLockMs =
                authRateLimiter.remainingLockMs(AuthRateAction.SIGNUP, trimmedEmail)
            if (signupLockMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage = rateLimitMessage(AuthRateAction.SIGNUP, signupLockMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            viewModelScope.launch {
                val trimmedPhone = phoneNumber.trim()
                val dialCode = phoneCountryCode.trim().ifBlank { "+91" }
                // Hold Home closed if signup returns a session before OTP.
                _formState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        infoMessage = null,
                        pendingConfirmationEmail = null,
                        pendingOtpPurpose = null,
                        holdSignedInForOtp = true,
                    )
                }
                val emailTaken =
                    authRepository.isEmailRegistered(trimmedEmail).getOrDefault(defaultValue = false)
                if (emailTaken) {
                    authRateLimiter.recordFailure(AuthRateAction.SIGNUP, trimmedEmail)
                    val lockMs =
                        authRateLimiter.remainingLockMs(AuthRateAction.SIGNUP, trimmedEmail)
                    _formState.update {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                lockMs?.let { remaining -> rateLimitMessage(AuthRateAction.SIGNUP, remaining) }
                                    ?: msg(AuthMessages.EMAIL_ALREADY_REGISTERED),
                        )
                    }
                    return@launch
                }
                if (trimmedPhone.isNotEmpty()) {
                    val phoneTaken =
                        authRepository
                            .isPhoneRegistered(dialCode, trimmedPhone)
                            .getOrDefault(defaultValue = false)
                    if (phoneTaken) {
                        _formState.update {
                            AuthFormState(
                                isLoading = false,
                                errorMessage =
                                    msg(AuthMessages.PHONE_ALREADY_REGISTERED),
                            )
                        }
                        return@launch
                    }
                }
                appSettingsRepository.setCurrencyCode(currencyCode)
                val result =
                    authRepository.signUp(
                        email = trimmedEmail,
                        password = password,
                        displayName = trimmedName,
                        phoneCountryCode = dialCode,
                        phoneNumber = trimmedPhone,
                        currencyCode = currencyCode,
                        photoUri = photoUri,
                    )
                if (result.isFailure) {
                    runCatching { authRepository.signOut() }
                    authRateLimiter.recordFailure(AuthRateAction.SIGNUP, trimmedEmail)
                    val lockMs =
                        authRateLimiter.remainingLockMs(AuthRateAction.SIGNUP, trimmedEmail)
                    _formState.update {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                lockMs?.let { remaining ->
                                    rateLimitMessage(AuthRateAction.SIGNUP, remaining)
                                } ?: friendlyAuthError(result.exceptionOrNull()),
                        )
                    }
                    return@launch
                }
                authRateLimiter.recordSuccess(AuthRateAction.SIGNUP, trimmedEmail)
                when (val outcome = result.getOrNull()) {
                    is SignUpResult.PendingEmailConfirmation ->
                        _formState.update {
                            AuthFormState(
                                isLoading = false,
                                pendingConfirmationEmail = outcome.email,
                                pendingOtpPurpose = PendingOtpPurpose.SIGNUP,
                                holdSignedInForOtp = false,
                                infoMessage = msg(AuthMessages.VERIFY_EMAIL_SENT),
                            )
                        }
                    is SignUpResult.SignedIn, null -> {
                        // Autoconfirm / session-before-OTP: still require email OTP and
                        // do not open the app until verify succeeds.
                        // Arm OTP gate BEFORE signOut — otherwise SignedOut briefly rebuilds Welcome.
                        _formState.update {
                            AuthFormState(
                                isLoading = true,
                                pendingConfirmationEmail = trimmedEmail,
                                pendingOtpPurpose = PendingOtpPurpose.LOGIN,
                                holdSignedInForOtp = false,
                            )
                        }
                        runCatching { authRepository.signOut() }
                        val otpResult = authRepository.sendLoginOtp(trimmedEmail)
                        if (otpResult.isFailure) {
                            authRateLimiter.recordFailure(AuthRateAction.SIGNUP, trimmedEmail)
                            _formState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = friendlyAuthError(otpResult.exceptionOrNull()),
                                )
                            }
                            return@launch
                        }
                        _formState.update {
                            it.copy(
                                isLoading = false,
                                infoMessage = msg(AuthMessages.VERIFY_EMAIL_SENT),
                            )
                        }
                    }
                }
            }
        }

        /**
         * @param email Pending confirmation email.
         */
        fun resendConfirmation(email: String) {
            val purpose = _formState.value.pendingOtpPurpose ?: PendingOtpPurpose.SIGNUP
            val trimmedEmail = email.trim()
            val rateAction =
                when (purpose) {
                    PendingOtpPurpose.RECOVERY -> AuthRateAction.FORGOT_PASSWORD
                    else -> AuthRateAction.SIGNUP
                }
            val lockMs = authRateLimiter.remainingLockMs(rateAction, trimmedEmail)
            if (lockMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage = rateLimitMessage(rateAction, lockMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            val successMessage =
                when (purpose) {
                    PendingOtpPurpose.RECOVERY ->
                        AuthMessages.resetOtpSent(appContext, trimmedEmail)
                    else -> msg(AuthMessages.VERIFY_EMAIL_RESENT)
                }
            submit(successMessage = successMessage) {
                // Count every resend toward the throttle (email spam protection).
                authRateLimiter.recordFailure(rateAction, trimmedEmail)
                when (purpose) {
                    PendingOtpPurpose.SIGNUP -> authRepository.resendSignupConfirmation(email)
                    PendingOtpPurpose.LOGIN -> authRepository.sendLoginOtp(email)
                    PendingOtpPurpose.RECOVERY -> {
                        _formState.update { it.copy(recoveryOtpVerified = false) }
                        authRepository.requestPasswordReset(email)
                    }
                }
            }
        }

        /**
         * Verifies the pending signup or login OTP and opens the app on success.
         * Recovery OTP is handled by [completePasswordReset].
         *
         * @param email Pending confirmation email.
         * @param token User-entered OTP.
         */
        fun verifyPendingOtp(email: String, token: String) {
            val purpose = _formState.value.pendingOtpPurpose ?: PendingOtpPurpose.SIGNUP
            // Recovery uses [completePasswordReset] on ResetPasswordOtpScreen.
            if (purpose == PendingOtpPurpose.RECOVERY) return
            val trimmedEmail = email.trim()
            val code = token.trim()
            if (code.length != SIGNUP_OTP_LENGTH || code.any { !it.isDigit() }) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.VERIFY_EMAIL_INVALID_CODE),
                        infoMessage = null,
                    )
                }
                return
            }
            val lockMs =
                authRateLimiter.remainingLockMs(AuthRateAction.SIGNUP, trimmedEmail)
            if (lockMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage = rateLimitMessage(AuthRateAction.SIGNUP, lockMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            viewModelScope.launch {
                _formState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                val result =
                    when (purpose) {
                        PendingOtpPurpose.SIGNUP ->
                            authRepository.verifySignupOtp(trimmedEmail, code)
                        PendingOtpPurpose.LOGIN ->
                            authRepository.verifyLoginOtp(trimmedEmail, code)
                        PendingOtpPurpose.RECOVERY -> return@launch
                    }
                if (result.isSuccess) {
                    authRateLimiter.recordSuccess(AuthRateAction.SIGNUP, trimmedEmail)
                    if (purpose == PendingOtpPurpose.SIGNUP) {
                        authRepository.getSignedInUserOrNull()?.userId?.takeIf { it.isNotBlank() }?.let { userId ->
                            appSettingsRepository.setPendingWelcomeEmailUserId(userId)
                        }
                    }
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            pendingConfirmationEmail = null,
                            pendingOtpPurpose = null,
                            recoveryOtpVerified = false,
                            errorMessage = null,
                            infoMessage = null,
                        )
                    }
                } else {
                    authRateLimiter.recordFailure(AuthRateAction.SIGNUP, trimmedEmail)
                    val postFailLockMs =
                        authRateLimiter.remainingLockMs(AuthRateAction.SIGNUP, trimmedEmail)
                    _formState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage =
                                postFailLockMs?.let { remaining ->
                                    rateLimitMessage(AuthRateAction.SIGNUP, remaining)
                                } ?: friendlyAuthError(result.exceptionOrNull()),
                        )
                    }
                }
            }
        }

        /**
         * Requests a password-reset OTP and always opens the set-new-password gate.
         *
         * Does not reveal whether [email] is registered — [AuthRepository.requestPasswordReset]
         * always resolves success; missing accounts simply never receive a code.
         *
         * @param email Account email.
         */
        fun requestPasswordReset(email: String) {
            val trimmedEmail = email.trim()
            if (trimmedEmail.isEmpty()) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.INVALID_EMAIL),
                        infoMessage = null,
                    )
                }
                return
            }
            val lockMs =
                authRateLimiter.remainingLockMs(AuthRateAction.FORGOT_PASSWORD, trimmedEmail)
            if (lockMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage = rateLimitMessage(AuthRateAction.FORGOT_PASSWORD, lockMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            viewModelScope.launch {
                _formState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                // Count every request (success or fail) — abuse vector is email spam.
                authRateLimiter.recordFailure(AuthRateAction.FORGOT_PASSWORD, trimmedEmail)
                val result = authRepository.requestPasswordReset(trimmedEmail)
                val sendError = result.exceptionOrNull()
                val rateLimited =
                    sendError != null && isEmailRateLimited(collectAuthErrorText(sendError).lowercase())
                val deliveryFailed =
                    sendError != null && isEmailDeliveryFailure(collectAuthErrorText(sendError).lowercase())
                val clientLockMs =
                    authRateLimiter.remainingLockMs(AuthRateAction.FORGOT_PASSWORD, trimmedEmail)
                // Always open the OTP gate (privacy: unknown emails also look the same).
                // Rate-limit / hook failures are surfaced so the user knows why no code arrived.
                _formState.update {
                    AuthFormState(
                        isLoading = false,
                        pendingConfirmationEmail = trimmedEmail,
                        pendingOtpPurpose = PendingOtpPurpose.RECOVERY,
                        recoveryOtpVerified = false,
                        infoMessage =
                            if (rateLimited || deliveryFailed || clientLockMs != null) {
                                null
                            } else {
                                AuthMessages.resetOtpSent(appContext, trimmedEmail)
                            },
                        errorMessage =
                            when {
                                clientLockMs != null ->
                                    rateLimitMessage(AuthRateAction.FORGOT_PASSWORD, clientLockMs)
                                rateLimited ->
                                    msg(AuthMessages.EMAIL_RATE_LIMITED)
                                deliveryFailed ->
                                    msg(AuthMessages.EMAIL_DELIVERY_FAILED)
                                else -> null
                            },
                    )
                }
            }
        }

        /**
         * Verifies the recovery OTP (if needed) and sets a new password.
         *
         * @param email Account email that received the code.
         * @param token Six-digit OTP (ignored when [AuthFormState.recoveryOtpVerified] is true).
         * @param newPassword New password.
         * @param confirmPassword Must match [newPassword].
         */
        fun completePasswordReset(
            email: String,
            token: String,
            newPassword: String,
            confirmPassword: String,
        ) {
            if (newPassword.length < MIN_SIGNUP_PASSWORD_LENGTH ||
                !newPassword.any { it.isUpperCase() } ||
                !newPassword.any { it.isLowerCase() } ||
                !newPassword.any { it.isDigit() }
            ) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.RESET_PASSWORD_REQUIREMENTS),
                        infoMessage = null,
                    )
                }
                return
            }
            if (newPassword != confirmPassword) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.RESET_PASSWORD_MISMATCH),
                        infoMessage = null,
                    )
                }
                return
            }
            val alreadyVerified = _formState.value.recoveryOtpVerified
            val code = token.trim()
            if (!alreadyVerified &&
                (code.length != SIGNUP_OTP_LENGTH || code.any { !it.isDigit() })
            ) {
                _formState.update {
                    it.copy(
                        errorMessage = msg(AuthMessages.VERIFY_EMAIL_INVALID_CODE),
                        infoMessage = null,
                    )
                }
                return
            }
            val trimmedEmail = email.trim()
            val resetLockMs =
                authRateLimiter.remainingLockMs(AuthRateAction.RESET_PASSWORD, trimmedEmail)
            if (resetLockMs != null) {
                _formState.update {
                    it.copy(
                        errorMessage =
                            rateLimitMessage(AuthRateAction.RESET_PASSWORD, resetLockMs),
                        infoMessage = null,
                        isLoading = false,
                    )
                }
                return
            }
            viewModelScope.launch {
                _formState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        infoMessage = null,
                        holdSignedInForOtp = true,
                    )
                }
                if (!alreadyVerified) {
                    val otpResult = authRepository.verifyRecoveryOtp(trimmedEmail, code)
                    if (otpResult.isFailure) {
                        authRateLimiter.recordFailure(AuthRateAction.RESET_PASSWORD, trimmedEmail)
                        val lockMs =
                            authRateLimiter.remainingLockMs(
                                AuthRateAction.RESET_PASSWORD,
                                trimmedEmail,
                            )
                        // Wrong / missing / expired / never-sent (unregistered) — same generic copy.
                        _formState.update { state ->
                            state.copy(
                                isLoading = false,
                                holdSignedInForOtp = false,
                                errorMessage =
                                    lockMs?.let { remaining ->
                                        rateLimitMessage(AuthRateAction.RESET_PASSWORD, remaining)
                                    } ?: msg(AuthMessages.RESET_OTP_INVALID_OR_EXPIRED),
                            )
                        }
                        return@launch
                    }
                    _formState.update { it.copy(recoveryOtpVerified = true) }
                }
                val passwordResult = authRepository.updatePassword(newPassword)
                if (passwordResult.isSuccess) {
                    authRateLimiter.recordSuccess(AuthRateAction.RESET_PASSWORD, trimmedEmail)
                    authRateLimiter.recordSuccess(AuthRateAction.FORGOT_PASSWORD, trimmedEmail)
                    _formState.update {
                        AuthFormState(
                            isLoading = false,
                            infoMessage = msg(AuthMessages.RESET_PASSWORD_SUCCESS),
                        )
                    }
                } else {
                    val err = passwordResult.exceptionOrNull()
                    val sessionLost = isRecoverySessionMissing(err)
                    if (!isSamePassword(collectAuthErrorText(err).lowercase()) &&
                        !isWeakPassword(collectAuthErrorText(err).lowercase())
                    ) {
                        authRateLimiter.recordFailure(AuthRateAction.RESET_PASSWORD, trimmedEmail)
                    }
                    val lockMs =
                        authRateLimiter.remainingLockMs(
                            AuthRateAction.RESET_PASSWORD,
                            trimmedEmail,
                        )
                    _formState.update { state ->
                        state.copy(
                            isLoading = false,
                            holdSignedInForOtp = false,
                            // Keep OTP verified for password-policy errors; re-prompt when session died.
                            recoveryOtpVerified = state.recoveryOtpVerified && !sessionLost,
                            errorMessage =
                                lockMs?.let { remaining ->
                                    rateLimitMessage(AuthRateAction.RESET_PASSWORD, remaining)
                                } ?: friendlyAuthError(err),
                        )
                    }
                }
            }
        }

        /** Signs out the current user and clears local session data. */
        fun signOut() {
            _formState.update {
                it.copy(
                    pendingConfirmationEmail = null,
                    pendingOtpPurpose = null,
                    holdSignedInForOtp = false,
                    recoveryOtpVerified = false,
                )
            }
            pendingFriendReviewStore.clear()
            submit {
                authRepository.signOut()
            }
        }

        /**
         * Claims any pending invite (sync), then returns and clears the open target
         * for post-accept navigation.
         *
         * @return Group id, [AppSettingsRepository.PENDING_INVITE_OPEN_FRIENDS], or null.
         */
        suspend fun claimInviteAndConsumeOpenTarget(): String? {
            val hadToken = !appSettingsRepository.getPendingInviteToken().isNullOrBlank()
            runCatching { authRepository.ensureLocalProfile() }
            // If a deep-link token is still stored, accept failed — don't pretend we joined.
            if (hadToken && !appSettingsRepository.getPendingInviteToken().isNullOrBlank()) {
                return null
            }
            val target = appSettingsRepository.getPendingInviteOpenTarget()
            if (!target.isNullOrBlank()) {
                appSettingsRepository.setPendingInviteOpenTarget(null)
            }
            return target?.takeIf { it.isNotBlank() }
        }

        private fun submit(
            successMessage: String? = null,
            block: suspend () -> Result<Unit>,
        ) {
            viewModelScope.launch {
                _formState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                val result = block()
                _formState.update {
                    if (result.isSuccess) {
                        it.copy(
                            isLoading = false,
                            infoMessage = successMessage,
                            errorMessage = null,
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyAuthError(result.exceptionOrNull()),
                        )
                    }
                }
            }
        }

        /**
         * Maps Supabase/RestException dumps into short user-facing copy.
         */
        private fun friendlyAuthError(throwable: Throwable?): String {
            if (throwable != null) {
                // Unit tests do not mock android.util.Log; never fail the UI path on logging.
                runCatching { android.util.Log.e(TAG, "Auth error", throwable) }
            }
            val raw = collectAuthErrorText(throwable)
            val lower = raw.lowercase()
            return when {
                isAlreadyRegistered(lower) ->
                    msg(AuthMessages.ALREADY_REGISTERED)
                isGoogleIdentityConflict(lower) ->
                    msg(AuthMessages.EMAIL_ALREADY_REGISTERED)
                isSamePassword(lower) ->
                    msg(AuthMessages.RESET_PASSWORD_SAME_AS_OLD)
                isRecoverySessionMissing(throwable) ->
                    msg(AuthMessages.RESET_PASSWORD_SESSION_EXPIRED)
                isInvalidCredentials(throwable) ->
                    msg(AuthMessages.INVALID_CREDENTIALS)
                isEmailRateLimited(lower) ->
                    msg(AuthMessages.EMAIL_RATE_LIMITED)
                isEmailDeliveryFailure(lower) ->
                    msg(AuthMessages.EMAIL_DELIVERY_FAILED)
                isInvalidEmail(lower) ->
                    msg(AuthMessages.INVALID_EMAIL)
                isWeakPassword(lower) ->
                    msg(AuthMessages.PASSWORD_SHORT)
                raw.isBlank() -> msg(AuthMessages.GENERIC)
                // RestException dumps include Url / Headers / Http Method — never show those.
                "url:" in lower || "headers:" in lower || "http method" in lower ->
                    when {
                        isAlreadyRegistered(lower) ->
                            msg(AuthMessages.ALREADY_REGISTERED)
                        isGoogleIdentityConflict(lower) ->
                            msg(AuthMessages.EMAIL_ALREADY_REGISTERED)
                        isSamePassword(lower) ->
                            msg(AuthMessages.RESET_PASSWORD_SAME_AS_OLD)
                        isRecoverySessionMissing(throwable) ->
                            msg(AuthMessages.RESET_PASSWORD_SESSION_EXPIRED)
                        isEmailRateLimited(lower) ->
                            msg(AuthMessages.EMAIL_RATE_LIMITED)
                        isEmailDeliveryFailure(lower) ->
                            msg(AuthMessages.EMAIL_DELIVERY_FAILED)
                        isInvalidEmail(lower) ->
                            msg(AuthMessages.INVALID_EMAIL)
                        else -> msg(AuthMessages.GENERIC)
                    }
                else ->
                    extractReadableAuthMessage(raw)
                        ?: msg(AuthMessages.GENERIC)
            }
        }

        /** Concatenates message text across the cause chain for pattern matching. */
        private fun collectAuthErrorText(throwable: Throwable?): String {
            if (throwable == null) return ""
            val parts = linkedSetOf<String>()
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < 6) {
                current.message
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { parts += it }
                val localized = current.localizedMessage?.trim().orEmpty()
                if (localized.isNotEmpty()) parts += localized
                current = current.cause
                depth++
            }
            return parts.joinToString("\n")
        }

        /**
         * Pulls a short human message from JSON-ish auth errors when present.
         */
        private fun extractReadableAuthMessage(raw: String): String? {
            val jsonKey =
                Regex("\"(?:error_description|msg|message|error)\"\\s*:\\s*\"([^\"]+)\"")
            val queryKey = Regex("error_description=([^&\\s]+)")
            for (pattern in listOf(jsonKey, queryKey)) {
                val match = pattern
                    .find(raw)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    .orEmpty()
                if (match.isNotBlank() &&
                    "url:" !in match.lowercase() &&
                    "http" !in match.lowercase()
                ) {
                    return match.take(160)
                }
            }
            val firstLine =
                raw
                    .lineSequence()
                    .map { it.trim() }
                    .firstOrNull { line ->
                        line.isNotEmpty() &&
                            "url:" !in line.lowercase() &&
                            "headers:" !in line.lowercase() &&
                            "http method" !in line.lowercase()
                    }
            return firstLine?.take(160)?.takeIf { it.isNotBlank() }
        }

        private fun isAlreadyRegistered(lower: String): Boolean =
            "user already registered" in lower ||
                "already been registered" in lower ||
                "email_exists" in lower ||
                "user_already_exists" in lower ||
                ("already exists" in lower && ("user" in lower || "email" in lower))

        private fun isGoogleIdentityConflict(lower: String): Boolean =
            "identity_already_exists" in lower ||
                "multiple accounts with the same email" in lower ||
                ("provider" in lower && "already" in lower && "linked" in lower)

        private fun isEmailRateLimited(lower: String): Boolean =
            "over_email_send_rate_limit" in lower ||
                "email rate limit" in lower ||
                "rate limit exceeded" in lower ||
                "too many requests" in lower ||
                "429" in lower ||
                Regex("""\bstatus\s*[:=]?\s*429\b""").containsMatchIn(lower)

        private fun isEmailDeliveryFailure(lower: String): Boolean =
            "error sending confirmation email" in lower ||
                "error sending magic link email" in lower ||
                "error sending recovery email" in lower ||
                "unexpected status code returned from hook" in lower ||
                ("hook" in lower && "email" in lower) ||
                "send email hook" in lower ||
                "resend send failed" in lower ||
                "you can only send testing emails" in lower ||
                "email address not authorized" in lower ||
                ("unable to send" in lower && "email" in lower)

        private fun isInvalidEmail(lower: String): Boolean =
            "invalid email" in lower ||
                "email_address_invalid" in lower ||
                "unable to validate email" in lower ||
                "email address is invalid" in lower ||
                ("invalid" in lower && "email" in lower && "format" in lower)

        private fun isWeakPassword(lower: String): Boolean =
            "weak_password" in lower ||
                "password should be at least" in lower ||
                "password is too short" in lower

        private fun isSamePassword(lower: String): Boolean =
            "same_password" in lower ||
                "different from the old password" in lower ||
                ("same" in lower && "password" in lower && "old" in lower)

        private fun isRecoverySessionMissing(throwable: Throwable?): Boolean {
            val lower = collectAuthErrorText(throwable).lowercase()
            return "session expired" in lower ||
                "session is missing" in lower ||
                "auth session missing" in lower ||
                "session_not_found" in lower ||
                "session_expired" in lower ||
                "reauthentication_needed" in lower ||
                "request a new reset code" in lower
        }

        private fun isInvalidCredentials(throwable: Throwable?): Boolean {
            val lower = collectAuthErrorText(throwable).lowercase()
            return "invalid_credentials" in lower || "invalid login credentials" in lower
        }

        /**
         * Failures that should advance the login lockout counter (auth rejections,
         * not generic connectivity).
         */
        private fun countsTowardLoginRateLimit(throwable: Throwable?): Boolean {
            val lower = collectAuthErrorText(throwable).lowercase()
            if (lower.isBlank()) return false
            return isEmailRateLimited(lower) ||
                "invalid login" in lower ||
                "email not confirmed" in lower ||
                "user not found" in lower ||
                "user_not_found" in lower
        }

        private fun rateLimitMessage(
            action: AuthRateAction,
            lockRemainingMs: Long,
        ): String {
            val waitMinutes =
                ((lockRemainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            return AuthMessages.authRateLimited(appContext, action, waitMinutes)
        }

        companion object {
            private const val TAG = "AuthViewModel"

            /** Exact digit count for signup email OTP (Supabase mailer OTP length). */
            const val SIGNUP_OTP_LENGTH = 6

            /** Minimum password length shown on the signup form. */
            const val MIN_SIGNUP_PASSWORD_LENGTH = 8

            private const val AUTH_LOADING_TIMEOUT_MS = 8_000L
        }
    }
