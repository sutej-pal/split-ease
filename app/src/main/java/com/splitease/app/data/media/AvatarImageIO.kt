package com.splitease.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max

/**
 * Loads and persists profile photos at a safe on-screen size.
 *
 * Full camera / gallery images can be tens of megapixels; decoding them at full
 * resolution crashes Compose (`Canvas: trying to draw too large(...) bitmap`).
 */
object AvatarImageIO {
    /** Max edge length for persisted avatars (enough for UI badges / profile header). */
    const val STORED_MAX_SIDE_PX = 512

    /** Max edge length when decoding only for on-screen preview. */
    const val PREVIEW_MAX_SIDE_PX = 256

    /** Max edge for persisted group detail cover images (banner-sized, not full camera res). */
    const val COVER_STORED_MAX_SIDE_PX = 1280

    /** Max edge when decoding a cover for on-screen display. */
    const val COVER_PREVIEW_MAX_SIDE_PX = 1280

    /** Width ÷ height for the group detail header cover crop frame. */
    const val COVER_ASPECT_RATIO = 2.4f

    /**
     * Decodes [photoUrl] (content/file URI, absolute path, or https URL) scaled so the longer
     * edge is at most [maxSidePx]. Remote URLs are cached under app files.
     */
    fun decodeScaled(
        context: Context,
        photoUrl: String?,
        maxSidePx: Int = PREVIEW_MAX_SIDE_PX,
    ): Bitmap? {
        if (photoUrl.isNullOrBlank()) return null
        return runCatching {
            when {
                photoUrl.startsWith("content:", ignoreCase = true) ||
                    photoUrl.startsWith("file:", ignoreCase = true) -> {
                    decodeUriScaled(context, Uri.parse(photoUrl), maxSidePx)
                }
                photoUrl.startsWith("http://", ignoreCase = true) ||
                    photoUrl.startsWith("https://", ignoreCase = true) -> {
                    val cached = cacheRemoteImage(context, photoUrl) ?: return@runCatching null
                    decodeFileScaled(cached, maxSidePx)
                }
                else -> decodeFileScaled(File(photoUrl), maxSidePx)
            }
        }.getOrNull()
    }

    /**
     * Downloads [remoteUrl] into a stable cache file (reuses existing bytes when present).
     *
     * @return Local cache file, or null on failure.
     */
    fun cacheRemoteImage(
        context: Context,
        remoteUrl: String,
    ): File? {
        if (remoteUrl.isBlank()) return null
        val dest = remoteCacheFile(context, remoteUrl)
        if (dest.exists() && dest.length() > 0L) return dest
        return runCatching {
            val connection =
                (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val tmp = File(dest.parentFile, "${dest.name}.tmp")
                connection.inputStream.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                dest.takeIf { it.exists() && it.length() > 0L }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Copies an already-local JPEG into the remote-URL cache slot so the UI can decode
     * [remoteUrl] offline immediately after upload.
     */
    fun seedRemoteImageCache(
        context: Context,
        remoteUrl: String,
        localJpeg: File,
    ): File? {
        if (remoteUrl.isBlank() || !localJpeg.exists()) return null
        return runCatching {
            val dest = remoteCacheFile(context, remoteUrl)
            localJpeg.copyTo(dest, overwrite = true)
            dest
        }.getOrNull()
    }

    private fun remoteCacheFile(
        context: Context,
        remoteUrl: String,
    ): File {
        val dir = File(context.filesDir, "remote_image_cache").apply { mkdirs() }
        return File(dir, "${sha1Hex(remoteUrl)}.jpg")
    }

    private fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    /**
     * Copies [photoUri] into [destFile] as a JPEG with the longer edge ≤ [maxSidePx].
     *
     * @return Absolute path of [destFile].
     */
    fun copyScaledJpeg(
        context: Context,
        photoUri: String,
        destFile: File,
        maxSidePx: Int = STORED_MAX_SIDE_PX,
        quality: Int = 85,
    ): String {
        destFile.parentFile?.mkdirs()
        val uri = Uri.parse(photoUri.trim())
        val bitmap =
            decodeUriScaled(context, uri, maxSidePx)
                ?: decodeFileScaled(File(uri.path ?: photoUri), maxSidePx)
                ?: error("Could not read the selected photo.")
        try {
            FileOutputStream(destFile).use { out ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    "Could not compress the selected photo."
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return destFile.absolutePath
    }

    /**
     * Crops [sourceBitmap] to [cropRect] (in bitmap pixels), scales the longer edge to
     * ≤ [maxSidePx], and writes a JPEG to [destFile].
     *
     * @return Absolute path of [destFile].
     */
    fun cropScaledJpeg(
        sourceBitmap: Bitmap,
        cropRect: android.graphics.Rect,
        destFile: File,
        maxSidePx: Int = COVER_STORED_MAX_SIDE_PX,
        quality: Int = 82,
    ): String {
        destFile.parentFile?.mkdirs()
        val safeLeft = cropRect.left.coerceIn(0, sourceBitmap.width - 1)
        val safeTop = cropRect.top.coerceIn(0, sourceBitmap.height - 1)
        val safeWidth = cropRect.width().coerceAtLeast(1).coerceAtMost(sourceBitmap.width - safeLeft)
        val safeHeight = cropRect.height().coerceAtLeast(1).coerceAtMost(sourceBitmap.height - safeTop)
        val cropped = Bitmap.createBitmap(sourceBitmap, safeLeft, safeTop, safeWidth, safeHeight)
        val scaled =
            if (max(cropped.width, cropped.height) <= maxSidePx) {
                cropped
            } else {
                val scale = maxSidePx.toFloat() / max(cropped.width, cropped.height).toFloat()
                val w = (cropped.width * scale).toInt().coerceAtLeast(1)
                val h = (cropped.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(cropped, w, h, true).also {
                    if (it !== cropped && !cropped.isRecycled) cropped.recycle()
                }
            }
        try {
            FileOutputStream(destFile).use { out ->
                require(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    "Could not compress the cropped photo."
                }
            }
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
        return destFile.absolutePath
    }

    private fun decodeUriScaled(
        context: Context,
        uri: Uri,
        maxSidePx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
            }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun decodeFileScaled(
        file: File,
        maxSidePx: Int,
    ): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
            }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun sampleSizeFor(
        width: Int,
        height: Int,
        maxSidePx: Int,
    ): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxSidePx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
