package com.splitease.app.presentation.auth

import android.content.Context
import androidx.annotation.StringRes
import com.splitease.app.R

/**
 * Auth feedback copy as [R.string] / [R.plurals] ids — single source in `strings.xml`.
 *
 * Resolve with `context.getString(...)` / `stringResource(...)`. Formatted helpers take [Context].
 */
object AuthMessages {
    @StringRes val GENERIC = R.string.error_generic

    // Login
    @StringRes val LOGIN_FIELDS_REQUIRED = R.string.error_login_fields_required
    @StringRes val INVALID_CREDENTIALS = R.string.error_invalid_credentials
    @StringRes val NOT_REGISTERED = R.string.error_not_registered
    @StringRes val GOOGLE_NOT_CONFIGURED = R.string.error_google_not_configured
    @StringRes val GOOGLE_NO_ACCOUNT = R.string.error_google_no_account
    @StringRes val GOOGLE_FAILED = R.string.error_google_sign_in_failed

    // Sign up
    @StringRes val NAME_REQUIRED = R.string.signup_error_name_required
    @StringRes val PASSWORD_SHORT = R.string.signup_error_password_short
    @StringRes val EMAIL_ALREADY_REGISTERED = R.string.error_email_already_registered
    @StringRes val PHONE_ALREADY_REGISTERED = R.string.error_phone_already_registered
    @StringRes val ALREADY_REGISTERED = R.string.error_already_registered

    // Email delivery / rate limits
    @StringRes val EMAIL_DELIVERY_FAILED = R.string.error_signup_email_delivery
    @StringRes val EMAIL_RATE_LIMITED = R.string.error_signup_email_rate_limit
    @StringRes val INVALID_EMAIL = R.string.error_invalid_email

    // Verify / OTP
    @StringRes val VERIFY_EMAIL_SENT = R.string.verify_email_sent
    @StringRes val VERIFY_EMAIL_RESENT = R.string.verify_email_resent
    @StringRes val VERIFY_EMAIL_INVALID_CODE = R.string.verify_email_invalid_code

    // Password reset
    @StringRes val RESET_OTP_INVALID_OR_EXPIRED = R.string.reset_otp_invalid_or_expired
    @StringRes val RESET_PASSWORD_MISMATCH = R.string.reset_password_mismatch
    @StringRes val RESET_PASSWORD_SUCCESS = R.string.reset_password_success
    @StringRes val RESET_PASSWORD_SAME_AS_OLD = R.string.reset_password_same_as_old
    @StringRes val RESET_PASSWORD_SESSION_EXPIRED = R.string.reset_password_session_expired
    @StringRes val RESET_PASSWORD_REQUIREMENTS = R.string.reset_password_requirements

    /** Shown after too many attempts for an auth action on one email. */
    fun authRateLimited(
        context: Context,
        action: AuthRateAction,
        waitMinutes: Int,
    ): String {
        val minutes = waitMinutes.coerceAtLeast(1)
        @StringRes
        val prefixRes =
            when (action) {
                AuthRateAction.LOGIN -> R.string.error_auth_rate_login
                AuthRateAction.SIGNUP -> R.string.error_auth_rate_signup
                AuthRateAction.FORGOT_PASSWORD -> R.string.error_auth_rate_forgot_password
                AuthRateAction.RESET_PASSWORD -> R.string.error_auth_rate_reset_password
            }
        val wait =
            context.resources.getQuantityString(
                R.plurals.error_auth_rate_wait,
                minutes,
                minutes,
            )
        return context.getString(prefixRes, wait)
    }

    /** Privacy-preserving copy after requesting / resending a reset code. */
    fun resetOtpSent(
        context: Context,
        email: String,
    ): String = context.getString(R.string.reset_otp_sent, email)
}
