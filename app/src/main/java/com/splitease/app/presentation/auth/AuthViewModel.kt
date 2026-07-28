package com.splitease.app.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
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
 * UI state for email/password auth forms.
 *
 * @property isLoading True while a network auth call is in flight.
 * @property errorMessage User-visible error, if any.
 * @property infoMessage User-visible success/info, if any.
 * @property pendingConfirmationEmail When set, show the verify-email OTP screen
 *   (blocks Home even if a session already exists).
 */
data class AuthFormState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingConfirmationEmail: String? = null,
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
                    if (current is AuthSession.SignedIn &&
                        _formState.value.pendingConfirmationEmail == null
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
            _formState.update { it.copy(pendingConfirmationEmail = null) }
            viewModelScope.launch {
                // Abandoning OTP must not leave a half-created session into the app.
                runCatching { authRepository.signOut() }
            }
        }

        /**
         * Signs in with email and password.
         *
         * @param email Account email.
         * @param password Account password.
         */
        fun signIn(email: String, password: String) {
            submit {
                authRepository.signIn(email, password)
            }
        }

        /**
         * Creates an account and, when required by Supabase settings, opens the OTP gate.
         *
         * @param email Account email.
         * @param password Account password.
         * @param displayName Preferred display name.
         */
        fun signUp(email: String, password: String, displayName: String) {
            viewModelScope.launch {
                _formState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        infoMessage = null,
                        pendingConfirmationEmail = null,
                    )
                }
                val trimmedEmail = email.trim()
                val result = authRepository.signUp(trimmedEmail, password, displayName)
                _formState.update {
                    if (result.isFailure) {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: appContext.getString(R.string.error_generic),
                        )
                    } else {
                        // Always gate on OTP after signup — do not enter the app until verified.
                        AuthFormState(
                            isLoading = false,
                            pendingConfirmationEmail = trimmedEmail,
                            infoMessage = appContext.getString(R.string.verify_email_sent),
                        )
                    }
                }
            }
        }

        /**
         * @param email Pending confirmation email.
         */
        fun resendConfirmation(email: String) {
            submit(successMessage = appContext.getString(R.string.verify_email_resent)) {
                authRepository.resendSignupConfirmation(email)
            }
        }

        /**
         * @param email Pending confirmation email.
         * @param token User-entered OTP.
         */
        fun verifySignupOtp(email: String, token: String) {
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
            viewModelScope.launch {
                _formState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                val result = authRepository.verifySignupOtp(email.trim(), code)
                if (result.isSuccess) {
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            pendingConfirmationEmail = null,
                            errorMessage = null,
                            infoMessage = null,
                        )
                    }
                } else {
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: appContext.getString(R.string.error_generic),
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
            _formState.update { it.copy(pendingConfirmationEmail = null) }
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
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: appContext.getString(R.string.error_generic),
                        )
                    }
                }
            }
        }

        companion object {
            /** Exact digit count for signup email OTP (Supabase mailer OTP length). */
            const val SIGNUP_OTP_LENGTH = 6

            private const val AUTH_LOADING_TIMEOUT_MS = 8_000L
        }
    }
