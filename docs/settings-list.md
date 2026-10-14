# Settings list

Flat settings list used on Account and Account settings. Reuse this layout on other settings-style screens. Do not wrap these rows in cards or icon tiles.

Reference screens:

- `presentation/account/AccountScreen.kt`
- `presentation/account/AccountProfileSettingsScreen.kt`

Shared composables (package `com.splitease.app.presentation.account`):

| Composable | Role |
| ---------- | ---- |
| `AccountSettingsSectionHeader` | Group label |
| `AccountSettingsRow` | One tappable row |
| `AccountSettingsIcon` | 24dp outline icon, shared tint |
| `accountSettingsDividerColor()` | Hairline between groups |
| `accountSettingsIconColor()` | Icon tint for a custom leading glyph |

## Structure

The list sits on the screen background. The scroll column has **no horizontal padding**, so dividers run edge to edge.

```
[optional identity block]
row
──────── divider
row                          ← standalone row, no header
──────── divider
Section label
row
row                          ← no divider between rows in a group
──────── divider
Section label
row
```

- One group is a header plus its rows, or a single row with no header.
- `HorizontalDivider` only **between** groups, color `accountSettingsDividerColor()`.
- Do not draw a divider between rows in the same group.
- The whole row is the click target. Do not add a chevron.
- A trailing control is optional (`AccountSettingsRow` `trailing`). Keep 12dp between the text and that control.
- A profile photo may use `leadingSize = 36.dp`. Every other leading stays 24dp so titles share one column.

## Spacing

| Piece | Value |
| ----- | ----- |
| Row padding | 16dp horizontal, 14dp vertical |
| Leading slot | 24dp, content centered |
| Icon glyph | 24dp |
| Icon to text | 16dp |
| Title to subtitle | 2dp |
| Text to trailing | 12dp |
| Section label padding | 16dp start/end, 16dp top, 4dp bottom |

Icons are vertically centered on the text block.

## Type

Sans serif. `includeFontPadding = false`. Letter spacing `0.sp`.

| Role | Size | Weight | Line height | Color | Lines |
| ---- | ---- | ------ | ----------- | ----- | ----- |
| Section label | 14sp | Medium (500) | 20sp | `SplitEaseColors.NavyMuted` | 1 |
| Row title | 16sp | Medium (500) | 24sp | `SplitEaseColors.Navy` | 1, ellipsis |
| Row subtitle | 14sp | Normal (400) | 20sp | `SplitEaseColors.NavyMuted` | 2, ellipsis |

## Icons and separators

Outline Material icons only. No fill, no rounded well, no `SeIconTile`.

| | Light | Dark |
| --- | ----- | ---- |
| Icon | `#424751` | `#C5C7CC` |
| Divider | `#D8D9DE` | `#3A3C41` |

Every icon on the list uses that one tint, including destructive rows. Text colors stay `Navy` / `NavyMuted`. Do not recolor titles or subtitles to match the icon gray.

Dark values follow `MaterialTheme.colorScheme.background` luminance below `0.5`.

## Adding a screen

1. Use `SeScreen` for the top bar.
2. Scroll a `Column` with bottom padding only.
3. Call `AccountSettingsSectionHeader`, `AccountSettingsRow`, and `AccountSettingsIcon`.
4. Put `HorizontalDivider(color = accountSettingsDividerColor())` between groups.
5. If the screen is outside `presentation.account`, move these composables to a shared `presentation/ui` file and keep the values in this doc.

```kotlin
HorizontalDivider(color = accountSettingsDividerColor())
AccountSettingsSectionHeader(text = stringResource(R.string.section))
AccountSettingsRow(
    title = stringResource(R.string.row_title),
    subtitle = stringResource(R.string.row_subtitle),
    onClick = onOpen,
    leading = { AccountSettingsIcon(icon = Icons.Outlined.Example) },
)
```

## Do not

- Put the list in a rounded `Surface` card.
- Use `SeIconTile`, filled icons, or a tinted icon background.
- Use `SeListRow` / `SeSectionHeader` for this layout. Their type and padding are different.
- Insert chevrons on every row.
- Change icon or divider hex without updating this doc and both account screens together.
