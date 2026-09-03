package com.splitease.app.presentation.activity

import android.util.Log
import com.splitease.app.BuildConfig

/**
 * Debug-only performance logging for the Activity feed.
 *
 * Filter Logcat with tag `ActivityPerf` while reproducing scroll jank.
 */
internal object ActivityPerfLog {
    private const val TAG = "ActivityPerf"

    fun emit(
        source: String,
        detail: String,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "emit source=$source $detail")
    }

    fun rebuild(
        reason: String,
        itemCount: Int,
        elapsedMs: Long,
        signatureChanged: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "rebuild reason=$reason items=$itemCount elapsedMs=$elapsedMs signatureChanged=$signatureChanged",
        )
    }

    fun scrollJumpSuspect(
        previousCount: Int,
        newCount: Int,
        previousTopId: String?,
        newTopId: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        if (previousCount == newCount && previousTopId == newTopId) return
        Log.w(
            TAG,
            "list mutation while visible prevCount=$previousCount newCount=$newCount " +
                "prevTop=$previousTopId newTop=$newTopId",
        )
    }
}
