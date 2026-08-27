package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * SplitEase brand palette: indigo + amber from the app icon, tuned for an airy
 * light UI. Semantic balance-state colors ("you owe" / "you're owed") use soft
 * rose and teal so they stay readable on pale surfaces.
 */

// --- Light theme ---

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoLight = Color(0xFF4F46E5)

/** Accent amber — highlights, "pending" states, home-group warmth. */
val AmberLight = Color(0xFFFFA008)

/** Screen canvas — lavender-tinted off-white, not stark paper. */
val BackgroundLight = Color(0xFFF6F5FC)

/** Soft indigo fill for selected / muted brand accents (not screen backgrounds). */
val PrimaryContainerLight = Color(0xFFE4E3FD)

/** Cards, sheets, dialogs. */
val SurfaceLight = Color(0xFFFFFFFF)

/** Grouped rows, unfocused fields, chip idle fills. */
val SurfaceMutedLight = Color(0xFFEFEEF8)

/** Body/heading text on light backgrounds. */
val TextPrimaryLight = Color(0xFF1A1840)

/** Captions, hints, timestamps, muted labels (light theme). */
val TextSecondaryLight = Color(0xFF6B6790)

/** Resting borders. */
val OutlineLight = Color(0xFFD9D6E8)

/** Hairline / card edges. */
val OutlineVariantLight = Color(0xFFECEAF4)

/** Pastel detail-header banners. */
val BannerFriendsLight = Color(0xFFDDDCFC)
val BannerHomeLight = Color(0xFFFFECD0)
val BannerOtherLight = Color(0xFFE6E4F4)

// --- Dark theme ---

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoDark = Color(0xFF818CF8)

/** Accent amber — divider, CTAs, highlights, "pending" states. */
val AmberDark = Color(0xFFFFB020)

/** Screen backgrounds. */
val BackgroundDark = Color(0xFF14121F)

/** Cards, sheets, input fields (one step lighter than background). */
val SurfaceDark = Color(0xFF201C33)

/** Body/heading text on dark backgrounds. */
val TextPrimaryDark = Color(0xFFECEAFB)

/** Captions, hints, timestamps, muted labels (dark theme). */
val TextSecondaryDark = Color(0xFFB4AFC7)

// --- Semantic balance ---

/** "You owe" / error — rose that stays readable on pale fills. */
val ErrorPlaceholder = Color(0xFFC43D5A)

/** Error / you-owe container. */
val ErrorContainerPlaceholder = Color(0xFFFDE8EC)

/** "You're owed" / positive — teal. */
val PositivePlaceholder = Color(0xFF1B8A6B)

/** Positive container. */
val PositiveContainerPlaceholder = Color(0xFFDDF6EE)
