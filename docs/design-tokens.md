# Design Tokens

Canonical **visual** design tokens for SplitEase. Schema / Room entities remain in [data-dictionary.md](data-dictionary.md).

Brand colors are tied to the app icon (two-tone indigo receipt with amber divider). Change hex values only together with icon/design updates.

Source of truth in code: `presentation/theme/Color.kt` → `Theme.kt` `ColorScheme`.

Colours are explicit hex; do not use lerp/wash/shade.

## Authored seeds

These values are written directly. Balance colours stay on these tokens.

| Token | Hex | Use |
| ----- | --- | --- |
| `IndigoLight` | `#4F46E5` | Primary: buttons, focused fields, links |
| `AmberLight` | `#FFA008` | Accent: highlights, pending, home-group warmth |
| `OweRed` | `#C43D5A` | "You owe" balance |
| `OweContainer` | `#FDE8EC` | You-owe / error container |
| `OwedTeal` | `#1B8A6B` | "You're owed" / positive |
| `OwedContainer` | `#DDF6EE` | Positive container |
| `OutlineDark` | `#4B465C` | Dark resting borders |
| `OutlineVariantDark` | `#3A3552` | Dark hairline / card edges |
| `SurfaceLight` | `#FFFFFF` | Light cards, sheets, dialogs |

## Light theme

| Token | Hex | Use |
| ----- | --- | --- |
| `BackgroundLight` | `#FBFCFF` | Screen canvas |
| `PrimaryContainerLight` | `#E6EAFF` | Selected / muted brand fill |
| `SurfaceMutedLight` | `#F1F3FF` | Grouped rows, unfocused fields, chip idle fills |
| `BannerFriendsLight` | `#C3CBFC` | Friends group banner |
| `BannerHomeLight` | `#FFE9D1` | Home group banner |
| `BannerOtherLight` | `#C3CBFC` | Other group banner |
| `TextPrimaryLight` | `#060424` | Body and heading text |
| `TextSecondaryLight` | `#575F9E` | Captions, hints, timestamps |
| `OutlineLight` | `#DAE0FE` | Resting borders |
| `OutlineVariantLight` | `#EBEEFF` | Hairline / card edges |

## Dark theme

| Token | Hex | Use |
| ----- | --- | --- |
| `IndigoDark` | `#7781F1` | Primary: buttons, focused fields, links |
| `AmberDark` | `#FFAE4C` | Accent: highlights, pending |
| `BackgroundDark` | `#000004` | Screen canvas |
| `SurfaceDark` | `#030217` | Cards, sheets, input fields |
| `BannerFriendsDark` | `#3D36B7` | Friends group banner |
| `BannerHomeDark` | `#C37D2B` | Home group banner |
| `BannerOtherDark` | `#49509E` | Other group banner |
| `FriendDetailBannerDark` | `#747CF0` | Friend detail header banner |
| `NonGroupBannerDark` | `#332C9B` | Non-group expenses header banner |
| `TextPrimaryDark` | `#EFF1FF` | Body and heading text |
| `TextSecondaryDark` | `#BFC8FC` | Captions, hints, timestamps |

`NonGroupBannerDark` keeps the previous dark-banner colour. That blend used the light-theme text colour and may be unintended.

## Other

| Token | Hex | Use |
| ----- | --- | --- |
| `SplitEaseColors.IconFriends` | `#4F46E5` | Friends group glyph (`IndigoLight`) |
| `SplitEaseColors.IconHome` | `#FFA008` | Home group glyph (`AmberLight`) |
| `SplitEaseColors.IconOther` | `#5F68C5` | Other group glyph |

## Material 3 role mapping

| Material role | Light | Dark |
| ------------- | ----- | ---- |
| `primary` | IndigoLight `#4F46E5` | IndigoDark `#7781F1` |
| `onPrimary` | White | BackgroundDark `#000004` |
| `primaryContainer` | PrimaryContainerLight `#E6EAFF` | SurfaceDark `#030217` |
| `tertiary` (accent) | AmberLight `#FFA008` | AmberDark `#FFAE4C` |
| `tertiaryContainer` | BannerHomeLight `#FFE9D1` | `#4A3400` |
| `background` | BackgroundLight `#FBFCFF` | BackgroundDark `#000004` |
| `surface` | SurfaceLight `#FFFFFF` | SurfaceDark `#030217` |
| `surfaceVariant` | SurfaceMutedLight `#F1F3FF` | SurfaceDark `#030217` |
| `onBackground` / `onSurface` | TextPrimaryLight `#060424` | TextPrimaryDark `#EFF1FF` |
| `onSurfaceVariant` | TextSecondaryLight `#575F9E` | TextSecondaryDark `#BFC8FC` |
| `outline` | OutlineLight `#DAE0FE` | OutlineDark `#4B465C` |
| `outlineVariant` | OutlineVariantLight `#EBEEFF` | OutlineVariantDark `#3A3552` |
| `error` | ErrorLight `#B3261E` | ErrorDark `#FF8A80` |
| `onError` | White | BackgroundDark `#000004` |
| `errorContainer` | OweContainer `#FDE8EC` | `#8C1D18` |
| `onErrorContainer` | ErrorLight `#B3261E` | OweContainer `#FDE8EC` |
| `positive` / `positiveContainer` | OwedTeal `#1B8A6B` / OwedContainer `#DDF6EE` | same |

