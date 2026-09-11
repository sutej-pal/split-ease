package com.splitease.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads / deletes group photos in the `group-covers` Storage bucket.
 *
 * Object keys:
 * - `{groupId}/photo.jpg` — list/settings avatar (`groups.photo_url`)
 *
 * Leftover `{groupId}/cover.jpg` objects from the removed header-cover feature
 * are deleted with [deleteAllForGroup].
 */
@Singleton
class GroupCoverStorage
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Uploads [localJpegPath] list photo for [groupId] (upsert) and returns the public URL.
         */
        suspend fun uploadPhoto(
            groupId: String,
            localJpegPath: String,
        ): String = uploadJpeg(objectPathPhoto(groupId), localJpegPath, missingLabel = "Photo")

        /** Deletes the list photo object for [groupId] if present (no-op when missing). */
        suspend fun deletePhoto(groupId: String) {
            runCatching {
                supabase.storage.from(BUCKET).delete(objectPathPhoto(groupId))
            }
        }

        /** Deletes list photo and leftover cover objects for [groupId]. */
        suspend fun deleteAllForGroup(groupId: String) {
            deletePhoto(groupId)
            runCatching {
                supabase.storage.from(BUCKET).delete(objectPathLegacyCover(groupId))
            }
        }

        private suspend fun uploadJpeg(
            path: String,
            localJpegPath: String,
            missingLabel: String,
        ): String {
            val file = File(localJpegPath)
            require(file.exists() && file.isFile) { "$missingLabel file missing." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "$missingLabel file is empty." }
            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            return supabase.storage.from(BUCKET).publicUrl(path)
        }

        companion object {
            const val BUCKET = "group-covers"

            fun objectPathPhoto(groupId: String): String = "$groupId/photo.jpg"

            private fun objectPathLegacyCover(groupId: String): String = "$groupId/cover.jpg"
        }
    }
