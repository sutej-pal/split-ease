package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Convenience aliases for screens and `Se*` components.
 *
 * Canonical brand hex values live in [Color.kt]. Prefer
 * `MaterialTheme.colorScheme` for role-based colors when possible.
 *
 * Semantic money colors ([YouOwe], [OwedToYou]) are **PLACEHOLDERS** — see TODOs.
 */
object SplitEaseColors {
    // Brand (aliases → Color.kt)
    val Primary = IndigoLight
    val PrimaryDark = IndigoDark
    val PrimarySoft = BackgroundLight
    val Secondary = IndigoLight
    val Accent = AmberLight
    val AccentSoft = Color(0xFFFFE8C2)

    val Navy = TextPrimaryLight
    val NavyMuted = Color(0xFF5C5878) // light muted text; not in brand table yet
    val Background = BackgroundLight
    val Surface = SurfaceLight
    val SurfaceMuted = BackgroundLight
    val Outline = Color(0xFFC5C7E8)
    val OutlineStrong = IndigoLight

    // TODO(design): Confirm semantic balance colors before shipping.
    /** PLACEHOLDER — "you owe". */
    val YouOwe = ErrorPlaceholder
    /** PLACEHOLDER — "you're owed" / positive. */
    val OwedToYou = PositivePlaceholder
    val Settled = TextSecondaryDark

    // Group type tiles (tint accents; may revisit with brand)
    val IconFriends = IndigoLight
    val IconHome = AmberLight
    val IconOther = IndigoDark

    // Dark shell aliases
    val ShellBackground = BackgroundDark
    val ShellSurface = SurfaceDark

    // Explicit positive pair for callers that need containers
    /** PLACEHOLDER — positive fill. Confirm before shipping. */
    val Positive = PositivePlaceholder
    /** PLACEHOLDER — positive container. Confirm before shipping. */
    val PositiveContainer = PositiveContainerPlaceholder
}
