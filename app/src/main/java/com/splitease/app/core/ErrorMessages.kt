package com.splitease.app.core

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.splitease.app.R

/**
 * Shared user-facing error copy. Unexpected failures always show [GENERIC] in the UI;
 * the original throwable is logged to Logcat for development.
 */
object ErrorMessages {
    @StringRes
    val GENERIC = R.string.error_generic

    /**
     * Logs [throwable] to Logcat and returns the generic user-facing string.
     * Log failures are ignored so unit tests without a mocked [Log] still succeed.
     */
    fun message(
        context: Context,
        tag: String,
        throwable: Throwable,
    ): String {
        log(tag, throwable)
        if (isInviteSenderProfileMissing(throwable)) {
            return context.getString(R.string.msg_invite_sender_profile_missing)
        }
        if (isInvalidOtp(throwable)) {
            return context.getString(R.string.error_invalid_otp)
        }
        return context.getString(GENERIC)
    }

    /**
     * True when invite creation refused to invent a sender name.
     */
    fun isInviteSenderProfileMissing(throwable: Throwable?): Boolean {
        val raw = throwable?.message ?: return false
        return raw.contains("Invite sender profile is missing", ignoreCase = true)
    }

    /**
     * True when [throwable] indicates an invalid, expired, or wrong OTP code.
     */
    fun isInvalidOtp(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val raw = collectErrorText(throwable)
        val lower = raw.lowercase()
        return (
            ("token has expired or is invalid" in lower) ||
                ("invalid otp" in lower) ||
                ("invalid_otp" in lower) ||
                ("wrong otp" in lower) ||
                ("wrong_otp" in lower) ||
                ("otp_expired" in lower) ||
                ("otp_invalid" in lower) ||
                ("invalid token" in lower) ||
                ("invalid_token" in lower) ||
                ("token is invalid" in lower) ||
                ("token is expired" in lower) ||
                ("token expired" in lower) ||
                ("bad_code_verifier" in lower) ||
                ("invalid_grant" in lower) ||
                ("invalid grant" in lower) ||
                ("invalid verification code" in lower) ||
                ("verification code is invalid" in lower) ||
                ("code is invalid" in lower) ||
                ("confirmation code is invalid" in lower) ||
                (("invalid" in lower) && ("code" in lower)) ||
                (("invalid" in lower) && ("token" in lower)) ||
                (("invalid" in lower) && ("otp" in lower)) ||
                (("expired" in lower) && ("otp" in lower)) ||
                (("expired" in lower) && ("code" in lower)) ||
                (("expired" in lower) && ("token" in lower))
        )
    }

    private fun collectErrorText(throwable: Throwable?): String {
        if (throwable == null) return ""
        val parts = linkedSetOf<String>()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            current.message
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts += it }
            val localized = current.localizedMessage?.trim().orEmpty()
            if (localized.isNotEmpty()) parts += localized
            current = current.cause
            depth++
        }
        return parts.joinToString("\n")
    }

    /**
     * Like [message], or `null` when [throwable] is null (success path).
     */
    fun messageOrNull(
        context: Context,
        tag: String,
        throwable: Throwable?,
    ): String? {
        if (throwable == null) return null
        return message(context, tag, throwable)
    }

    fun log(
        tag: String,
        throwable: Throwable?,
    ) {
        if (throwable == null) return
        runCatching {
            Log.e(tag, throwable.message ?: "Unknown error", throwable)
        }
    }

    fun isNetworkError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            if (isNetworkExceptionType(current)) return true
            if (isNetworkErrorText(current.message) || isNetworkErrorText(current.localizedMessage)) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun isNetworkExceptionType(error: Throwable): Boolean =
        error is java.net.UnknownHostException ||
            error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException ||
            error is java.net.NoRouteToHostException ||
            error is java.net.SocketException ||
            error is java.net.UnknownServiceException ||
            error is java.io.InterruptedIOException ||
            error is javax.net.ssl.SSLException

    private fun isNetworkErrorText(message: String?): Boolean {
        val raw = message?.lowercase() ?: return false
        return raw.contains("unable to resolve host") ||
            raw.contains("unknownhost") ||
            raw.contains("failed to connect") ||
            isTimeoutNetworkText(raw) ||
            raw.contains("network is unreachable") ||
            raw.contains("no address associated with hostname") ||
            raw.contains("network_error") ||
            raw.contains("network error") ||
            raw.contains("connection abort") ||
            raw.contains("connection reset") ||
            raw.contains("connection refused") ||
            raw.contains("econnrefused") ||
            raw.contains("econnreset") ||
            raw.contains("enetunreach") ||
            raw.contains("ehostunreach") ||
            raw.contains("no internet") ||
            raw.contains("err_internet_disconnected") ||
            raw.contains("err_name_not_resolved") ||
            raw.contains("unexpected end of stream") ||
            raw.contains("stream was reset") ||
            raw.contains("api_not_connected") ||
            raw.contains("unable to resolve") ||
            (
                (raw.contains("one tap") || raw.contains("sign in") || raw.contains("credential")) &&
                    (
                        raw.contains(": 7:") ||
                            raw.contains(": 15:") ||
                            raw.contains(": 16:")
                    )
            )
    }

    /**
     * "timeout" / "timed out" only count as network when the message also looks
     * like a connection failure. Bare Play Services "operation timed out"
     * (user waited too long) must not be mapped to offline.
     */
    private fun isTimeoutNetworkText(raw: String): Boolean {
        val hasTimeout = raw.contains("timeout") || raw.contains("timed out")
        if (!hasTimeout) return false
        return raw.contains("url=") ||
            raw.contains("http") ||
            raw.contains("connect") ||
            raw.contains("host") ||
            raw.contains("network") ||
            raw.contains("socket") ||
            raw.contains("ssl") ||
            raw.contains("dns")
    }
}
