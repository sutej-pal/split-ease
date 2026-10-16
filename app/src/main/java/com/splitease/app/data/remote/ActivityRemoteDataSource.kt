package com.splitease.app.data.remote

import com.splitease.app.data.remote.dto.ActivityEventDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST access for activity events.
 */
@Singleton
class ActivityRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Upserts an activity event row.
         *
         * @param event Activity event DTO.
         */
        suspend fun upsert(event: ActivityEventDto) {
            supabase.from("activity_events").upsert(event)
        }

        /**
         * Fetches recent activity events for the given user.
         *
         * @param userId The signed-in user id.
         * @param limit The max number of events to fetch (default 50).
         * @return Activity event rows.
         */
        suspend fun fetchRecentForUser(
            userId: String,
            limit: Long = 50,
        ): List<ActivityEventDto> =
            supabase
                .from("activity_events")
                .select(Columns.ALL) {
                    filter {
                        or {
                            eq("actor_user_id", userId)
                            ilike("involved_user_ids", "%,$userId,%")
                        }
                    }
                    order("sort_epoch_ms", Order.DESCENDING)
                    limit(limit)
                }.decodeList()
    }
