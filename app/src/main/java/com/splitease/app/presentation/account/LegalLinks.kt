package com.splitease.app.presentation.account

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.splitease.app.R

/**
 * Opens the public privacy policy in the device browser.
 */
fun Context.openPrivacyPolicy() {
    val intent = Intent(Intent.ACTION_VIEW, getString(R.string.signup_privacy_url).toUri())
    runCatching { startActivity(intent) }
}
