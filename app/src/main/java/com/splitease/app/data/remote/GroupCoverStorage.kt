package com.splitease.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads / deletes group detail cover images in the `group-covers` Storage bucket.
 *
 * Object key: `{groupId}/cover.jpg`. Public URL is stored on `groups.cover_url`.
 */
@Singleton
class GroupCoverStorage
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Uploads [localJpegPath] for [groupId] (upsert) and returns the public object URL.
         */
        suspend fun uploadCover(
            groupId: String,
            localJpegPath: String,
        ): String {
            val file = File(localJpegPath)
            require(file.exists() && file.isFile) { "Cover file missing." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "Cover file is empty." }
            val path = objectPath(groupId)
            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            return supabase.storage.from(BUCKET).publicUrl(path)
        }

        /** Deletes the cover object for [groupId] if present (no-op when missing). */
        suspend fun deleteCover(groupId: String) {
            runCatching {
                supabase.storage.from(BUCKET).delete(objectPath(groupId))
            }
        }

        companion object {
            const val BUCKET = "group-covers"

            fun objectPath(groupId: String): String = "$groupId/cover.jpg"
        }
    }
