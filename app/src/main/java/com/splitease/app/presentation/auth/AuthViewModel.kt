package com.splitease.app.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for email/password auth forms.
 *
 * @property isLoading True while a network auth call is in flight.
 * @property errorMessage User-visible error, if any.
 * @property infoMessage User-visible success/info, if any.
 * @property pendingConfirmationEmail When set, show the verify-email screen.
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
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = AuthSession.Loading,
                )

        private val _formState = MutableStateFlow(AuthFormState())

        /** Form loading / error / info for auth screens. */
        val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

        init {
            viewModelScope.launch {
                session.collect { current ->
                    if (current is AuthSession.SignedIn) {
                        authRepository.ensureLocalProfile()
                        _formState.update { it.copy(pendingConfirmationEmail = null) }
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

        /** Leaves the pending-confirmation screen without signing in. */
        fun clearPendingConfirmation() {
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
         * Creates an account. Navigates to verify-email when confirmation is required.
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
                val result = authRepository.signUp(email, password, displayName)
                _formState.update {
                    if (result.isFailure) {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: appContext.getString(R.string.error_generic),
                        )
                    } else {
                        when (val outcome = result.getOrThrow()) {
                            SignUpResult.SignedIn ->
                                AuthFormState(isLoading = false)
                            is SignUpResult.PendingEmailConfirmation ->
                                AuthFormState(
                                    isLoading = false,
                                    pendingConfirmationEmail = outcome.email,
                                    infoMessage =
                                        appContext.getString(R.string.verify_email_sent),
                                )
                        }
                    }
                }
            }
        }

        /**
         * Resends the signup confirmation email for [email].
         *
         * @param email Pending confirmation email.
         */
        fun resendConfirmation(email: String) {
            submit(successMessage = appContext.getString(R.string.verify_email_resent)) {
                authRepository.resendSignupConfirmation(email)
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
    }
