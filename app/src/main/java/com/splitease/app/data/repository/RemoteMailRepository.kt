package com.splitease.app.data.repository

import com.splitease.app.data.remote.MailRemoteDataSource
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.repository.MailRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote mail-service backed [MailRepository].
 */
@Singleton
class RemoteMailRepository
    @Inject
    constructor(
        private val mailRemoteDataSource: MailRemoteDataSource,
    ) : MailRepository {
        override suspend fun sendOnboardingStartedEmail(
            toEmail: String,
            displayName: String,
        ): Result<Unit> =
            runCatching {
                val safeName = displayName.trim().ifBlank { "there" }
                val subject = "Welcome to SplitEase"
                val text =
                    "Hi $safeName,\n\n" +
                        "Welcome to SplitEase. Your onboarding has started.\n\n" +
                        "Finish setup in the app to start tracking shared expenses.\n"
                mailRemoteDataSource.sendMail(
                    to = toEmail.trim(),
                    subject = subject,
                    text = text,
                    fromName = "SplitEase",
                )
            }

        override suspend fun sendInviteEmail(
            toEmail: String,
            inviterName: String,
            groupName: String?,
            token: String,
        ): Result<Unit> =
            runCatching {
                val safeInviter = inviterName.trim().ifBlank { "A friend" }
                val safeGroup = groupName?.trim()?.takeIf { it.isNotEmpty() }
                val subject =
                    if (safeGroup != null) {
                        "You're invited to join \"$safeGroup\" on SplitEase"
                    } else {
                        "You're invited to SplitEase"
                    }
                val text =
                    if (safeGroup != null) {
                        InviteLinks.groupShareText(safeInviter, safeGroup, token)
                    } else {
                        InviteLinks.friendShareText(safeInviter, token)
                    }
                val html =
                    if (safeGroup != null) {
                        InviteLinks.groupShareHtml(safeInviter, safeGroup, token)
                    } else {
                        InviteLinks.friendShareHtml(safeInviter, token)
                    }
                mailRemoteDataSource.sendMail(
                    to = toEmail.trim(),
                    subject = subject,
                    text = text,
                    html = html,
                    fromName = "SplitEase",
                )
            }
    }
