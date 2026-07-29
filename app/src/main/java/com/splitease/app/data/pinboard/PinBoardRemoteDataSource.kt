package com.splitease.app.data.pinboard

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase `pin_boards` row.
 */
@Serializable
data class PinBoardDto(
    @SerialName("group_id") val groupId: String,
    val content: String,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Thin PostgREST access for the per-group pin board.
 */
@Singleton
class PinBoardRemoteDataSource
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Fetches the pin board for [groupId], or null if none exists yet.
         */
        suspend fun fetch(groupId: String): PinBoardDto? =
            supabase
                .from("pin_boards")
                .select(Columns.ALL) {
                    filter { eq("group_id", groupId) }
                }.decodeList<PinBoardDto>()
                .firstOrNull()

        /**
         * Creates or updates the pin board for a group (upsert on PK).
         */
        suspend fun upsert(dto: PinBoardDto) {
            supabase.from("pin_boards").upsert(dto)
        }
    }
