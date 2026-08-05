package com.splitease.app.presentation.groups

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import java.io.File
import kotlin.math.max

/**
 * Full-screen cropper that locks the output to the group detail header aspect ratio.
 */
@Composable
fun CoverImageCropDialog(
    sourceUri: String,
    onDismiss: () -> Unit,
    onCropped: (croppedFileUri: String) -> Unit,
    aspectRatio: Float = AvatarImageIO.COVER_ASPECT_RATIO,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sourceBitmap =
        remember(sourceUri) {
            AvatarImageIO.decodeScaled(
                context = context,
                photoUrl = sourceUri,
                maxSidePx = AvatarImageIO.COVER_STORED_MAX_SIDE_PX * 2,
            )
        }

    DisposableEffect(sourceBitmap) {
        onDispose {
            sourceBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.group_cover_crop_title),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.group_cover_crop_body),
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (sourceBitmap == null) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.msg_group_cover_load_failed),
                        color = Color.White,
                    )
                }
                SeTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                )
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
                val cropMetrics = remember { CropMetrics() }

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    val canvasW = with(density) { maxWidth.toPx() }
                    val canvasH = with(density) { maxHeight.toPx() }
                    val frameW = canvasW
                    val frameH = (frameW / aspectRatio).coerceAtMost(canvasH * 0.72f)
                    val frameTop = (canvasH - frameH) / 2f
                    val coverBase =
                        max(
                            frameW / sourceBitmap.width.toFloat(),
                            frameH / sourceBitmap.height.toFloat(),
                        )
                    cropMetrics.frameW = frameW
                    cropMetrics.frameH = frameH
                    cropMetrics.baseScale = coverBase

                    fun clampOffsets(
                        nextScale: Float,
                        ox: Float,
                        oy: Float,
                    ): Pair<Float, Float> {
                        val drawnW = sourceBitmap.width * coverBase * nextScale
                        val drawnH = sourceBitmap.height * coverBase * nextScale
                        val maxX = max(0f, (drawnW - frameW) / 2f)
                        val maxY = max(0f, (drawnH - frameH) / 2f)
                        return ox.coerceIn(-maxX, maxX) to oy.coerceIn(-maxY, maxY)
                    }

                    Canvas(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(coverBase, frameW, frameH) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                        val (cx, cy) =
                                            clampOffsets(
                                                nextScale,
                                                offsetX + pan.x,
                                                offsetY + pan.y,
                                            )
                                        scale = nextScale
                                        offsetX = cx
                                        offsetY = cy
                                    }
                                },
                    ) {
                        val drawnW = sourceBitmap.width * coverBase * scale
                        val drawnH = sourceBitmap.height * coverBase * scale
                        val imgLeft = (frameW - drawnW) / 2f + offsetX
                        val imgTop = frameTop + (frameH - drawnH) / 2f + offsetY

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                            dstSize =
                                IntSize(
                                    drawnW.toInt().coerceAtLeast(1),
                                    drawnH.toInt().coerceAtLeast(1),
                                ),
                        )

                        val scrim = Color.Black.copy(alpha = 0.55f)
                        drawRect(scrim, size = Size(size.width, frameTop))
                        drawRect(
                            scrim,
                            topLeft = Offset(0f, frameTop + frameH),
                            size = Size(size.width, size.height - frameTop - frameH),
                        )
                        drawRect(
                            Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(0f, frameTop),
                            size = Size(frameW, frameH),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SePrimaryButton(
                    text = stringResource(R.string.action_use_photo),
                    onClick = {
                        val frameW = cropMetrics.frameW
                        val frameH = cropMetrics.frameH
                        val coverBase = cropMetrics.baseScale
                        if (frameW <= 0f || frameH <= 0f || coverBase <= 0f) {
                            return@SePrimaryButton
                        }
                        val coverScale = coverBase * scale
                        val drawnW = sourceBitmap.width * coverScale
                        val drawnH = sourceBitmap.height * coverScale
                        val imgLeftInFrame = (frameW - drawnW) / 2f + offsetX
                        val imgTopInFrame = (frameH - drawnH) / 2f + offsetY
                        val srcLeft = ((0f - imgLeftInFrame) / coverScale).toInt()
                        val srcTop = ((0f - imgTopInFrame) / coverScale).toInt()
                        val srcRight = ((frameW - imgLeftInFrame) / coverScale).toInt()
                        val srcBottom = ((frameH - imgTopInFrame) / coverScale).toInt()
                        val crop =
                            Rect(
                                srcLeft.coerceIn(0, sourceBitmap.width - 1),
                                srcTop.coerceIn(0, sourceBitmap.height - 1),
                                srcRight.coerceIn(1, sourceBitmap.width),
                                srcBottom.coerceIn(1, sourceBitmap.height),
                            )
                        if (crop.width() < 2 || crop.height() < 2) return@SePrimaryButton
                        val dest =
                            File(
                                context.cacheDir,
                                "group_covers/crop_${System.currentTimeMillis()}.jpg",
                            )
                        runCatching {
                            AvatarImageIO.cropScaledJpeg(
                                sourceBitmap = sourceBitmap,
                                cropRect = crop,
                                destFile = dest,
                            )
                            onCropped(uriFile(dest))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SeTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** Prefer a file:// URI so [AvatarImageIO] can decode the cropped cache file. */
private fun uriFile(file: File): String = "file://${file.absolutePath}"

private class CropMetrics {
    var frameW: Float = 0f
    var frameH: Float = 0f
    var baseScale: Float = 0f
}
