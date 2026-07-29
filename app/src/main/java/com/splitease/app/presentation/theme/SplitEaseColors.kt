package com.splitease.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Convenience aliases for screens and `Se*` components.
 *
 * Theme-dependent roles resolve from [MaterialTheme.colorScheme] so light and dark
 * stay readable. Canonical brand hex values live in [Color.kt].
 *
 * Semantic money colors ([YouOwe], [OwedToYou]) are **PLACEHOLDERS** — see TODOs.
 */
object SplitEaseColors {
    // Brand / surface roles (theme-aware)
    val Primary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val PrimaryDark: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val PrimarySoft: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val Secondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    val Accent: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    val AccentSoft: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiaryContainer

    /** Body / heading text on background and surface. */
    val Navy: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    /** Captions, hints, muted labels. */
    val NavyMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val SurfaceMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val Outline: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val OutlineStrong: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    // TODO(design): Confirm semantic balance colors before shipping.
    /** PLACEHOLDER — "you owe". */
    val YouOwe = ErrorPlaceholder
    /** PLACEHOLDER — "you're owed" / positive. */
    val OwedToYou = PositivePlaceholder
    val Settled: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Group type tiles (tint accents; may revisit with brand)
    val IconFriends = IndigoLight
    val IconHome = AmberLight
    val IconOther = IndigoDark

    // Dark shell aliases (fixed dark tokens for forced-dark chrome)
    val ShellBackground = BackgroundDark
    val ShellSurface = SurfaceDark

    // Explicit positive pair for callers that need containers
    /** PLACEHOLDER — positive fill. Confirm before shipping. */
    val Positive = PositivePlaceholder
    /** PLACEHOLDER — positive container. Confirm before shipping. */
    val PositiveContainer = PositiveContainerPlaceholder
}
