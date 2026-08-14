package com.splitease.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.splitease.app.BuildConfig
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
 *
 * Decodes bake EXIF orientation into pixel data so crop / preview match the
 * upright image users see in the system gallery.
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

    /** Width ÷ height for profile and group avatar crops. */
    const val SQUARE_ASPECT_RATIO = 1f

    /** Max edge for persisted pinboard / content image crops. */
    const val CONTENT_STORED_MAX_SIDE_PX = 1280

    /** Max edge for persisted expense receipt attachments. */
    const val ATTACHMENT_STORED_MAX_SIDE_PX = 1280

    /** Max edge when decoding expense attachment thumbnails. */
    const val ATTACHMENT_PREVIEW_MAX_SIDE_PX = 480

    /** Max edge when decoding expense attachments in the full-screen gallery. */
    const val ATTACHMENT_GALLERY_MAX_SIDE_PX = 1600

    /** JPEG quality for persisted expense receipt attachments (receipts compress well). */
    const val ATTACHMENT_STORED_JPEG_QUALITY = 78

    /** Width ÷ height for pinboard / content image crops. */
    const val CONTENT_ASPECT_RATIO = 4f / 3f

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
                    decodeUriScaled(context, photoUrl.toUri(), maxSidePx)
                }
                photoUrl.startsWith("http://", ignoreCase = true) ||
                    photoUrl.startsWith("https://", ignoreCase = true) -> {
                    decodeRemoteScaled(context, photoUrl, maxSidePx)
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
                    applySupabaseAuthHeaders(remoteUrl)
                }
            try {
                if (connection.responseCode !in 200..299) {
                    runCatching { dest.delete() }
                    return@runCatching null
                }
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

    private fun decodeRemoteScaled(
        context: Context,
        remoteUrl: String,
        maxSidePx: Int,
    ): Bitmap? {
        val cached = cacheRemoteImage(context, remoteUrl) ?: return null
        decodeFileScaled(cached, maxSidePx)?.let { return it }
        evictRemoteCache(context, remoteUrl)
        val retry = cacheRemoteImage(context, remoteUrl) ?: return null
        return decodeFileScaled(retry, maxSidePx)
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

    /** Removes a cached remote image decode file for [remoteUrl], if present. */
    fun evictRemoteCache(
        context: Context,
        remoteUrl: String,
    ) {
        if (remoteUrl.isBlank()) return
        runCatching {
            val file = remoteCacheFile(context, remoteUrl)
            if (file.exists()) file.delete()
        }
    }

    private fun remoteCacheFile(
        context: Context,
        remoteUrl: String,
    ): File {
        val dir = File(context.filesDir, "remote_image_cache").apply { mkdirs() }
        val cacheKey = remoteUrl.substringBefore('?').trim()
        return File(dir, "${sha1Hex(cacheKey)}.jpg")
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
        val trimmed = photoUri.trim()
        val bitmap =
            when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) ->
                    decodeScaled(context, trimmed, maxSidePx)
                else -> {
                    val uri = trimmed.toUri()
                    decodeUriScaled(context, uri, maxSidePx)
                        ?: decodeFileScaled(File(uri.path ?: trimmed), maxSidePx)
                }
            } ?: error("Could not read the selected photo.")
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
                cropped.scale(w, h).also {
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
        val decoded =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
        val orientation = readExifOrientation(context, uri)
        return applyExifOrientation(decoded, orientation)
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
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val orientation = readExifOrientation(file)
        return applyExifOrientation(decoded, orientation)
    }

    private fun readExifOrientation(
        context: Context,
        uri: Uri,
    ): Int =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun readExifOrientation(file: File): Int =
        runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /**
     * Bakes EXIF orientation into pixel data so [BitmapFactory] results match gallery display.
     * Recycles [bitmap] when a new bitmap is created.
     */
    private fun applyExifOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return runCatching {
            val corrected =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (corrected !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            corrected
        }.getOrDefault(bitmap)
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

    /**
     * Supabase Storage objects are fetched outside the Supabase client. Always send the anon
     * key; prefer the signed-in JWT when available so authenticated-only bucket policies still
     * authorize the GET after public-select migrations (or for private buckets).
     */
    private fun HttpURLConnection.applySupabaseAuthHeaders(remoteUrl: String) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (base.isBlank() || key.isBlank()) return
        if (!remoteUrl.startsWith(base, ignoreCase = true)) return
        setRequestProperty("apikey", key)
        val bearer = SupabaseImageAuth.accessToken?.takeIf { it.isNotBlank() } ?: key
        setRequestProperty("Authorization", "Bearer $bearer")
    }
}
