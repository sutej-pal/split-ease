package com.splitease.app.presentation.auth

/**
 * User-facing auth feedback copy (errors + info snackbars).
 *
 * Edit message text here — [AuthViewModel] and auth screens read from this object
 * so copy stays in one place.
 */
object AuthMessages {
    const val GENERIC = "Something went wrong. Try again."

    // Login
    const val LOGIN_FIELDS_REQUIRED = "Enter your email and password."
    const val INVALID_CREDENTIALS = "Invalid email or password. Try again."
    const val NOT_REGISTERED = "You're not registered with us. Please sign up."

    // Sign up
    const val NAME_REQUIRED = "Enter your full name."
    const val PASSWORD_SHORT = "Password must be at least 8 characters."
    const val EMAIL_ALREADY_REGISTERED = "This email is already registered. Please log in."
    const val PHONE_ALREADY_REGISTERED = "This phone number is already registered. Please log in."
    const val ALREADY_REGISTERED = "You're already registered with us. Please log in."

    // Email delivery / rate limits
    const val EMAIL_DELIVERY_FAILED =
        "We couldn't send the verification email. Please try again in a moment."
    const val EMAIL_RATE_LIMITED =
        "Too many verification emails were sent. Wait a few minutes and try again."
    const val INVALID_EMAIL = "Enter a valid email address."

    // Verify / OTP
    const val VERIFY_EMAIL_SENT =
        "Account created. Check your email for a verification code."
    const val VERIFY_EMAIL_RESENT = "Verification code resent. Check your inbox."
    const val VERIFY_EMAIL_INVALID_CODE = "Enter a valid 6-digit code."

    // Password reset
    const val RESET_OTP_INVALID_OR_EXPIRED = "Invalid or expired code."
    const val RESET_PASSWORD_MISMATCH = "Passwords do not match."
    const val RESET_PASSWORD_SUCCESS = "Password updated."
    const val RESET_PASSWORD_SAME_AS_OLD =
        "Choose a different password than your current one."
    const val RESET_PASSWORD_SESSION_EXPIRED =
        "Your reset session expired. Request a new code and try again."
    const val RESET_PASSWORD_REQUIREMENTS =
        "Password must include 8+ characters, upper & lowercase letters, and a number."

    /** Privacy-preserving copy after requesting / resending a reset code. */
    fun resetOtpSent(email: String): String =
        "If an account exists for $email, we've sent a code."
}
