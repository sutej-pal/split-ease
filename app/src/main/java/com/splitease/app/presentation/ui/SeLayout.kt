package com.splitease.app.presentation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Screen layout rhythm for SplitEase.
 *
 * Secondary screens (back + title) should use [SeScreen] / [SeTopBar] and these
 * spacings so typography and gaps stay consistent. Prefer [SeScreenTitleStyle]
 * over raw `headline*` / `title*` picks in screen headers.
 */
object SeLayout {
    /** Horizontal inset for standard screen body content. */
    val screenHorizontal: Dp = 24.dp

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
 * Canonical text style for secondary-screen titles (app bar / back+title chrome).
 *
 * Uses Material [Typography.titleLarge] (22sp) so titles are consistent — not a mix
 * of `headlineMedium` and `titleMedium`.
 */
@Composable
@ReadOnlyComposable
fun SeScreenTitleStyle(): TextStyle =
    MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
    )

/**
 * Canonical subtitle style under a screen title.
 */
@Composable
@ReadOnlyComposable
fun SeScreenSubtitleStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
