package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.NotificationPrefsDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access for [notification_prefs] (mute all / muted groups).
 */
@Singleton
class NotificationPrefsRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Loads the signed-in user's notification preferences, or null if none.
         *
         * @param userId Owner user id.
         * @return Remote row, or null.
         */
        suspend fun fetch(userId: String): NotificationPrefsDto? =
            supabase
                .from("notification_prefs")
                .select(Columns.ALL) {
                    filter { eq("user_id", userId) }
                }.decodeList<NotificationPrefsDto>()
                .firstOrNull()

        /**
         * Upserts the caller's notification preferences.
         *
         * @param prefs Preference row.
         */
        suspend fun upsert(prefs: NotificationPrefsDto) {
            supabase.from("notification_prefs").upsert(prefs)
        }
    }
