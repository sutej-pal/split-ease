package com.splitease.app.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
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
 */
data class AuthFormState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingConfirmationEmail: String? = null,
    val pendingOtpPurpose: PendingOtpPurpose? = null,
    val holdSignedInForOtp: Boolean = false,
)

/**
 * Session-aware auth ViewModel for login, signup, reset, and sign-out.
 *
 * @property authRepository Supabase-backed auth operations.
 * @property appContext Application context for string resources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val appSettingsRepository: AppSettingsRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        /** Live session used to gate navigation. */
        val session: StateFlow<AuthSession> =
            authRepository
                .observeSession()
                .transformLatest { current ->
                    emit(current)
                    if (current is AuthSession.Loading) {
                        // Auth init can hang (stale refresh token / network). Unblock UI.
                        delay(AUTH_LOADING_TIMEOUT_MS)
                        emit(AuthSession.SignedOut)
                    }
                }
                .stateIn(
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

        /**
         * Where to navigate after invite accept (group id or friends sentinel).
         * Survives token clear until [consumePendingInviteOpenTarget].
         */
        val pendingInviteOpenTarget: StateFlow<String?> =
            appSettingsRepository
                .observePendingInviteOpenTarget()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
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
                    if (current is AuthSession.SignedIn &&
                        form.pendingConfirmationEmail == null &&
                        !form.holdSignedInForOtp
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
            viewModelScope.launch {
                val trimmedEmail = email.trim()
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
                    val message =
                        if (isInvalidCredentials(err)) {
                            val registered =
                                authRepository.isEmailRegistered(trimmedEmail).getOrDefault(true)
                            if (!registered) {
                                appContext.getString(R.string.error_not_registered)
                            } else {
                                appContext.getString(R.string.error_invalid_credentials)
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
                // Password auth is enough — hydrate profile and leave the auth gate.
                runCatching { authRepository.ensureLocalProfile() }
                _formState.update { AuthFormState(isLoading = false) }
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
                        errorMessage = appContext.getString(R.string.signup_error_name_required),
                        infoMessage = null,
                    )
                }
                return
            }
            if (password.length < MIN_SIGNUP_PASSWORD_LENGTH) {
                _formState.update {
                    it.copy(
                        errorMessage = appContext.getString(R.string.signup_error_password_short),
                        infoMessage = null,
                    )
                }
                return
            }
            viewModelScope.launch {
                val trimmedEmail = email.trim()
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
                val emailTaken = authRepository.isEmailRegistered(trimmedEmail).getOrDefault(false)
                if (emailTaken) {
                    _formState.update {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                appContext.getString(R.string.error_email_already_registered),
                        )
                    }
                    return@launch
                }
                if (trimmedPhone.isNotEmpty()) {
                    val phoneTaken =
                        authRepository
                            .isPhoneRegistered(dialCode, trimmedPhone)
                            .getOrDefault(false)
                    if (phoneTaken) {
                        _formState.update {
                            AuthFormState(
                                isLoading = false,
                                errorMessage =
                                    appContext.getString(R.string.error_phone_already_registered),
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
                    _formState.update {
                        AuthFormState(
                            isLoading = false,
                            errorMessage = friendlyAuthError(result.exceptionOrNull()),
                        )
                    }
                    return@launch
                }
                when (val outcome = result.getOrNull()) {
                    is SignUpResult.PendingEmailConfirmation ->
                        _formState.update {
                            AuthFormState(
                                isLoading = false,
                                pendingConfirmationEmail = outcome.email,
                                pendingOtpPurpose = PendingOtpPurpose.SIGNUP,
                                holdSignedInForOtp = false,
                                infoMessage = appContext.getString(R.string.verify_email_sent),
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
                                infoMessage = appContext.getString(R.string.verify_email_sent),
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
            val successMessage = appContext.getString(R.string.verify_email_resent)
            submit(successMessage = successMessage) {
                when (purpose) {
                    PendingOtpPurpose.SIGNUP -> authRepository.resendSignupConfirmation(email)
                    PendingOtpPurpose.LOGIN -> authRepository.sendLoginOtp(email)
                }
            }
        }

        /**
         * Verifies the pending signup or login OTP and opens the app on success.
         *
         * @param email Pending confirmation email.
         * @param token User-entered OTP.
         */
        fun verifyPendingOtp(email: String, token: String) {
            val code = token.trim()
            if (code.length != SIGNUP_OTP_LENGTH || code.any { !it.isDigit() }) {
                _formState.update {
                    it.copy(
                        errorMessage = appContext.getString(R.string.verify_email_invalid_code),
                        infoMessage = null,
                    )
                }
                return
            }
            val purpose = _formState.value.pendingOtpPurpose ?: PendingOtpPurpose.SIGNUP
            viewModelScope.launch {
                _formState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                val result =
                    when (purpose) {
                        PendingOtpPurpose.SIGNUP ->
                            authRepository.verifySignupOtp(email.trim(), code)
                        PendingOtpPurpose.LOGIN ->
                            authRepository.verifyLoginOtp(email.trim(), code)
                    }
                if (result.isSuccess) {
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            pendingConfirmationEmail = null,
                            pendingOtpPurpose = null,
                            errorMessage = null,
                            infoMessage = null,
                        )
                    }
                } else {
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = friendlyAuthError(result.exceptionOrNull()),
                        )
                    }
                }
            }
        }

        /**
         * Requests a password-reset email.
         *
         * @param email Account email.
         * @param successMessage Message shown after the request succeeds.
         */
        fun sendPasswordReset(email: String, successMessage: String) {
            submit(successMessage = successMessage) {
                authRepository.sendPasswordReset(email)
            }
        }

        /** Signs out the current user. */
        fun signOut() {
            _formState.update {
                it.copy(
                    pendingConfirmationEmail = null,
                    pendingOtpPurpose = null,
                    holdSignedInForOtp = false,
                )
            }
            submit {
                authRepository.signOut()
            }
        }

        /**
         * Re-runs profile hydrate / sync so a pending invite token is claimed
         * for an already signed-in user.
         */
        fun ensureInviteAccepted() {
            viewModelScope.launch {
                runCatching { authRepository.ensureLocalProfile() }
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
            val raw = throwable?.localizedMessage.orEmpty()
            val lower = raw.lowercase()
            return when {
                isAlreadyRegistered(lower) ->
                    appContext.getString(R.string.error_already_registered)
                isInvalidCredentials(throwable) ->
                    appContext.getString(R.string.error_invalid_credentials)
                isEmailRateLimited(lower) ->
                    appContext.getString(R.string.error_signup_email_rate_limit)
                isEmailDeliveryFailure(lower) ->
                    appContext.getString(R.string.error_signup_email_delivery)
                raw.isBlank() -> appContext.getString(R.string.error_generic)
                // RestException dumps include Url / Headers / Http Method — never show those.
                "url:" in lower || "headers:" in lower || "http method" in lower ->
                    // Prefer specific auth/email clues buried in RestException text.
                    when {
                        isAlreadyRegistered(lower) ->
                            appContext.getString(R.string.error_already_registered)
                        isEmailRateLimited(lower) ->
                            appContext.getString(R.string.error_signup_email_rate_limit)
                        isEmailDeliveryFailure(lower) ->
                            appContext.getString(R.string.error_signup_email_delivery)
                        else -> appContext.getString(R.string.error_generic)
                    }
                else ->
                    raw.lineSequence().firstOrNull()?.take(160)?.trim().orEmpty()
                        .ifBlank { appContext.getString(R.string.error_generic) }
            }
        }

        private fun isAlreadyRegistered(lower: String): Boolean =
            "user already registered" in lower ||
                "already been registered" in lower ||
                "email_exists" in lower ||
                "user_already_exists" in lower ||
                ("already exists" in lower && ("user" in lower || "email" in lower))

        private fun isEmailRateLimited(lower: String): Boolean =
            "over_email_send_rate_limit" in lower ||
                "email rate limit" in lower ||
                "rate limit exceeded" in lower

        private fun isEmailDeliveryFailure(lower: String): Boolean =
            "error sending confirmation email" in lower ||
                "error sending magic link email" in lower ||
                "unexpected status code returned from hook" in lower ||
                ("hook" in lower && "email" in lower) ||
                "send email hook" in lower ||
                "resend send failed" in lower ||
                "you can only send testing emails" in lower

        private fun isInvalidCredentials(throwable: Throwable?): Boolean {
            val lower = throwable?.localizedMessage.orEmpty().lowercase()
            return "invalid_credentials" in lower || "invalid login credentials" in lower
        }

        companion object {
            /** Exact digit count for signup email OTP (Supabase mailer OTP length). */
            const val SIGNUP_OTP_LENGTH = 6

            /** Minimum password length shown on the signup form. */
            const val MIN_SIGNUP_PASSWORD_LENGTH = 8

            private const val AUTH_LOADING_TIMEOUT_MS = 8_000L
        }
    }
