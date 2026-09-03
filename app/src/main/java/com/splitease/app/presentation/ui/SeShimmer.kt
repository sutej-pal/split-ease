package com.splitease.app.presentation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.splitease.app.presentation.theme.SplitEaseColors

private val LocalSeShimmerProgress = compositionLocalOf<State<Float>?> { null }

private const val SHIMMER_DURATION_MS = 1_400

/**
 * One shared shimmer clock for a skeleton tree. Bones then animate in the draw
 * phase instead of each owning an [androidx.compose.animation.core.InfiniteTransition]
 * that recomposes every frame.
 */
@Composable
fun SeShimmerProvider(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "se-shimmer")
    val progress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "se-shimmer-progress",
        )
    CompositionLocalProvider(LocalSeShimmerProgress provides progress) {
        content()
    }
}

/** Applies [SeShimmerProvider] only while [enabled] so idle screens pay no animation cost. */
@Composable
fun SeShimmerHost(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        SeShimmerProvider(content)
    } else {
        content()
    }
}

/**
 * Sweeping highlight drawn behind the content. Reads animation progress in the
 * draw pass so the composition is not invalidated at 60fps.
 */
fun Modifier.seShimmer(): Modifier =
    composed {
        val base = SplitEaseColors.Outline
        val highlight = SplitEaseColors.Surface
        val hosted = LocalSeShimmerProgress.current
        val progress = hosted ?: rememberStandaloneShimmerProgress()
        val colors = remember(base, highlight) { listOf(base, highlight, base) }
        drawBehind {
            val width = size.width.coerceAtLeast(1f)
            val x = -width + (progress.value * width * 2f)
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors = colors,
                        start = Offset(x, 0f),
                        end = Offset(x + width, 0f),
                    ),
            )
        }
    }

@Composable
private fun rememberStandaloneShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "se-shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "se-shimmer-progress",
    )
}
