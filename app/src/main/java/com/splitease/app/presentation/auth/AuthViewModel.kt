package com.splitease.app.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
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

        /** Password kept only in memory until the OTP gate completes (dev signup flow). */
        private var pendingPassword: String? = null

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

        /** Leaves the pending-confirmation screen without completing OTP. */
        fun clearPendingConfirmation() {
            pendingPassword = null
            _formState.update { it.copy(pendingConfirmationEmail = null) }
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
         * Creates an account, then shows the OTP gate (dev: enter [DEV_DEFAULT_OTP]).
         *
         * TODO(auth-email-otp): Restore real emailed OTP verification and remove the
         * hardcoded [DEV_DEFAULT_OTP] bypass once custom SMTP email delivery works.
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
                pendingPassword = password
                val trimmedEmail = email.trim()
                val result = authRepository.signUp(trimmedEmail, password, displayName)
                _formState.update {
                    if (result.isFailure) {
                        pendingPassword = null
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: appContext.getString(R.string.error_generic),
                        )
                    } else {
                        // Always gate on OTP after signup (dev bypass), whether or not
                        // Supabase already established a session (autoconfirm).
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
         * Dev stub: does not send email. Reminds the user of [DEV_DEFAULT_OTP].
         *
         * TODO(auth-email-otp): Call [AuthRepository.resendSignupConfirmation] again.
         *
         * @param email Pending confirmation email.
         */
        @Suppress("UNUSED_PARAMETER")
        fun resendConfirmation(email: String) {
            _formState.update {
                it.copy(
                    errorMessage = null,
                    infoMessage = appContext.getString(R.string.verify_email_resent),
                )
            }
        }

        /**
         * Verifies signup OTP. Dev: accepts [DEV_DEFAULT_OTP] only.
         *
         * TODO(auth-email-otp): Call [AuthRepository.verifySignupOtp] with the emailed code.
         *
         * @param email Pending confirmation email.
         * @param token User-entered OTP.
         */
        fun verifySignupOtp(email: String, token: String) {
            val code = token.trim()
            if (code != DEV_DEFAULT_OTP) {
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
                val password = pendingPassword
                val signedIn = session.value is AuthSession.SignedIn
                val result =
                    if (signedIn) {
                        Result.success(Unit)
                    } else if (password != null) {
                        authRepository.signIn(email.trim(), password)
                    } else {
                        Result.failure(
                            IllegalStateException(appContext.getString(R.string.error_generic)),
                        )
                    }
                if (result.isSuccess) {
                    pendingPassword = null
                    _formState.update {
                        it.copy(
                            isLoading = false,
                            pendingConfirmationEmail = null,
                            errorMessage = null,
                            infoMessage = null,
                        )
                    }
                    authRepository.ensureLocalProfile()
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
            pendingPassword = null
            _formState.update { it.copy(pendingConfirmationEmail = null) }
            submit {
                authRepository.signOut()
            }
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
            /**
             * Temporary hardcoded OTP while email delivery is deferred.
             *
             * TODO(auth-email-otp): Remove once real emailed OTP is wired.
             */
            const val DEV_DEFAULT_OTP = "1234"

            private const val AUTH_LOADING_TIMEOUT_MS = 8_000L
        }
    }
