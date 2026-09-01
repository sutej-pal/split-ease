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
        return context.getString(GENERIC)
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
            if (isNetworkErrorText(current.message) || isNetworkErrorText(current.localizedMessage)) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun isNetworkErrorText(message: String?): Boolean {
        val raw = message?.lowercase() ?: return false
        return raw.contains("unable to resolve host") ||
            raw.contains("unknownhost") ||
            raw.contains("failed to connect") ||
            raw.contains("timeout") ||
            raw.contains("network is unreachable") ||
            raw.contains("no address associated with hostname")
    }
}
