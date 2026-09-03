package com.splitease.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Convenience aliases for screens and `Se*` components.
 *
 * Theme-dependent roles resolve from [MaterialTheme.colorScheme] so light and dark
 * stay readable. Canonical brand hex values live in [Color.kt].
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

    /** "You owe". */
    val YouOwe = ErrorPlaceholder

    /** "You're owed" / positive. */
    val OwedToYou = PositivePlaceholder

    @get:Composable
    @get:ReadOnlyComposable
    val Settled: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Group type tiles (glyph color; [SeIconTile] washes these into a pastel fill)
    val IconFriends = IndigoLight
    val IconHome = AmberLight
    val IconOther = lerp(IndigoLight, Color.Gray, 0.38f)

    // Light detail-header banners
    val BannerFriends = BannerFriendsLight
    val BannerHome = BannerHomeLight
    val BannerOther = BannerOtherLight

    // Dark shell aliases (fixed dark tokens for forced-dark chrome)
    val ShellBackground = BackgroundDark
    val ShellSurface = SurfaceDark

    /** Positive fill. */
    val Positive = PositivePlaceholder
}
