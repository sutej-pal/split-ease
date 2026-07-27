package com.splitease.app.domain.repository

/**
 * Outbound transactional mail operations.
 */
interface MailRepository {
    /**
     * Sends the onboarding-start email to a user.
     *
     * Implementations should be best-effort and return a failure [Result]
     * when network/provider errors happen.
     *
     * @param toEmail Recipient email address.
     * @param displayName Recipient display name.
     * @return [Result] success or failure.
     */
    suspend fun sendOnboardingStartedEmail(
        toEmail: String,
        displayName: String,
    ): Result<Unit>
}
