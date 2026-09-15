package com.splitease.app.presentation.media

import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
import com.splitease.app.presentation.theme.AmberDark
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.TextPrimaryDark
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import java.io.File
import kotlin.math.max
import kotlin.math.min

private val CropFrameInset = 20.dp
private val CropCornerBracket = 22.dp

/**
 * Full-screen cropper that locks the output to [cropSpec.aspectRatio].
 *
 * Uses the SplitEase dark shell so the photo reads clearly while chrome stays on-brand.
 */
@Composable
fun ImageCropDialog(
    sourceUri: String,
    cropSpec: ImageCropSpec,
    cropTitle: String,
    cropBody: String,
    onDismiss: () -> Unit,
    onCropped: (croppedFileUri: String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val decodeMaxSide = (cropSpec.maxSidePx * 2).coerceAtLeast(cropSpec.maxSidePx)
    
    val baseBitmap =
        remember(sourceUri, decodeMaxSide) {
            AvatarImageIO.decodeScaled(
                context = context,
                photoUrl = sourceUri,
                maxSidePx = decodeMaxSide,
            )
        }

    DisposableEffect(baseBitmap) {
        onDispose {
            baseBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }
    
    var rotationTurns by remember(sourceUri) { mutableIntStateOf(0) }
    
    val sourceBitmap = remember(baseBitmap, rotationTurns) {
        if (baseBitmap == null) return@remember null
        val degrees = (rotationTurns % 4) * 90f
        if (degrees == 0f) {
            baseBitmap
        } else {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            Bitmap.createBitmap(
                baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true
            )
        }
    }
    
    DisposableEffect(sourceBitmap) {
        onDispose {
            if (sourceBitmap != baseBitmap) {
                sourceBitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    val shell = SplitEaseColors.ShellBackground
    SeSystemBars(
        statusBarColor = shell,
        navigationBarColor = shell,
        statusBarDarkIcons = false,
        navigationBarDarkIcons = false,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(shell)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
        ) {
            if (sourceBitmap == null) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = SeLayout.detailHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.msg_image_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimaryDark,
                    )
                }
                CropActions(
                    onUsePhoto = null,
                    onRotate = null,
                    onCancel = onDismiss,
                )
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                
                DisposableEffect(rotationTurns) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    onDispose { }
                }
                
                val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
                val cropMetrics = remember { CropMetrics() }
                val aspectRatio = cropSpec.aspectRatio
                val frameInsetPx = with(density) { CropFrameInset.toPx() }
                val bracketLenPx = with(density) { CropCornerBracket.toPx() }

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                ) {
                    val canvasW = with(density) { maxWidth.toPx() }
                    val canvasH = with(density) { maxHeight.toPx() }
                    val maxFrameW = (canvasW - frameInsetPx * 2f).coerceAtLeast(1f)
                    val maxFrameH = (canvasH - frameInsetPx * 2f).coerceAtLeast(1f)
                    val frameW =
                        if (maxFrameW / aspectRatio <= maxFrameH) {
                            maxFrameW
                        } else {
                            maxFrameH * aspectRatio
                        }
                    val frameH = frameW / aspectRatio
                    val frameLeft = (canvasW - frameW) / 2f
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
                        val imgLeft = frameLeft + (frameW - drawnW) / 2f + offsetX
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

                        val frameRect =
                            androidx.compose.ui.geometry.Rect(
                                offset = Offset(frameLeft, frameTop),
                                size = Size(frameW, frameH),
                            )
                        val scrimPath =
                            Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(
                                    androidx.compose.ui.geometry.Rect(
                                        Offset.Zero,
                                        Size(size.width, size.height),
                                    ),
                                )
                                addRect(frameRect)
                            }
                        drawPath(
                            path = scrimPath,
                            color = Color.Black.copy(alpha = 0.58f),
                        )

                        val frameBorderColor = Color.White.copy(alpha = 0.88f)
                        val thinStroke = 1.dp.toPx()
                        drawRect(
                            color = frameBorderColor,
                            topLeft = Offset(frameLeft, frameTop),
                            size = Size(frameW, frameH),
                            style = Stroke(width = thinStroke),
                        )

                        // 3x3 Grid
                        val colW = frameW / 3f
                        val rowH = frameH / 3f
                        for (i in 1..2) {
                            val x = frameLeft + i * colW
                            drawLine(frameBorderColor, Offset(x, frameTop), Offset(x, frameTop + frameH), strokeWidth = thinStroke)
                            val y = frameTop + i * rowH
                            drawLine(frameBorderColor, Offset(frameLeft, y), Offset(frameLeft + frameW, y), strokeWidth = thinStroke)
                        }

                        val accent = Color.White
                        val stroke = 3.dp.toPx()
                        
                        // Top-left
                        drawLine(accent, Offset(frameLeft, frameTop), Offset(frameLeft, frameTop + bracketLenPx), stroke)
                        drawLine(accent, Offset(frameLeft, frameTop), Offset(frameLeft + bracketLenPx, frameTop), stroke)
                        // Top-right
                        drawLine(accent, Offset(frameLeft + frameW, frameTop), Offset(frameLeft + frameW, frameTop + bracketLenPx), stroke)
                        drawLine(accent, Offset(frameLeft + frameW, frameTop), Offset(frameLeft + frameW - bracketLenPx, frameTop), stroke)
                        // Bottom-left
                        drawLine(accent, Offset(frameLeft, frameTop + frameH), Offset(frameLeft, frameTop + frameH - bracketLenPx), stroke)
                        drawLine(accent, Offset(frameLeft, frameTop + frameH), Offset(frameLeft + bracketLenPx, frameTop + frameH), stroke)
                        // Bottom-right
                        drawLine(accent, Offset(frameLeft + frameW, frameTop + frameH), Offset(frameLeft + frameW, frameTop + frameH - bracketLenPx), stroke)
                        drawLine(accent, Offset(frameLeft + frameW, frameTop + frameH), Offset(frameLeft + frameW - bracketLenPx, frameTop + frameH), stroke)
                        
                        // Middle edges (thick white)
                        val midX = frameLeft + frameW / 2f
                        val midY = frameTop + frameH / 2f
                        val midBracketLen = bracketLenPx
                        
                        // Top middle
                        drawLine(accent, Offset(midX - midBracketLen / 2f, frameTop), Offset(midX + midBracketLen / 2f, frameTop), stroke)
                        // Bottom middle
                        drawLine(accent, Offset(midX - midBracketLen / 2f, frameTop + frameH), Offset(midX + midBracketLen / 2f, frameTop + frameH), stroke)
                        // Left middle
                        drawLine(accent, Offset(frameLeft, midY - midBracketLen / 2f), Offset(frameLeft, midY + midBracketLen / 2f), stroke)
                        // Right middle
                        drawLine(accent, Offset(frameLeft + frameW, midY - midBracketLen / 2f), Offset(frameLeft + frameW, midY + midBracketLen / 2f), stroke)
                    }
                }

                CropActions(
                    onUsePhoto = {
                        val frameW = cropMetrics.frameW
                        val frameH = cropMetrics.frameH
                        val coverBase = cropMetrics.baseScale
                        if (frameW <= 0f || frameH <= 0f || coverBase <= 0f) {
                            return@CropActions
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
                        if (crop.width() < 2 || crop.height() < 2) return@CropActions
                        val dest =
                            File(
                                context.cacheDir,
                                "${cropSpec.cacheSubdir}/crop_${System.currentTimeMillis()}.jpg",
                            )
                        runCatching {
                            AvatarImageIO.cropScaledJpeg(
                                sourceBitmap = sourceBitmap,
                                cropRect = crop,
                                destFile = dest,
                                maxSidePx = cropSpec.maxSidePx,
                                quality = cropSpec.jpegQuality,
                            )
                            onCropped(uriFile(dest))
                        }
                    },
                    onRotate = { rotationTurns++ },
                    onCancel = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun CropActions(
    onUsePhoto: (() -> Unit)?,
    onRotate: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SplitEaseColors.ShellSurface)
                .padding(horizontal = SeLayout.detailHorizontal)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = SplitEaseColors.Primary,
            )
        }
        
        if (onRotate != null) {
            IconButton(onClick = onRotate) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Rotate",
                    tint = Color.White,
                )
            }
        } else {
            Spacer(modifier = Modifier.padding(24.dp))
        }
        
        if (onUsePhoto != null) {
            TextButton(onClick = onUsePhoto) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = SplitEaseColors.Primary,
                )
            }
        } else {
            Spacer(modifier = Modifier.padding(24.dp))
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
