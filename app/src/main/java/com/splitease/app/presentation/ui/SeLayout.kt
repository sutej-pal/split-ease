package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Screen layout rhythm for SplitEase.
 *
 * Secondary screens (back + title) must use [SeScreen] (or [SeTopBar] inside a
 * Scaffold) so chevron + title placement stays identical. Prefer
 * [seScreenTitleStyle] over raw `headline*` / `title*` picks in screen headers.
 *
 * List / balance / totals body content should use [detailHorizontal] (16dp) so it
 * matches group-detail rows. Detail banners are a separate chrome pattern.
 */
object SeLayout {
    /** Horizontal inset for form-style bodies (auth, settings). */
    val screenHorizontal: Dp = 20.dp

    /**
     * Canonical content margin for group-detail-style screens: Activity, Balances,
     * Totals, ledger rows, chips. Material 3 default 16dp each side.
     */
    val detailHorizontal: Dp = 16.dp

    /** Vertical padding below the top app bar before body content. */
    val screenTop: Dp = 8.dp

    /** Bottom padding for scrollable screen bodies. */
    val screenBottom: Dp = 24.dp

    /** Gap between screen title and subtitle. */
    val titleToSubtitle: Dp = 8.dp

    /** Gap between header block (title/subtitle) and first body section. */
    val headerToContent: Dp = 16.dp

    /** Default vertical gap between stacked form fields / sections. */
    val sectionGap: Dp = 16.dp

    /** Tighter gap between related rows (list items, chips). */
    val itemGap: Dp = 8.dp

    /** Extra space above primary CTAs at the bottom of a form. */
    val ctaTopGap: Dp = 20.dp
}

/**
 * Applies [SeLayout.detailHorizontal] — the same 16dp side margin as group-detail rows.
 */
fun Modifier.seDetailHorizontal(): Modifier =
    this.padding(horizontal = SeLayout.detailHorizontal)

/**
 * Canonical text style for secondary-screen titles (app bar / [SeTopBar] chrome).
 *
 * Uses Material [Typography.titleLarge] (22sp) so titles are consistent — not a mix
 * of `headlineMedium` and `titleMedium`. [includeFontPadding] is off so the glyph
 * centers optically beside the back chevron in [SeTopBar].
 */
@Composable
@ReadOnlyComposable
fun seScreenTitleStyle(): TextStyle =
    MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
    )

/**
 * Canonical subtitle style under a screen title.
 */
@Composable
@ReadOnlyComposable
fun seScreenSubtitleStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
