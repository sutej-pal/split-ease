package com.splitease.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val bannerFriendsDarkToken = BannerFriendsDark
private val bannerHomeDarkToken = BannerHomeDark
private val bannerOtherDarkToken = BannerOtherDark

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
    val YouOwe = OweRed

    /** "You're owed" / positive. */
    val OwedToYou = OwedTeal

    @get:Composable
    @get:ReadOnlyComposable
    val Settled: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // Group type tiles (glyph color; [SeIconTile] washes these into a pastel fill)
    val IconFriends = IndigoLight
    val IconHome = AmberLight
    val IconOther = Color(0xFF5F68C5) // other-group glyph

    // Light detail-header banners
    val BannerFriends = BannerFriendsLight
    val BannerHome = BannerHomeLight
    val BannerOther = BannerOtherLight

    // Dark detail-header banners
    val BannerFriendsDark = bannerFriendsDarkToken
    val BannerHomeDark = bannerHomeDarkToken
    val BannerOtherDark = bannerOtherDarkToken

    // Dark shell aliases (fixed dark tokens for forced-dark chrome)
    val ShellBackground = BackgroundDark
    val ShellSurface = SurfaceDark

    /** Positive fill. */
    val Positive = OwedTeal

    /** Empty-state card background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateTint: Color
        get() = if (isSystemInDarkTheme()) SurfaceDark else EmptyStateTintLight

    /** Empty-state card border (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateBorder: Color
        get() = if (isSystemInDarkTheme()) EmptyStateBorderDark else EmptyStateBorderLight

    /** Empty-state icon circle background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateIconBg: Color
        get() = if (isSystemInDarkTheme()) BackgroundDark else Color.White

    /** Empty-state icon tint (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateIconTint: Color
        get() = if (isSystemInDarkTheme()) IndigoDark else IndigoLight

    /** Empty-state title color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateTitle: Color
        get() = if (isSystemInDarkTheme()) TextPrimaryDark else Color(0xFF1E1B4B)

    /** Empty-state hint color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateHint: Color
        get() = if (isSystemInDarkTheme()) TextSecondaryDark else EmptyStateHintLight

    /** Empty-state secondary button background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryBg: Color
        get() = if (isSystemInDarkTheme()) Color.Transparent else Color.White

    /** Empty-state secondary button border (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryBorder: Color
        get() = if (isSystemInDarkTheme()) TextPrimaryDark.copy(alpha = 0.25f) else EmptyStateSecondaryBorderLight

    /** Empty-state secondary button text (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryText: Color
        get() = if (isSystemInDarkTheme()) IndigoDark else EmptyStateSecondaryTextLight

    /** Empty-state shadow color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateShadowColor: Color
        get() = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.30f) else Color(0xFF4F46E5).copy(alpha = 0.12f)
}
