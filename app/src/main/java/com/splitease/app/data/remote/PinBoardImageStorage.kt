package com.splitease.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads pin-board inline images in the `pin-board-images` Storage bucket.
 *
 * Object key: `{groupId}/{imageId}.jpg`.
 */
@Singleton
class PinBoardImageStorage
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Uploads [localJpegPath] for [groupId]/[imageId] (upsert) and returns the public URL.
         */
        suspend fun upload(
            groupId: String,
            imageId: String,
            localJpegPath: String,
        ): String {
            val file = File(localJpegPath)
            require(file.exists() && file.isFile) { "Pin board image file missing." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "Pin board image file is empty." }
            val path = objectPath(groupId, imageId)
            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            return supabase.storage.from(BUCKET).publicUrl(path)
        }

        companion object {
            const val BUCKET = "pin-board-images"

            fun objectPath(
                groupId: String,
                imageId: String,
            ): String = "$groupId/$imageId.jpg"
        }
    }
