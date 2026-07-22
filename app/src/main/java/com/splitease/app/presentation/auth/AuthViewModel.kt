package com.splitease.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
 */
data class AuthFormState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

/**
 * Session-aware auth ViewModel for login, signup, reset, and sign-out.
 *
 * @property authRepository Supabase-backed auth operations.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
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

        /** Clears transient form messages when navigating between auth screens. */
        fun clearMessages() {
            _formState.update { it.copy(errorMessage = null, infoMessage = null) }
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
         * Creates an account and signs in when email confirmation is disabled (MVP).
         *
         * Email confirmation is intentionally skipped for now — keep Confirm email
         * off in the Supabase Dashboard. TODO: re-enable before production.
         *
         * @param email Account email.
         * @param password Account password.
         * @param displayName Preferred display name.
         */
        fun signUp(email: String, password: String, displayName: String) {
            submit {
                authRepository.signUp(email, password, displayName)
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
                        AuthFormState(isLoading = false, infoMessage = successMessage)
                    } else {
                        AuthFormState(
                            isLoading = false,
                            errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: "Something went wrong. Try again.",
                        )
                    }
                }
            }
        }
    }
