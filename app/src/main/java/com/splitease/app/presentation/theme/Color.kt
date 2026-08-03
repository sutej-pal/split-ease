package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand-approved SplitEase colors derived from the app icon (two-tone indigo receipt
 * with amber divider). These hex values are intentional design tokens — do not change
 * them without updating the icon and design docs in tandem.
 *
 * Semantic balance-state colors ("you owe" / "you're owed") are **not** finalized;
 * see the PLACEHOLDER vals and TODOs below.
 */

// --- Light theme ---

/** Primary indigo — panel outlines, primary buttons, active states, icon strokes. */
val IndigoLight = Color(0xFF4F46E5)

/** Accent amber — divider, CTAs, highlights, "pending" states. */
val AmberLight = Color(0xFFFFA008)

/** Screen backgrounds. */
val BackgroundLight = Color(0xFFFFFFFF)

/** Soft indigo fill for selected / muted brand accents (not screen backgrounds). */
val PrimaryContainerLight = Color(0xFFE8EAFE)

/** Cards, sheets, input fields. */
val SurfaceLight = Color(0xFFFFFFFF)

/** Body/heading text on light backgrounds. */
val TextPrimaryLight = Color(0xFF1E1B4B)

// --- Dark theme ---

/** Primary indigo — panel outlines, primary buttons, active states, icon strokes. */
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

// --- Semantic balance PLACEHOLDERS (not brand-approved) ---

// TODO(design): Confirm "you owe" / "you're owed" / pending semantic colors before shipping.
// These reuse desaturated Material-ish red/green as labeled placeholders only.

/** PLACEHOLDER — "you owe" / error. Confirm before shipping. */
val ErrorPlaceholder = Color(0xFFB3261E)

/** PLACEHOLDER — error container. Confirm before shipping. */
val ErrorContainerPlaceholder = Color(0xFFF9DEDC)

/** PLACEHOLDER — "you're owed" / positive. Confirm before shipping. */
val PositivePlaceholder = Color(0xFF386A20)

/** PLACEHOLDER — positive container. Confirm before shipping. */
val PositiveContainerPlaceholder = Color(0xFFB7F397)
