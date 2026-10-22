# Locked UI (do not change)

Product decisions that later prompts must **leave alone** unless the user explicitly asks to change them.

## Icon tiles — rounded

`SeIconTile` uses `RoundedCornerShape(16.dp)`. `SeGroupIconTile` photos use `RoundedCornerShape(14.dp)`. **Do not square them** by dropping the clip.

- Implementation: `presentation/ui/SeList.kt`
- Home skeleton icon well must stay 16.dp rounded (`GroupSkeletonListItem` in `GroupsHomeScreen.kt`)
- Avatars (`SeAvatarBadge`) remain circular; action chips / cards keep their own radii

## Groups home list

Do not gate the group **list** on `ui.isLoading || balances == null`. Show the full-list skeleton only when `ui.isLoading && ui.allGroups.isEmpty()`. Cached group rows stay visible while balances catch up; freeze row amounts with `showAmounts = !freezeAmounts`, not by swapping the list for skeletons.

## Image crop dialog

Keep title + body, rotate (`Icons.AutoMirrored.Filled.RotateRight` + `cd_rotate_photo`), and reset pan/zoom with `remember(rotationTurns)` (not `DisposableEffect`). Recycle rotated bitmaps that are not the base decode.

## Currency symbols

`MoneyFormat` uses `AppCurrencies.symbol`. Locked overrides: AED/SAR/EGP as Latin codes; INR `₹`, USD `$`, EUR `€`, GBP `£`. Update `AppCurrenciesTest` if these ever change on purpose.

## Bottom bar fade

The tab-bar fade is drawn **above** the bar with `drawBehind` so it does not increase `LocalBottomBarInset` or steal list taps. Do not wrap the bar in extra layout height to fake a gradient.
