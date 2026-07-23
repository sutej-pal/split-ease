# Phase 0b — Brand Theme System

## Phase Goal

Wire the finalized icon-derived brand palette into a hand-authored Material 3 `ColorScheme` (light + dark), expose it via `SplitEaseTheme`, and document tokens + contrast checks.

## Scope

### In

- `presentation/theme/Color.kt` brand vals (exact approved hex)
- `Theme.kt` light/dark `ColorScheme` + system bar appearance
- Align `SplitEaseColors` aliases to the new tokens (so existing `Se*` call sites pick up brand hues)
- Contrast (WCAG AA) verification for approved text/background pairs
- Docs: this file, `ARCHITECTURE.md` Theming, `CHANGELOG.md`, `docs/design-tokens.md`

### Out

- Custom fonts / overhauled typography (existing `Type.kt` kept)
- Migrating every screen off hardcoded `SplitEaseColors.*` to `MaterialTheme.colorScheme` (follow-up)
- Final semantic "you owe" / "you're owed" / pending colors (placeholders only)
- Enabling Material You dynamic color by default

## Architecture Decisions

1. **Hand-authored `ColorScheme`** — brand indigo/amber are fixed; do not substitute Material tonal palette generation for those roles.
2. **Dynamic color opt-in only** — `SplitEaseTheme(dynamicColor = false)` by default; an Appearance settings toggle may expose it later, never as the default.
3. **Accent → `tertiary`** — amber maps to Material `tertiary` so primary stays indigo.
4. **Design tokens file** — color roles live in [design-tokens.md](design-tokens.md), not `data-dictionary.md` (that file remains schema-only). Linked from `ARCHITECTURE.md`.
5. **System bars** — `SideEffect` + `WindowCompat` insets controller inside `SplitEaseTheme`; `MainActivity` keeps `enableEdgeToEdge` + `SystemBarStyle` for edge-to-edge.

## Data Model Changes

N/A — theme only; no Room/Supabase schema changes.

## Files Added/Modified

| File | Change |
|---|---|
| `presentation/theme/Color.kt` | **Added** — brand + placeholder semantic vals |
| `presentation/theme/Theme.kt` | **Modified** — light/dark schemes, system bars, KDoc |
| `presentation/theme/SplitEaseColors.kt` | **Modified** — aliases → `Color.kt` + positive placeholders |
| `presentation/theme/Type.kt` | Unchanged (already exists) |
| `MainActivity.kt` | Already wraps `SplitEaseTheme`; no change required |
| `docs/phase-0b-theme-system.md` | **Added** (this file) |
| `docs/design-tokens.md` | **Added** |
| `ARCHITECTURE.md` | **Modified** — Theming section |
| `CHANGELOG.md` | **Modified** — Unreleased entry |

## Screens/UI Added

N/A — no new screens; existing UI inherits via `SplitEaseTheme` / updated aliases.

## Contrast check results (WCAG 2.1)

Relative luminance / contrast computed with the standard sRGB WCAG formula. AA body text requires **4.5:1**.

| Pairing | Ratio | AA (4.5:1) |
|---|---|---|
| `TextPrimaryLight` (`#1E1B4B`) on `BackgroundLight` (`#E8EAFE`) | **13.42:1** | Pass |
| `TextPrimaryLight` on `SurfaceLight` (`#FFFFFF`) | **15.99:1** | Pass |
| `TextPrimaryDark` (`#ECEAFB`) on `BackgroundDark` (`#14121F`) | **15.61:1** | Pass |
| `TextPrimaryDark` on `SurfaceDark` (`#201C33`) | **13.90:1** | Pass |
| `IndigoDark` (`#818CF8`) on `BackgroundDark` (links / active labels) | **6.20:1** | Pass |
| White on `IndigoLight` (`onPrimary` light) | **6.29:1** | Pass |
| `BackgroundDark` on `IndigoDark` (`onPrimary` dark) | **6.20:1** | Pass |
| `TextPrimaryLight` on `AmberLight` (`onTertiary` light) | **7.83:1** | Pass |
| `AmberLight` on `BackgroundLight` (amber *as text*) | **1.71:1** | **Fail** — do not use amber for body text on light bg |
| White on `AmberLight` | **2.04:1** | **Fail** — avoid white label on amber fills |

Approved brand hex values were **not** altered to fix amber text contrast; usage guidance is documented instead.

## How to Test

1. Launch with system (or Settings → Appearance) set to **Light** — background `#E8EAFE`, surface white, primary indigo `#4F46E5`, accent amber `#FFA008`.
2. Switch to **Dark** — background `#14121F`, surface `#201C33`, primary `#818CF8`, accent `#FFB020`; status/nav bar icons flip to light-content.
3. Check body text on `background` and on a `surface` card in both themes for readability.
4. Grep for stray `Color(0x…)` outside theme files (see Known Issues).

```text
./gradlew :app:assembleDebug
```

## Known Issues / TODOs

- **Semantic balance colors** — `error` / `ErrorPlaceholder` and `PositivePlaceholder` / `PositiveContainerPlaceholder` are placeholders. **Confirm "you owe" / "you're owed" / pending colors before shipping.**
- **Light text-secondary** — not in the brand table; interim soft indigo-gray (`#5C5878`) used for `onSurfaceVariant` / `NavyMuted`. Confirm or add a light muted token.
- **Amber as text on light backgrounds** fails AA — use amber for dividers, fills, and icons; pair amber fills with `TextPrimaryLight`, not white.
- **Hardcoded colors outside theme** (grep):
  - `GroupSettingsScreen.kt` — `Color(0xFF7C4DFF)` invite chip
  - `LedgerEntryUi.kt` — pastel avatar background list
  - Derived containers in `Theme.kt` / `SplitEaseColors` (`#FFE8C2`, `#4A3400`, outline variants) are not brand-table tokens
- **`Se*` / screens still reference `SplitEaseColors` light aliases** (e.g. `Navy`) — dark theme `ColorScheme` applies to Material roles, but many components will stay “light-colored” until migrated to `MaterialTheme.colorScheme`.
- **Dynamic color** — remains available via `dynamicColor = true` for a future settings opt-in; default stays brand-fixed.

## Screenshots

_Placeholder — add light/dark Welcome + Groups home captures after visual QA._
