package com.splitease.app.data.repository

import com.splitease.app.data.remote.MailRemoteDataSource
import com.splitease.app.data.social.ContactIdentifier
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.MailRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote SplitEase Server backed [MailRepository].
 *
 * Bodies/subjects are rendered from `server/mail-templates/` via template ids.
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
                mailRemoteDataSource.sendTemplate(
                    to = toEmail.trim(),
                    template = "welcome",
                    vars = mapOf("displayName" to safeName),
                )
            }

        override suspend fun sendInviteEmail(
            toEmail: String,
            inviterName: String,
            groupName: String?,
            token: String,
        ): Result<Unit> {
            val safeInviter = inviterName.trim()
            if (safeInviter.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException(AuthRepository.INVITE_SENDER_PROFILE_MISSING),
                )
            }
            val safeGroup = groupName?.trim()?.takeIf { it.isNotEmpty() }
            return runCatching {
                val link = InviteLinks.urlFor(token)
                if (safeGroup != null) {
                    mailRemoteDataSource.sendTemplate(
                        to = toEmail.trim(),
                        template = "invite-group",
                        vars =
                            mapOf(
                                "inviterName" to safeInviter,
                                "groupName" to safeGroup,
                                "link" to link,
                            ),
                    )
                } else {
                    mailRemoteDataSource.sendTemplate(
                        to = toEmail.trim(),
                        template = "invite-friend",
                        vars =
                            mapOf(
                                "inviterName" to safeInviter,
                                "link" to link,
                            ),
                    )
                }
            }
        }

        override suspend fun sendBalanceReminderEmail(
            toEmails: List<String>,
            subject: String,
            body: String,
            settleUpNote: String,
        ): Result<Unit> =
            runCatching {
                val recipients =
                    toEmails
                        .map { it.trim() }
                        .filter { ContactIdentifier.isRealEmail(it) }
                        .distinctBy { it.lowercase() }
                require(recipients.isNotEmpty()) { "No valid email addresses to remind." }

                val safeBody = body.trim().ifBlank { "Please settle up on SplitEase." }
                val safeNote = settleUpNote.trim()
                // Plain text only — server builds escaped HTML from these vars.
                val vars =
                    mapOf(
                        "body" to safeBody,
                        "note" to safeNote,
                    )
                var lastError: Throwable? = null
                var sent = 0
                recipients.forEach { email ->
                    runCatching {
                        mailRemoteDataSource.sendTemplate(
                            to = email,
                            template = "reminder",
                            vars = vars,
                            subject = subject.trim().ifBlank { null },
                        )
                        sent += 1
                    }.onFailure { lastError = it }
                }
                if (sent == 0) {
                    throw lastError ?: IllegalStateException("Failed to send reminder email.")
                }
            }
    }
