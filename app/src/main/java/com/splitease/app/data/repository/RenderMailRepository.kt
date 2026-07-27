package com.splitease.app.data.repository

import com.splitease.app.data.remote.MailRemoteDataSource
import com.splitease.app.domain.repository.MailRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Render mail-service backed [MailRepository].
 */
@Singleton
class RenderMailRepository
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
    }
