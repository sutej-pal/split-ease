# Design Tokens

Canonical **visual** design tokens for SplitEase. Schema / Room entities remain in [data-dictionary.md](data-dictionary.md).

Brand colors are tied to the app icon (two-tone indigo receipt with amber divider). Change hex values only together with icon/design updates.

Source of truth in code: `presentation/theme/Color.kt` → `Theme.kt` `ColorScheme`.

## Light theme

| Role | Hex | Compose val | Use |
|---|---|---|---|
| Primary (indigo) | `#4F46E5` | `IndigoLight` | Panel outlines, primary buttons, active states, icon strokes |
| Accent (amber) | `#FFA008` | `AmberLight` | Divider, CTAs, highlights, "pending" states |
| Background tint | `#E8EAFE` | `BackgroundLight` | Screen backgrounds, card fills, subtle sections |
| Surface | `#FFFFFF` | `SurfaceLight` | Cards, sheets, input fields |
| Text primary | `#1E1B4B` | `TextPrimaryLight` | Body/heading text on light backgrounds |

## Dark theme

| Role | Hex | Compose val | Use |
|---|---|---|---|
| Primary (indigo) | `#818CF8` | `IndigoDark` | Panel outlines, primary buttons, active states, icon strokes |
| Accent (amber) | `#FFB020` | `AmberDark` | Divider, CTAs, highlights, "pending" states |
| Background | `#14121F` | `BackgroundDark` | Screen backgrounds |
| Surface | `#201C33` | `SurfaceDark` | Cards, sheets, input fields |
| Text primary | `#ECEAFB` | `TextPrimaryDark` | Body/heading text on dark backgrounds |
| Text secondary | `#B4AFC7` | `TextSecondaryDark` | Captions, hints, timestamps, muted labels |

## Material 3 role mapping

| Material role | Light | Dark |
|---|---|---|
| `primary` | IndigoLight | IndigoDark |
| `onPrimary` | White | BackgroundDark |
| `tertiary` (accent) | AmberLight | AmberDark |
| `background` | BackgroundLight | BackgroundDark |
| `surface` | SurfaceLight | SurfaceDark |
| `onBackground` / `onSurface` | TextPrimaryLight | TextPrimaryDark |
| `onSurfaceVariant` | soft indigo-gray (interim) | TextSecondaryDark |
| `error` / `errorContainer` | PLACEHOLDER ("you owe") | PLACEHOLDER |
| `positive` / `positiveContainer` | PLACEHOLDER custom vals (not in `ColorScheme`) | same |

## Semantic balance colors (not finalized)

`error` / `ErrorPlaceholder` and `PositivePlaceholder` / `PositiveContainerPlaceholder` are **labeled placeholders** only. Confirm before shipping.
