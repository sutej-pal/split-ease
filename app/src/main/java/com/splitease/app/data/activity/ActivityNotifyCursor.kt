package com.splitease.app.data.activity

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-user cursor so the first activity hydrate does not notify for old rows.
 */
@Singleton
class ActivityNotifyCursor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * @param userId Signed-in user id.
         * @return Epoch millis, or `0` when this user has never completed a pull.
         */
        fun get(userId: String): Long = prefs.getLong(key(userId), 0L)

        /**
         * @param userId Signed-in user id.
         * @param epochMs Cursor to store. Values `<= 0` are ignored.
         */
        fun set(userId: String, epochMs: Long) {
            if (epochMs <= 0L) return
            prefs.edit { putLong(key(userId), epochMs) }
        }

        private fun key(userId: String): String = "wm_$userId"

        private companion object {
            const val PREFS = "activity_notify_cursor"
        }
    }
