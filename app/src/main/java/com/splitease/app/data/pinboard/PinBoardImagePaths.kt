package com.splitease.app.data.pinboard

private val IMAGE_MARKDOWN = Regex("""!\[([^\]\n]*)]\(([^)\n]+)\)""")

/** Markdown image references in [content], in document order. */
fun pinBoardImagePaths(content: String): List<String> =
    IMAGE_MARKDOWN.findAll(content).map { it.groupValues[2].trim() }.toList()

/** Normalizes a markdown image target for decode / file lookup. */
fun normalizePinImagePath(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("file://", ignoreCase = true)) return trimmed
    val withoutScheme = trimmed.substringAfter("file://")
    return if (withoutScheme.startsWith("/")) withoutScheme else "/$withoutScheme"
}

fun isRemotePinImagePath(path: String): Boolean {
    val normalized = normalizePinImagePath(path)
    return normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("https://", ignoreCase = true)
}

/**
 * Uploads local pin-board images and rewrites [content] markdown targets to public URLs.
 *
 * @return Updated markdown (unchanged when nothing was uploaded).
 */
suspend fun syncPinBoardImagePaths(
    content: String,
    upload: suspend (localPath: String) -> String?,
): String {
    var updated = content
    pinBoardImagePaths(content).forEach { raw ->
        if (isRemotePinImagePath(raw)) return@forEach
        val local = normalizePinImagePath(raw)
        val remote = upload(local) ?: return@forEach
        updated = updated.replace("($raw)", "($remote)")
    }
    return updated
}

/** Finds markdown image references in [content]. */
internal fun findPinBoardImages(content: String): Sequence<MatchResult> = IMAGE_MARKDOWN.findAll(content)
