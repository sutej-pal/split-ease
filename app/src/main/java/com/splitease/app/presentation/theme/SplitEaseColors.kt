package com.splitease.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val bannerFriendsDarkToken = BannerFriendsDark
private val bannerHomeDarkToken = BannerHomeDark
private val bannerOtherDarkToken = BannerOtherDark
private val iconDefaultLightToken = IconDefault

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

    /**
     * Body behind [com.splitease.app.presentation.ui.SeContentCard].
     * Light: a slight dark shade of primary. Dark: a slight primary shade above the canvas.
     */
    @get:Composable
    @get:ReadOnlyComposable
    val ContentArea: Color
        get() = if (darkChrome()) ContentAreaDark else ContentAreaLight

    /** Fill for [com.splitease.app.presentation.ui.SeContentCard]. */
    @get:Composable
    @get:ReadOnlyComposable
    val ContentCard: Color
        get() = if (darkChrome()) ContentCardDark else SurfaceLight

    /** Soft ambient shadow for [com.splitease.app.presentation.ui.SeContentCard]. */
    @get:Composable
    @get:ReadOnlyComposable
    val ContentCardShadowAmbient: Color
        get() = if (darkChrome()) CardShadowAmbientDark else CardShadowAmbientLight

    /** Soft spot shadow for [com.splitease.app.presentation.ui.SeContentCard]. */
    @get:Composable
    @get:ReadOnlyComposable
    val ContentCardShadowSpot: Color
        get() = if (darkChrome()) CardShadowSpotDark else CardShadowSpotLight

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

    /** "You owe" container fill (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val YouOweContainer: Color
        get() = if (darkChrome()) OweContainerDark else OweContainer

    /** "You're owed" / positive. */
    val OwedToYou = OwedTeal

    /** "You're owed" container fill (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val OwedToYouContainer: Color
        get() = if (darkChrome()) OwedContainerDark else OwedContainer

    @get:Composable
    @get:ReadOnlyComposable
    val Settled: Color
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** Default icon tint (theme-aware so icons stay readable in dark mode). */
    @get:Composable
    @get:ReadOnlyComposable
    val IconDefault: Color
        get() = if (darkChrome()) IconDefaultDark else iconDefaultLightToken

    /** Glyph tint on light pastel category tiles ([CategoryPastels]). */
    val CategoryGlyph: Color = iconDefaultLightToken

    /** Pastel category icon tile palette. */
    val CategoryPastels: List<Color> = CategoryPastelPalette

    // Group type tiles (glyph color; [SeIconTile] washes these into a pastel fill)
    val IconFriends = IndigoLight
    val IconHome = AmberLight
    val IconOther = IconOtherGroup // other-group glyph

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

    /** Switch unchecked track (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val SwitchUncheckedTrack: Color
        get() = if (darkChrome()) SwitchUncheckedTrackDark else SwitchUncheckedTrackLight

    /** Switch unchecked border (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val SwitchUncheckedBorder: Color
        get() = if (darkChrome()) SwitchUncheckedBorderDark else SwitchUncheckedBorderLight

    /** Pending-invite banner card background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val PendingInviteCard: Color
        get() = if (darkChrome()) PendingInviteCardDark else PendingInviteCardLight

    /** Empty-state card background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateTint: Color
        get() = if (darkChrome()) SurfaceDark else EmptyStateTintLight

    /** Empty-state card border (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateBorder: Color
        get() = if (darkChrome()) EmptyStateBorderDark else EmptyStateBorderLight

    /** Empty-state icon circle background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateIconBg: Color
        get() = if (darkChrome()) BackgroundDark else SurfaceLight

    /** Empty-state icon tint (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateIconTint: Color
        get() = if (darkChrome()) IndigoDark else IndigoLight

    /** Empty-state title color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateTitle: Color
        get() = if (darkChrome()) TextPrimaryDark else EmptyStateTitleLight

    /** Empty-state hint color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateHint: Color
        get() = if (darkChrome()) TextSecondaryDark else EmptyStateHintLight

    /** Empty-state secondary button background (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryBg: Color
        get() = if (darkChrome()) Color.Transparent else SurfaceLight

    /** Empty-state secondary button border (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryBorder: Color
        get() = if (darkChrome()) TextPrimaryDark.copy(alpha = 0.25f) else EmptyStateSecondaryBorderLight

    @Composable
    @ReadOnlyComposable
    private fun darkChrome(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** Empty-state secondary button text (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateSecondaryText: Color
        get() = if (darkChrome()) IndigoDark else EmptyStateSecondaryTextLight

    /** Empty-state shadow color (theme-aware). */
    @get:Composable
    @get:ReadOnlyComposable
    val EmptyStateShadowColor: Color
        get() = if (darkChrome()) Color.Black.copy(alpha = 0.30f) else IndigoLight.copy(alpha = 0.12f)
}
