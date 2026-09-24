package com.splitease.app.data.remote

/**
 * Parses Supabase Storage object keys from public URLs.
 *
 * Public URLs look like:
 * `https://{project}.supabase.co/storage/v1/object/public/{bucket}/{objectPath}`
 */
object StorageObjectPaths {
    /**
     * Returns the object key inside [bucket], or null when [publicUrl] is not from that bucket.
     */
    fun objectPathFromPublicUrl(
        publicUrl: String,
        bucket: String,
    ): String? {
        val trimmed = publicUrl.trim()
        if (trimmed.isEmpty()) return null
        val marker = "/storage/v1/object/public/$bucket/"
        val start = trimmed.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        return trimmed
            .substring(start + marker.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .takeIf { it.isNotEmpty() }
    }

    /** Extracts `{imageId}` from a pin-board public URL for [groupId]. */
    fun pinBoardImageIdFromPublicUrl(
        publicUrl: String,
        groupId: String,
    ): String? {
        val objectPath =
            objectPathFromPublicUrl(publicUrl, PinBoardImageStorage.BUCKET) ?: return null
        val prefix = "$groupId/"
        if (!objectPath.startsWith(prefix)) return null
        val fileName = objectPath.removePrefix(prefix)
        if (!fileName.endsWith(".jpg", ignoreCase = true)) return null
        return fileName.removeSuffix(".jpg").removeSuffix(".JPG").takeIf { it.isNotEmpty() }
    }
}
