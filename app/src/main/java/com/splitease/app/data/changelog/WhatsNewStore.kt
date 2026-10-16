package com.splitease.app.data.changelog

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which Play `versionCode` last showed What's new.
 *
 * Device-level (survives sign-out) so an update prompt is not tied to an account.
 *
 * @property context Application context.
 */
@Singleton
class WhatsNewStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Last Play `versionCode` the user dismissed (or first-install auto-ack).
         *
         * @return `0` before any version has been recorded.
         */
        fun lastSeenVersionCode(): Int = prefs.getInt(KEY_LAST_SEEN, 0)

        /**
         * Marks [versionCode] as acknowledged so the update dialog does not repeat.
         *
         * @param versionCode Current [com.splitease.app.BuildConfig.VERSION_CODE].
         */
        fun markSeen(versionCode: Int) {
            prefs.edit { putInt(KEY_LAST_SEEN, versionCode) }
        }

        companion object {
            private const val PREFS_NAME = "splitease_whats_new"
            private const val KEY_LAST_SEEN = "last_seen_version_code"
        }
    }
