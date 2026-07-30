package com.splitease.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Decodes [photoUrl] (content/file URI or absolute path) scaled so the longer edge
     * is at most [maxSidePx].
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
                    null
                }
                else -> decodeFileScaled(File(photoUrl), maxSidePx)
            }
        }.getOrNull()
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
