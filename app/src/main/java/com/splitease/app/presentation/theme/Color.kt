package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * SplitEase brand palette. [IndigoLight] (primary) and [AmberLight] (secondary /
 * accent) are the only authored seeds. All colours are explicit hex values. Do not derive colours at runtime.
 */

// ============================================================
// BRAND SEEDS & SURFACE COLORS
// ============================================================

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoLight = Color(0xFF4F46E5) // primary brand

/** Accent amber — highlights, "pending" states, home-group warmth. */
val AmberLight = Color(0xFFFFA008) // accent brand

// --- Light theme ---

/** Screen canvas — indigo washed almost to white. */
val BackgroundLight = Color(0xFFFBFCFF) // light screen canvas

/** Soft indigo fill for selected / muted brand accents (not screen backgrounds). */
val PrimaryContainerLight = Color(0xFFE6EAFF) // light primary container

/** Cards, sheets, dialogs. */
val SurfaceLight = Color.White // light cards and sheets

/** Grouped rows, unfocused fields, chip idle fills. */
val SurfaceMutedLight = Color(0xFFF1F3FF) // light muted surface

/** Pastel detail-header banners. */
val BannerFriendsLight = Color(0xFFC3CBFC) // light friends banner
val BannerHomeLight = Color(0xFFFFE9D1) // light home banner
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
val BannerHomeDark = Color(0xFFC37D2B) // dark home banner
val BannerOtherDark = Color(0xFF49509E) // dark other banner

/** Friend-detail header banner in dark theme. */
val FriendDetailBannerDark = Color(0xFF747CF0) // dark friend-detail banner

/**
 * Non-group expenses header banner in dark theme.
 * Keeps the previous rendered colour, which was mixed with the light-theme text colour.
 * That mix may be unintended.
 */
val NonGroupBannerDark = Color(0xFF332C9B) // dark non-group banner

// --- Semantic balance ---

/** "You owe" / error — rose that stays readable on pale fills. */
val OweRed = Color(0xFFC43D5A) // you-owe balance

/** Error / you-owe container. */
val OweContainer = Color(0xFFFDE8EC) // you-owe container

/** "You're owed" / positive — teal. */
val OwedTeal = Color(0xFF1B8A6B) // you're-owed balance

/** Positive container. */
val OwedContainer = Color(0xFFDDF6EE) // positive container

// ============================================================
// TEXT & SEPARATOR COLORS
// To change app-wide text or divider/outline colors, edit ONLY
// the values below. See theme/SeText.kt for which named text
// style uses which of these.
// ============================================================

/** Body/heading text on light backgrounds. */
val TextPrimaryLight = Color(0xFF060424) // light primary text

/** Captions, hints, timestamps, muted labels (light theme). */
val TextSecondaryLight = Color(0xFF575F9E) // light secondary text

/** Body/heading text on dark backgrounds. */
val TextPrimaryDark = Color(0xFFEFF1FF) // dark primary text

/** Captions, hints, timestamps, muted labels (dark theme). */
val TextSecondaryDark = Color(0xFFBFC8FC) // dark secondary text

/** Resting borders. */
val OutlineLight = Color(0xFFDAE0FE) // light resting border

/** Hairline / card edges. */
val OutlineVariantLight = Color(0xFFEBEEFF) // light hairline

/** Resting borders (dark theme). */
val OutlineDark = Color(0xFF4B465C) // dark resting border

/** Hairline / card edges (dark theme). */
val OutlineVariantDark = Color(0xFF3A3552) // dark hairline
