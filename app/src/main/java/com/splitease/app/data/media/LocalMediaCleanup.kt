package com.splitease.app.data.media

import android.content.Context
import android.net.Uri
import java.io.File

/** Deletes on-disk media folders/files under app-private storage. */
object LocalMediaCleanup {
    const val ATTACHMENT_CAPTURE_DIR = "expense_attachments"

    fun deleteExpensePhotoDir(
        context: Context,
        expenseId: String,
    ) {
        runCatching {
            File(context.filesDir, "expense_photos/$expenseId").deleteRecursively()
        }
    }

    fun deleteGroupCoverFiles(
        context: Context,
        groupId: String,
    ) {
        prunePrefixedMediaFiles(File(context.filesDir, "group_covers"), groupId, keepNewest = 0)
    }

    fun deleteGroupPhotoFiles(
        context: Context,
        groupId: String,
    ) {
        prunePrefixedMediaFiles(File(context.filesDir, "group_photos"), groupId, keepNewest = 0)
    }

    fun pruneGroupCoverFiles(
        context: Context,
        groupId: String,
        keepNewest: Int,
    ) {
        prunePrefixedMediaFiles(File(context.filesDir, "group_covers"), groupId, keepNewest)
    }

    fun pruneGroupPhotoFiles(
        context: Context,
        groupId: String,
        keepNewest: Int,
    ) {
        prunePrefixedMediaFiles(File(context.filesDir, "group_photos"), groupId, keepNewest)
    }

    fun deletePinBoardLocalDir(
        context: Context,
        groupId: String,
    ) {
        runCatching {
            File(context.filesDir, "pinboard/$groupId").deleteRecursively()
        }
    }

    fun deleteUserAvatars(
        context: Context,
        userId: String,
        keepNewest: Int = 0,
    ) {
        prunePrefixedMediaFiles(File(context.filesDir, "avatars"), userId, keepNewest)
    }

    fun deleteLocalFile(
        context: Context,
        path: String?,
    ) {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return
        }
        runCatching {
            val file = File(trimmed).canonicalFile
            if (!isInsideAppStorage(context, file)) return@runCatching
            file.delete()
        }
    }

    /** Removes a camera capture JPEG from app cache after it has been copied or discarded. */
    fun deleteCachedCapture(
        context: Context,
        photoUri: String,
    ) {
        val name = Uri.parse(photoUri).lastPathSegment ?: return
        if (!name.startsWith("capture_") || !name.endsWith(".jpg", ignoreCase = true)) return
        deleteLocalFile(
            context,
            File(File(context.cacheDir, ATTACHMENT_CAPTURE_DIR), name).absolutePath,
        )
    }

    private fun isInsideAppStorage(
        context: Context,
        file: File,
    ): Boolean {
        val roots =
            listOf(context.filesDir, context.cacheDir).map { it.canonicalFile }
        return roots.any { root ->
            file == root || file.path.startsWith(root.path + File.separator)
        }
    }

    private fun prunePrefixedMediaFiles(
        dir: File,
        idPrefix: String,
        keepNewest: Int,
    ) {
        if (!dir.isDirectory) return
        dir
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                    (
                        file.name.equals("$idPrefix.jpg", ignoreCase = true) ||
                            (
                                file.name.startsWith("${idPrefix}_") &&
                                    file.name.endsWith(".jpg", ignoreCase = true)
                            )
                    )
            }?.sortedByDescending { it.lastModified() }
            ?.drop(keepNewest.coerceAtLeast(0))
            ?.forEach { it.delete() }
    }
}
