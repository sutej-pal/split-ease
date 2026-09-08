# Design Tokens

Canonical **visual** design tokens for SplitEase. Schema / Room entities remain in [data-dictionary.md](data-dictionary.md).

Brand colors are tied to the app icon (two-tone indigo receipt with amber divider). Change hex values only together with icon/design updates.

Source of truth in code: `presentation/theme/Color.kt` → `Theme.kt` `ColorScheme`.

## Light theme

| Role             | Hex       | Compose val        | Use                                                          |
| ---------------- | --------- | ------------------ | ------------------------------------------------------------ |
| Primary (indigo) | `#4F46E5` | `IndigoLight`      | Panel outlines, primary buttons, active states, icon strokes |
| Accent (amber)   | `#FFA008` | `AmberLight`       | Divider, CTAs, highlights, "pending" states                  |
| Background tint  | `#E8EAFE` | `BackgroundLight`  | Screen backgrounds, card fills, subtle sections              |
| Surface          | `#FFFFFF` | `SurfaceLight`     | Cards, sheets, input fields                                  |
| Text primary     | `#1E1B4B` | `TextPrimaryLight` | Body/heading text on light backgrounds                       |

## Dark theme

| Role             | Hex       | Compose val         | Use                                                          |
| ---------------- | --------- | ------------------- | ------------------------------------------------------------ |
| Primary (indigo) | `#818CF8` | `IndigoDark`        | Panel outlines, primary buttons, active states, icon strokes |
| Accent (amber)   | `#FFB020` | `AmberDark`         | Divider, CTAs, highlights, "pending" states                  |
| Background       | `#14121F` | `BackgroundDark`    | Screen backgrounds                                           |
| Surface          | `#201C33` | `SurfaceDark`       | Cards, sheets, input fields                                  |
| Text primary     | `#ECEAFB` | `TextPrimaryDark`   | Body/heading text on dark backgrounds                        |
| Text secondary   | `#B4AFC7` | `TextSecondaryDark` | Captions, hints, timestamps, muted labels                    |

## Material 3 role mapping

| Material role                    | Light                                          | Dark              |
| -------------------------------- | ---------------------------------------------- | ----------------- |
| `primary`                        | IndigoLight                                    | IndigoDark        |
| `onPrimary`                      | White                                          | BackgroundDark    |
| `tertiary` (accent)              | AmberLight                                     | AmberDark         |
| `background`                     | BackgroundLight                                | BackgroundDark    |
| `surface`                        | SurfaceLight                                   | SurfaceDark       |
| `onBackground` / `onSurface`     | TextPrimaryLight                               | TextPrimaryDark   |
| `onSurfaceVariant`               | soft indigo-gray (interim)                     | TextSecondaryDark |
| `error` / `errorContainer`       | `OweRed` / `OweContainer`                      | `OweRed`          |
| `positive` / `positiveContainer` | `OwedTeal` / `OwedContainer` (custom vals)     | same              |

## Semantic balance colors

Brand-permanent tokens in `Color.kt`: **OweRed** (`#C43D5A`) for "you owe" / error, **OwedTeal** (`#1B8A6B`) for "you're owed" / positive. Role aliases `SplitEaseColors.YouOwe` / `OwedToYou` map to those seeds.

## Screen chrome (back + title)

**One navigation chrome** for secondary screens: `SeScreen` → `SeTopBar` → `SeScreenTitleText`.

Do not invent per-screen title sizes (`headlineMedium` vs `titleMedium` vs `titleLarge`) in app bars. Body content uses `SeLayout` spacings.

| Token / API           | Source                      | Value / role                           |
| --------------------- | --------------------------- | -------------------------------------- |
| Screen title          | `SeScreenTitleStyle()`      | `titleLarge` + SemiBold + Navy (~22sp) |
| Screen subtitle       | `SeScreenSubtitleStyle()`   | `bodyMedium` + `onSurfaceVariant`      |
| Top bar height        | `SeTopBar`                  | 64.dp content height below status bar   |
| Full-width buttons    | `SePrimaryButton` etc.      | 56.dp                                  |
| Action chips          | `SeActionChip`              | 44.dp                                  |
| Leading icon tile     | `SeLayout.iconTile`         | 46 dp                                  |
| Icon → text gap       | `SeLayout.iconTileGap`      | 14.dp                                  |
| After icon tile       | `SeLayout.afterIconTile`    | tile + gap (60.dp)                     |
| Horizontal inset      | `SeLayout.screenHorizontal` | 24.dp                                  |
| Below top bar         | `SeLayout.screenTop`        | 8.dp                                   |
| Bottom of scroll body | `SeLayout.screenBottom`     | 24.dp                                  |
| Title → subtitle      | `SeLayout.titleToSubtitle`  | 8.dp                                   |
| Header → content      | `SeLayout.headerToContent`  | 16.dp                                  |
| Between sections      | `SeLayout.sectionGap`       | 16.dp                                  |
| Between related rows  | `SeLayout.itemGap`          | 8.dp                                   |
| Above primary CTA     | `SeLayout.ctaTopGap`        | 20.dp                                  |

### How to use

1. Prefer `SeScreen(title = …, onBack = …) { padding → … }` for full pages with back.
2. Prefer `SeTopBar(…)` when you need a custom `Scaffold` (auth, home tabs, close+Done flows).
3. Prefer `SeBackTitleRow` only when a Material top app bar is not a fit; it still uses `SeScreenTitleText`.
4. Inside content, pad with `SeLayout.screenHorizontal` / `screenBottom` — **do not** double-apply if `SeScreen(subtitle = …)` already wraps the body (that path applies horizontal inset for the subtitle).
5. Auth back screens use the same `SeTopBar` via `AuthScaffold`.

### Exceptions (not app-bar titles)

Hero / banner titles on colored group headers, ledger amount lines, and in-list row titles may use `headlineMedium` / `titleLarge` as **content** typography. Those are not navigation chrome.

## Typography (`Type.kt`)

`SplitEaseTypography` is the Material 3 type scale. Prefer these styles over per-screen `copy(fontSize = …)`.

| Role | Size / line | Weight |
| ---- | ----------- | ------ |
| `displayLarge` | 46 / 54 sp | Bold |
| `displayMedium` | 36 / 42 sp | Bold |
| `headlineLarge` | 32 / 38 sp | Bold (tab page titles via `SePageHeader`) |
| `headlineMedium` | 28 / 34 sp | Bold |
| `headlineSmall` | 24 / 30 sp | SemiBold |
| `titleLarge` | 22 / 28 sp | SemiBold (secondary screen titles) |
| `titleMedium` | 18 / 24 sp | SemiBold |
| `titleSmall` | 16 / 22 sp | SemiBold |
| `bodyLarge` | 18 / 26 sp | Normal |
| `bodyMedium` | 16 / 22 sp | Normal |
| `bodySmall` | 14 / 18 sp | Normal |
| `labelLarge` | 16 / 22 sp | SemiBold (buttons) |
| `labelMedium` | 14 / 18 sp | Medium |
| `labelSmall` | 13 / 16 sp | Medium |
