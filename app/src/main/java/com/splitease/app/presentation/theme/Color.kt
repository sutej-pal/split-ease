package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * SplitEase brand palette. [IndigoLight] (primary) and [AmberLight] (secondary /
 * accent) are the only authored seeds; every other token is mixed from those
 * toward white or black so banners, fills, and chrome stay on-brand.
 */

// --- Brand seeds ---

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoLight = Color(0xFF4F46E5)

/** Accent amber — highlights, "pending" states, home-group warmth. */
val AmberLight = Color(0xFFFFA008)

private fun Color.wash(amount: Float): Color = lerp(this, Color.White, amount)

private fun Color.shade(amount: Float): Color = lerp(this, Color.Black, amount)

/** Soft indigo fill used on Friends + Other group banners and the friends header. */
private val IndigoBannerWash = IndigoLight.wash(0.70f)

// --- Light theme (derived from seeds) ---

/** Screen canvas — indigo washed almost to white. */
val BackgroundLight = IndigoLight.wash(0.96f)

/** Soft indigo fill for selected / muted brand accents (not screen backgrounds). */
val PrimaryContainerLight = IndigoLight.wash(0.88f)

/** Cards, sheets, dialogs. */
val SurfaceLight = Color.White

/** Grouped rows, unfocused fields, chip idle fills. */
val SurfaceMutedLight = IndigoLight.wash(0.93f)

/** Body/heading text on light backgrounds. */
val TextPrimaryLight = IndigoLight.shade(0.72f)

/** Captions, hints, timestamps, muted labels (light theme). */
val TextSecondaryLight = lerp(IndigoLight.wash(0.40f), Color.Black, 0.28f)

/** Resting borders. */
val OutlineLight = IndigoLight.wash(0.82f)

/** Hairline / card edges. */
val OutlineVariantLight = IndigoLight.wash(0.90f)

/** Pastel detail-header banners. */
val BannerFriendsLight = IndigoBannerWash
val BannerHomeLight = AmberLight.wash(0.76f)
val BannerOtherLight = IndigoBannerWash

// --- Dark theme (derived from the same seeds) ---

/** Primary indigo — CTAs, focused fields, links, and other brand accents. */
val IndigoDark = IndigoLight.wash(0.28f)

/** Accent amber — divider, CTAs, highlights, "pending" states. */
val AmberDark = AmberLight.wash(0.14f)

/** Screen backgrounds. */
val BackgroundDark = IndigoLight.shade(0.88f)

/** Cards, sheets, input fields (one step lighter than background). */
val SurfaceDark = IndigoLight.shade(0.78f)

/** Body/heading text on dark backgrounds. */
val TextPrimaryDark = IndigoLight.wash(0.92f)

/** Captions, hints, timestamps, muted labels (dark theme). */
val TextSecondaryDark = IndigoLight.wash(0.68f)

// --- Semantic balance ---

/** "You owe" / error — rose that stays readable on pale fills. */
val ErrorPlaceholder = Color(0xFFC43D5A)

/** Error / you-owe container. */
val ErrorContainerPlaceholder = Color(0xFFFDE8EC)

/** "You're owed" / positive — teal. */
val PositivePlaceholder = Color(0xFF1B8A6B)

/** Positive container. */
val PositiveContainerPlaceholder = Color(0xFFDDF6EE)
