package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * SplitEase brand tokens inspired by the [Apzo SaaS](https://demo.goodlayers.com/apzo/saas/)
 * light marketing palette: electric blue primary, soft gray surfaces, navy copy.
 */
object SplitEaseColors {
    // Apzo-inspired brand
    val Primary = Color(0xFF2F57EF)
    val PrimaryDark = Color(0xFF2446C7)
    val PrimarySoft = Color(0xFFE8EDFF)
    val Secondary = Color(0xFF5B6CFF)
    val Accent = Color(0xFFFF6A5A)
    val AccentSoft = Color(0xFFFFE8E5)

    val Navy = Color(0xFF1B1F3B)
    val NavyMuted = Color(0xFF6B728A)
    val Background = Color(0xFFF5F7FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF0F2F8)
    val Outline = Color(0xFFE2E6F0)
    val OutlineStrong = Color(0xFFC8CEDC)

    // Semantic money (kept distinct from brand for scanability)
    val YouOwe = Color(0xFFE85D4C)
    val OwedToYou = Color(0xFF1FA97A)
    val Settled = Color(0xFF8A94A6)

    // Group type tiles
    val IconFriends = Color(0xFF2F57EF)
    val IconHome = Color(0xFFFF8A65)
    val IconOther = Color(0xFF5B6CFF)

    // Dark shell (optional / system dark)
    val ShellBackground = Color(0xFF0E1224)
    val ShellSurface = Color(0xFF171C31)
}
