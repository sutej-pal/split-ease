package com.splitease.app.presentation.account

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val GMAIL_PACKAGE = "com.google.android.gm"

/**
 * Opens Gmail with [email] and [subject] filled in.
 *
 * Falls back to any mail app that handles `mailto:` when Gmail is not installed.
 *
 * @return `true` when an activity was started.
 */
internal fun openSupportGmail(
    context: Context,
    email: String,
    subject: String,
): Boolean {
    val uri = Uri.parse("mailto:$email?subject=${Uri.encode(subject)}")
    val gmail =
        Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            setPackage(GMAIL_PACKAGE)
        }
    if (runCatching { context.startActivity(gmail) }.isSuccess) return true
    val fallback =
        Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
    return runCatching { context.startActivity(fallback) }.isSuccess
}
