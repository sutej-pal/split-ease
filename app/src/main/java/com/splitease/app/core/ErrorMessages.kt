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
}
