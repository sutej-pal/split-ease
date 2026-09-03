package com.splitease.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Post-signup side effects that do not require a dedicated setup UI.
 *
 * Display name is collected on the signup screen; this ViewModel only sends
 * the one-time welcome email after signup OTP verification completes.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val mailRepository: MailRepository,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        /**
         * Sends the welcome email once per user after signup OTP verification.
         *
         * Regular logins do not queue welcome mail and are ignored here.
         *
         * @param userId Signed-in user id.
         * @param email Account email.
         * @param displayName Name from signup metadata.
         */
        fun onSignedInWelcome(userId: String, email: String, displayName: String) {
            viewModelScope.launch {
                if (userId.isBlank() || email.isBlank()) return@launch

                val pendingUserId = appSettingsRepository.getPendingWelcomeEmailUserId()
                if (pendingUserId != userId) return@launch

                if (appSettingsRepository.getOnboardingEmailSent(userId)) {
                    appSettingsRepository.setPendingWelcomeEmailUserId(null)
                    return@launch
                }

                val sendResult =
                    mailRepository.sendOnboardingStartedEmail(
                        toEmail = email,
                        displayName = displayName,
                    )
                if (sendResult.isSuccess) {
                    appSettingsRepository.setOnboardingEmailSent(userId, true)
                    appSettingsRepository.setPendingWelcomeEmailUserId(null)
                }
                // Keep pending on failure so the next signed-in session can retry.
            }
        }
    }
