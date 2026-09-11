package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.DeviceTokenDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access for FCM [device_tokens] rows.
 */
@Singleton
class DeviceTokenRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Upserts the caller's device token (unique on user_id + token).
         *
         * @param token Device token row.
         */
        suspend fun upsert(token: DeviceTokenDto) {
            supabase.from("device_tokens").upsert(token)
        }

        /**
         * Deletes a token for [userId].
         *
         * @param userId Owner user id.
         * @param token FCM registration token.
         */
        suspend fun delete(
            userId: String,
            token: String,
        ) {
            supabase.from("device_tokens").delete {
                filter {
                    eq("user_id", userId)
                    eq("token", token)
                }
            }
        }
    }
