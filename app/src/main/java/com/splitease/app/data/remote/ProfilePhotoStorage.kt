package com.splitease.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads / deletes profile photos in the `user-avatars` Storage bucket.
 *
 * Object key: `{userId}/photo.jpg` — `profiles.photo_url`.
 */
@Singleton
class ProfilePhotoStorage
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Uploads [localJpegPath] for [userId] (upsert) and returns the public object URL.
         */
        suspend fun uploadPhoto(
            userId: String,
            localJpegPath: String,
        ): String {
            val file = File(localJpegPath)
            require(file.exists() && file.isFile) { "Photo file missing." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "Photo file is empty." }
            val path = objectPath(userId)
            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            return supabase.storage.from(BUCKET).publicUrl(path)
        }

        /** Deletes the profile photo object for [userId] if present (no-op when missing). */
        suspend fun deletePhoto(userId: String) {
            runCatching {
                supabase.storage.from(BUCKET).delete(objectPath(userId))
            }
        }

        companion object {
            const val BUCKET = "user-avatars"

            fun objectPath(userId: String): String = "$userId/photo.jpg"
        }
    }
