package com.splitease.app.presentation.ads

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeLayout

/**
 * Banner sizing mode for [SeBannerAd].
 *
 * [Anchored] keeps a compact sticky banner. [Inline] requests a taller adaptive
 * creative up to [Inline.maxHeightDp] (or the available container height).
 */
sealed class SeBannerAdSize {
    data object Anchored : SeBannerAdSize()

    data class Inline(
        /** Cap for AdMob's inline adaptive height; null uses available container height. */
        val maxHeightDp: Int? = null,
    ) : SeBannerAdSize()
}

/**
 * Adaptive banner styled for list / form surfaces.
 *
 * Renders nothing when ads are disabled, consent blocks requests, or load fails.
 */
@Composable
fun SeBannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SeLayout.detailHorizontal,
    size: SeBannerAdSize = SeBannerAdSize.Anchored,
    showBottomDivider: Boolean = true,
    onHeightChanged: (Dp) -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        SeBannerAdPlaceholder(
            modifier = modifier,
            horizontalPadding = horizontalPadding,
            tall = size is SeBannerAdSize.Inline,
            showBottomDivider = showBottomDivider,
        )
        val previewHeight = if (size is SeBannerAdSize.Inline) 90.dp else 50.dp
        val currentOnHeightChanged = rememberUpdatedState(onHeightChanged)
        LaunchedEffect(size, showBottomDivider) {
            currentOnHeightChanged.value(previewHeight + 8.dp + if (showBottomDivider) 9.dp else 0.dp)
        }
        return
    }

    if (!AdConfig.isEnabled || adUnitId.isBlank()) {
        LaunchedEffect(Unit) { onHeightChanged(0.dp) }
        return
    }

    val canRequestAds by AdConsentManager.canRequestAds
    if (!canRequestAds) {
        LaunchedEffect(Unit) { onHeightChanged(0.dp) }
        return
    }

    val context = LocalContext.current

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SplitEaseColors.Surface)
                .padding(horizontal = horizontalPadding),
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val resolvedAdSize =
            when (size) {
                is SeBannerAdSize.Anchored ->
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        adWidthDp,
                    )
                is SeBannerAdSize.Inline -> {
                    val maxHeight = (size.maxHeightDp ?: 100).coerceAtLeast(50)
                    AdSize.getInlineAdaptiveBannerAdSize(adWidthDp, maxHeight)
                }
            }

        // Recreate AdView when width/orientation changes; AdSize cannot be updated in place.
        key(adUnitId, size, adWidthDp, resolvedAdSize) {
            var loadFailed by remember { mutableStateOf(false) }
            var loadedHeightDp by remember { mutableStateOf(0) }
            val displayHeightDp = when {
                loadFailed -> 0
                loadedHeightDp > 0 -> loadedHeightDp
                else -> resolvedAdSize.height.coerceAtLeast(50)
            }

            val currentOnHeightChanged = rememberUpdatedState(onHeightChanged)
            LaunchedEffect(displayHeightDp, loadFailed, showBottomDivider) {
                val height =
                    if (loadFailed || displayHeightDp <= 0) {
                        0.dp
                    } else {
                        displayHeightDp.dp + 8.dp + if (showBottomDivider) 9.dp else 0.dp
                    }
                currentOnHeightChanged.value(height)
            }

            if (!loadFailed) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(displayHeightDp.dp),
                    ) {
                        AndroidView(
                            factory = {
                                AdView(context).apply {
                                    this.adUnitId = adUnitId
                                    setAdSize(resolvedAdSize)
                                    layoutParams =
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                        )
                                    adListener =
                                        object : AdListener() {
                                            override fun onAdLoaded() {
                                                loadFailed = false
                                                loadedHeightDp =
                                                    adSize?.getHeight()?.takeIf { it > 0 }
                                                        ?: resolvedAdSize.height.coerceAtLeast(50)
                                            }

                                            override fun onAdFailedToLoad(error: LoadAdError) {
                                                loadFailed = true
                                                loadedHeightDp = 0
                                            }
                                        }
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            onRelease = { adView ->
                                adView.destroy()
                            },
                        )
                    }
                    if (showBottomDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = SplitEaseColors.Outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeBannerAdPlaceholder(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SeLayout.detailHorizontal,
    tall: Boolean = false,
    showBottomDivider: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (tall) Modifier.fillMaxSize() else Modifier)
                .background(SplitEaseColors.Surface)
                .padding(horizontal = horizontalPadding),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .then(if (tall) Modifier.weight(1f) else Modifier.height(50.dp))
                    .background(SplitEaseColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ad_preview_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = SplitEaseColors.NavyMuted,
            )
        }
        if (showBottomDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = SplitEaseColors.Outline,
            )
        }
    }
}
