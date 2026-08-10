package com.splitease.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads expense receipt images in the `expense-receipts` Storage bucket.
 *
 * Object key: `{expenseId}/{photoId}.jpg`.
 */
@Singleton
class ExpenseReceiptStorage
    @Inject
    constructor(
        private val supabase: SupabaseClient,
    ) {
        /**
         * Uploads [localJpegPath] for [expenseId]/[photoId] (upsert) and returns the public URL.
         */
        suspend fun uploadReceipt(
            expenseId: String,
            photoId: String,
            localJpegPath: String,
        ): String {
            val file = File(localJpegPath)
            require(file.exists() && file.isFile) { "Receipt file missing." }
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "Receipt file is empty." }
            val path = objectPath(expenseId, photoId)
            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            return supabase.storage.from(BUCKET).publicUrl(path)
        }

        companion object {
            const val BUCKET = "expense-receipts"

            fun objectPath(
                expenseId: String,
                photoId: String,
            ): String = "$expenseId/$photoId.jpg"
        }
    }
