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

    /**
     * Sends a friend or group invite email with a join link.
     *
     * Best-effort: failures must not block local invite creation; callers may
     * fall back to the system share sheet.
     *
     * @param toEmail Recipient email address.
     * @param inviterName Display name of the person sending the invite.
     * @param groupName Group name when inviting into a group; null for friend invites.
     * @param token Opaque invite token used to build the join URL.
     * @return [Result] success or failure.
     */
    suspend fun sendInviteEmail(
        toEmail: String,
        inviterName: String,
        groupName: String?,
        token: String,
    ): Result<Unit>
}
