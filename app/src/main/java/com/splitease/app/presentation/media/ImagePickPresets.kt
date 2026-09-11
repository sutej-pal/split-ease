package com.splitease.app.presentation.media

import com.splitease.app.data.media.AvatarImageIO

/**
 * Crop / persist parameters for [rememberImagePicker].
 *
 * [cacheSubdir] is under [android.content.Context.getCacheDir] for camera capture and crop output.
 */
data class ImageCropSpec(
    val aspectRatio: Float,
    val maxSidePx: Int,
    val cacheSubdir: String,
    val jpegQuality: Int = 82,
)

/** Shared presets for profile, group, and content images. */
object ImagePickPresets {
    val Avatar =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.SQUARE_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
            cacheSubdir = "avatars",
            jpegQuality = AvatarImageIO.AVATAR_STORED_JPEG_QUALITY,
        )

    val GroupPhoto =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.SQUARE_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
            cacheSubdir = "group_photos",
            jpegQuality = AvatarImageIO.AVATAR_STORED_JPEG_QUALITY,
        )

    val Content =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.CONTENT_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.CONTENT_STORED_MAX_SIDE_PX,
            cacheSubdir = "image_capture",
        )

    val ExpenseReceipt =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.CONTENT_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.CONTENT_STORED_MAX_SIDE_PX,
            cacheSubdir = "expense_receipts",
            jpegQuality = AvatarImageIO.ATTACHMENT_STORED_JPEG_QUALITY,
        )
}
