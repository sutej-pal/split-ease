package com.splitease.app.data.expense

import com.splitease.app.domain.model.ExpensePhoto
import java.io.File

/**
 * Resolves the URI the UI should load for [ExpensePhoto].
 *
 * Prefer a local JPEG when it still exists on disk; otherwise fall back to the cloud URL.
 * Stale local paths (e.g. after reinstall or cache cleanup) must not hide a valid remote URL.
 */
fun ExpensePhoto.resolvedDisplayUri(): String? {
    val local = localPath?.trim()?.takeIf { it.isNotBlank() }
    if (local != null && isExistingLocalPhoto(local)) return local
    return remoteUrl?.trim()?.takeIf { it.isNotBlank() }
}

private fun isExistingLocalPhoto(path: String): Boolean {
    if (path.startsWith("content:", ignoreCase = true)) return true
    val filePath =
        if (path.startsWith("file:", ignoreCase = true)) {
            android.net.Uri.parse(path).path
        } else {
            path
        }
    return !filePath.isNullOrBlank() && File(filePath).isFile
}
