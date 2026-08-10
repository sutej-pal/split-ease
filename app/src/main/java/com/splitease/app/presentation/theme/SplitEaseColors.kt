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
    @get:Composable
    @get:ReadOnlyComposable
    val Primary: Color
        get() = MaterialTheme.colorScheme.primary

    @get:Composable
    @get:ReadOnlyComposable
    val PrimaryDark: Color
        get() = MaterialTheme.colorScheme.primary

    @get:Composable
    @get:ReadOnlyComposable
    val PrimarySoft: Color
        get() = MaterialTheme.colorScheme.primaryContainer

    @get:Composable
    @get:ReadOnlyComposable
    val Secondary: Color
        get() = MaterialTheme.colorScheme.secondary

    @get:Composable
    @get:ReadOnlyComposable
    val Accent: Color
        get() = MaterialTheme.colorScheme.tertiary

    /** Body / heading text on background and surface. */
    @get:Composable
    @get:ReadOnlyComposable
    val Navy: Color
        get() = MaterialTheme.colorScheme.onSurface

    /** Captions, hints, muted labels. */
    @get:Composable
    @get:ReadOnlyComposable
    val NavyMuted: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    @get:Composable
    @get:ReadOnlyComposable
    val Background: Color
        get() = MaterialTheme.colorScheme.background

    @get:Composable
    @get:ReadOnlyComposable
    val Surface: Color
        get() = MaterialTheme.colorScheme.surface

    @get:Composable
    @get:ReadOnlyComposable
    val SurfaceMuted: Color
        get() = MaterialTheme.colorScheme.surfaceVariant

    @get:Composable
    @get:ReadOnlyComposable
    val Outline: Color
        get() = MaterialTheme.colorScheme.outlineVariant

    @get:Composable
    @get:ReadOnlyComposable
    val OutlineStrong: Color
        get() = MaterialTheme.colorScheme.outline

    // TODO(design): Confirm semantic balance colors before shipping.
    /** PLACEHOLDER — "you owe". */
    val YouOwe = ErrorPlaceholder

    /** PLACEHOLDER — "you're owed" / positive. */
    val OwedToYou = PositivePlaceholder

    @get:Composable
    @get:ReadOnlyComposable
    val Settled: Color
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
}
