package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * SplitEase brand palette. [IndigoLight] (primary) and [AmberLight] (secondary /
 * accent) are the only authored seeds. All colors are explicit hex values. Do not derive colors at runtime.
 */

// ============================================================
// BRAND SEEDS & SURFACE COLORS
// ============================================================

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoLight = Color(0xFF4F46E5) // primary brand

/** Accent amber — highlights, "pending" states, home-group warmth. */
val AmberLight = Color(0xFFFFA008) // accent brand

// --- Light theme ---

/** Screen canvas. */
val BackgroundLight = Color(0xFFFFFFFF) // light screen canvas

/** Soft indigo fill for selected / muted brand accents (not screen backgrounds). */
val PrimaryContainerLight = Color(0xFFE6EAFF) // light primary container

/** Cards, sheets, dialogs. */
val SurfaceLight = Color.White // light cards and sheets

/** Grouped rows, unfocused fields, chip idle fills. */
val SurfaceMutedLight = Color(0xFFF1F3FF) // light muted surface

/** Pastel detail-header banners. */
val BannerFriendsLight = Color(0xFFC3CBFC) // light friends banner
val BannerHomeLight = PrimaryContainerLight // light home banner (very light shade of primary)
val BannerOtherLight = Color(0xFFC3CBFC) // light other banner

// --- Dark theme ---

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoDark = Color(0xFF7781F1) // dark primary brand

/** Accent amber — divider, CTAs, highlights, "pending" states. */
val AmberDark = Color(0xFFFFAE4C) // dark accent brand

/** Screen backgrounds. */
val BackgroundDark = Color(0xFF000004) // dark screen canvas

/** Cards, sheets, input fields (one step lighter than background). */
val SurfaceDark = Color(0xFF030217) // dark cards and sheets

/** Friends, home, and other group banners in dark theme. */
val BannerFriendsDark = Color(0xFF3D36B7) // dark friends banner
val BannerHomeDark = Color(0xFF1E1B4B) // dark home banner (dark shade of primary)
val BannerOtherDark = Color(0xFF49509E) // dark other banner

/** Friend-detail header banner in dark theme. */
val FriendDetailBannerDark = Color(0xFF747CF0) // dark friend-detail banner

/**
 * Non-group expenses header banner in dark theme.
 * Keeps the previous rendered color, which was mixed with the light-theme text color.
 * That mix may be unintended.
 */
val NonGroupBannerDark = Color(0xFF332C9B) // dark non-group banner

// --- Semantic balance ---

/** "You owe" — rose balance color. Not used for error text. */
val OweRed = Color(0xFFC43D5A) // you-owe balance

/** You-owe container. Also, the light-theme error container. */
val OweContainer = Color(0xFFFDE8EC) // you-owe container

/** "You're owed" / positive — teal. */
val OwedTeal = Color(0xFF1B8A6B) // you're-owed balance

/** Positive container. */
val OwedContainer = Color(0xFFDDF6EE) // positive container

/**
 * Error text and icons in light theme.
 * Contrast: 6.54:1 on BackgroundLight, 5.92:1 on SurfaceMutedLight.
 */
val ErrorLight = Color(0xFFB3261E) // light error

/**
 * Error text and icons in dark theme.
 * Contrast: 9.18:1 on BackgroundDark, 8.98:1 on SurfaceDark.
 */
val ErrorDark = Color(0xFFFF8A80) // dark error

// ============================================================
// TEXT & SEPARATOR COLORS
// To change app-wide text or divider/outline colors, edit ONLY
// the values below. See theme/SeText.kt for which named text
// style uses which of these.
// ============================================================

/** Body/heading text on light backgrounds. */
val TextPrimaryLight = Color(0xFF060424) // light primary text

/** Captions, hints, timestamps, muted labels (light theme). */
val TextSecondaryLight = Color(0xFF3A3F69) // light secondary text

/** Body/heading text on dark backgrounds. */
val TextPrimaryDark = Color(0xFFEFF1FF) // dark primary text

/** Captions, hints, timestamps, muted labels (dark theme). */
val TextSecondaryDark = Color(0xFFBFC8FC) // dark secondary text

/** Resting borders. */
val OutlineLight = Color(0xFFDAE0FE) // light resting border

/** Hairline / card edges. */
val OutlineVariantLight = Color(0xFFEBEEFF) // light hairline

/** Empty-state card background (light theme). */
val EmptyStateTintLight = Color(0xFFF0F1FF)

/** Empty-state card border (light theme). */
val EmptyStateBorderLight = Color(0xFFE1E4F8)

/** Empty-state card border (dark theme). */
val EmptyStateBorderDark = Color(0xFF2E2A45)

/** Empty-state hint text (light theme). */
val EmptyStateHintLight = Color(0xFF5B5880)

/** Empty-state secondary button border (light theme). */
val EmptyStateSecondaryBorderLight = Color(0xFFC9CDF5)

/** Empty-state secondary button text (light theme). */
val EmptyStateSecondaryTextLight = Color(0xFF4338CA)

/** Resting borders (dark theme). */
val OutlineDark = Color(0xFF4B465C) // dark resting border

/** Hairline / card edges (dark theme). */
val OutlineVariantDark = Color(0xFF3A3552) // dark hairline

/** Default icon color app-wide. */
val IconDefault = Color(0xFF353B3E)