## Semantic balance colors

Brand-permanent tokens in `Color.kt`: **OweRed** (`#C43D5A`) for "you owe", **OwedTeal** (`#1B8A6B`) for "you're owed" / positive. Role aliases `SplitEaseColors.YouOwe` / `OwedToYou` map to those seeds.

Errors use `colorScheme.error`, never `OweRed`.

## Error colours

| Token | Hex | Role |
| ----- | --- | ---- |
| `ErrorLight` | `#B3261E` | Light `colorScheme.error` and `onErrorContainer` |
| `ErrorDark` | `#FF8A80` | Dark `colorScheme.error` |

Contrast below is WCAG relative luminance from these hex values and the surface tokens above.

| Pair | Ratio |
| ---- | ----- |
| ErrorLight on BackgroundLight `#FBFCFF` | 6.37:1 |
| ErrorLight on white / SurfaceLight | 6.54:1 |
| ErrorLight on SurfaceMutedLight `#F1F3FF` | 5.92:1 |
| ErrorDark on BackgroundDark `#000004` | 9.18:1 |
| ErrorDark on SurfaceDark `#030217` (also dark `surfaceVariant`) | 8.98:1 |
| White on ErrorLight (`onError` on `error`, light) | 6.54:1 |
| BackgroundDark on ErrorDark (`onError` on `error`, dark) | 9.18:1 |
| ErrorLight on OweContainer `#FDE8EC` (`onErrorContainer` on `errorContainer`, light) | 5.58:1 |
| OweContainer on `#8C1D18` (`onErrorContainer` on `errorContainer`, dark) | 7.78:1 |

All of those pairs are at least 4.5:1. All inline error messages use bodySmall via `SeErrorText` / `seErrorTextStyle()`.

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
| Leading icon tile     | `SeLayout.iconTile`         | 46 dp, **16.dp rounded** (see Locked shapes) |
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

### Typography Composables (`theme/SeText.kt`)

For all text rendering going forward, prefer the named composables in `theme/SeText.kt` (`SeTitleMedium`, `SeBodyMedium`, etc.) over raw `Text()` calls with `MaterialTheme.typography.*`. They bundle the typography scale, default weight, and default color from `SplitEaseColors`:

| Composable | Typography scale | Default Weight | Default Color |
| --- | --- | --- | --- |
| `SeDisplayLarge` | `displayLarge` | Bold | `SplitEaseColors.Navy` |
| `SeDisplayMedium` | `displayMedium` | Bold | `SplitEaseColors.Navy` |
| `SeHeadlineLarge` | `headlineLarge` | Bold | `SplitEaseColors.Navy` |
| `SeHeadlineMedium` | `headlineMedium` | SemiBold | `SplitEaseColors.Navy` |
| `SeHeadlineSmall` | `headlineSmall` | SemiBold | `SplitEaseColors.Navy` |
| `SeTitleLarge` | `titleLarge` | SemiBold | `SplitEaseColors.Navy` |
| `SeTitleMedium` | `titleMedium` | SemiBold | `SplitEaseColors.Navy` |
| `SeTitleSmall` | `titleSmall` | SemiBold | `SplitEaseColors.Navy` |
| `SeBodyLarge` | `bodyLarge` | Normal | `SplitEaseColors.Navy` |
| `SeBodyMedium` | `bodyMedium` | Normal | `SplitEaseColors.NavyMuted` |
| `SeBodySmall` | `bodySmall` | Normal | `SplitEaseColors.NavyMuted` |
| `SeLabelLarge` | `labelLarge` | SemiBold | `SplitEaseColors.Navy` |
| `SeLabelMedium` | `labelMedium` | Medium | `SplitEaseColors.NavyMuted` |
| `SeLabelSmall` | `labelSmall` | Medium | `SplitEaseColors.NavyMuted` |

## Locked shapes (do not change)

These silhouettes are product decisions. Do **not** restyle them in a later prompt unless the user explicitly asks.

| Component | Shape | Do not |
| --------- | ----- | ------ |
| `SeIconTile` | `RoundedCornerShape(16.dp)` | Do not remove the clip or make the tile square |
| `SeGroupIconTile` (photo) | `RoundedCornerShape(14.dp)` | Do not remove the clip or make the photo square |
| Home list icon skeleton (`GroupSkeletonListItem`) | `RoundedCornerShape(16.dp)` | Keep in sync with `SeIconTile` |

Avatars (`SeAvatarBadge`) stay circular. Action chips and cards keep their own rounded radii.

Canonical freeze list: [locked-ui.md](locked-ui.md).
