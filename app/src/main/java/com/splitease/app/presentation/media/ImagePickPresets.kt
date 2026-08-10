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
)

/** Shared presets for profile, group, cover, and content images. */
object ImagePickPresets {
    val Avatar =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.SQUARE_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
            cacheSubdir = "avatars",
        )

    val GroupPhoto =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.SQUARE_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.STORED_MAX_SIDE_PX,
            cacheSubdir = "group_photos",
        )

    val GroupCover =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.COVER_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.COVER_STORED_MAX_SIDE_PX,
            cacheSubdir = "group_covers",
        )

    val Content =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.CONTENT_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.COVER_STORED_MAX_SIDE_PX,
            cacheSubdir = "image_capture",
        )

    val ExpenseReceipt =
        ImageCropSpec(
            aspectRatio = AvatarImageIO.CONTENT_ASPECT_RATIO,
            maxSidePx = AvatarImageIO.COVER_STORED_MAX_SIDE_PX,
            cacheSubdir = "expense_receipts",
        )
}
