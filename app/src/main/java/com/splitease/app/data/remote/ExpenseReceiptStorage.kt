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

        /** Deletes a single receipt object (no-op when missing). */
        suspend fun deleteReceipt(
            expenseId: String,
            photoId: String,
        ) {
            runCatching {
                supabase.storage.from(BUCKET).delete(objectPath(expenseId, photoId))
            }
        }

        /** Deletes all receipt objects for [expenseId]. Uses [photoIds] when known; otherwise lists the folder. */
        suspend fun deleteAllForExpense(
            expenseId: String,
            photoIds: Collection<String>,
        ) {
            val knownPaths = photoIds.map { objectPath(expenseId, it) }
            val listedPaths =
                runCatching {
                    supabase.storage
                        .from(BUCKET)
                        .list(expenseId)
                        .mapNotNull { item ->
                            val name = item.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                            "$expenseId/$name"
                        }
                }.getOrDefault(emptyList())
            val paths = (knownPaths + listedPaths).distinct()
            if (paths.isEmpty()) return
            runCatching {
                supabase.storage.from(BUCKET).delete(paths)
            }
        }

        companion object {
            const val BUCKET = "expense-receipts"

            fun objectPath(
                expenseId: String,
                photoId: String,
            ): String = "$expenseId/$photoId.jpg"
        }
    }
