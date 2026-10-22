package com.splitease.app.presentation.account

import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun buildSupportEmailIntent(
    email: String,
    subject: String,
): Intent {
    val uri = Uri.parse("mailto:$email?subject=${Uri.encode(subject)}")
    return Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
}

/**
 * Opens an email app with [email] and [subject] filled in.
 *
 * @return `true` when an activity was started.
 */
internal fun openSupportEmail(
    context: Context,
    email: String,
    subject: String,
): Boolean =
    runCatching {
        context.startActivity(buildSupportEmailIntent(email, subject))
    }.isSuccess
