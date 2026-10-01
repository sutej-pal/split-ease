# Review follow-ups — phone invites and empty-group card

From the 2026-10-01 review of staged phone-contact work and commit `1bbb662` (empty-group card). Not fixed yet. Check a box here and in [TODO.md](../TODO.md) when the item ships.

## Staged phone-invite work

- [ ] **TODO(invite-token-disclosure)** — `get_invite_preview` is `security definer` and granted to `anon`. The pending-member query in [migration_db.sql](sql/migration_db.sql) returns every other pending invite's `token`. Tapping that row (`InviteJoinViewModel.onPendingMemberSelected`) stores the token and opens sign-up, which loads that person's email or phone. A forwarded group link is enough. Joined members are safe (no token). Needs a server check that does not put raw tokens in the public payload. Until this SQL is applied, `invite_token` stays null and those rows do nothing. Explicitly deferred: do not change this behavior until this item is picked up.

- [ ] **TODO(group-resend-sms-dialog)** — Friend resend passes `InviteDeliveryPolicy.RESEND_SKIPS_DIALOG`. Group settings does not, so `GroupSettingsScreen` keeps the Edit Contact default and shows "Send invite by SMS?" after the user already chose resend. Pass `RESEND_SKIPS_DIALOG` on that handler. Group detail first-send should keep the confirm dialog.

- [ ] **TODO(invite-landing-resume)** — `InviteLandingScreen` uses `LifecycleEventEffect(ON_RESUME)`, which calls `onInviteToken(routeToken)`. That writes the route token over a member token already stored for sign-up and sets `isLoading`, so returning to the landing screen flashes the spinner. Load when the route token changes, not on every resume.

- [ ] **TODO(phone-embedded-country-code)** — A number that already contains the country code, but no `+` or `00`, is treated as a national number and gets a second prefix. `9876543210` correctly becomes `+91…`. `919876543210` opens the confirm dialog, still defaults to `+91`, and saves `+91919876543210`. Device contacts often look like that. Detect a known calling code when the digit length is longer than a national number (`ContactIdentifier.normalizePhone` / `hasExplicitCountryCode` / `EditContactViewModel.presentPhoneConfirm`). Do not treat a 10-digit Indian mobile that starts with `91` as already international.

- [ ] **TODO(share-chooser-once)** — SMS delivery uses `claimInviteOpen` so rotation does not open Messages twice. `InviteDeliveryAction.OpenChooser` does not. A rotation before `consumeShareText` can open the email share sheet twice. Guard the chooser the same way.

- [ ] **TODO(unknown-dial-prefill)** — Dial codes outside the ten-country list never prefill sign-up. `ContactIdentifier.splitDialCode` returns null, so the phone row stays hidden and the number is dropped. Still show the number and let the user pick a country.

- [ ] **TODO(dial-plus-one-flag)** — `+1` is both the United States and Canada. `DialCodes.flagFor` returns the first match, so a Canadian number shows the US flag. The picker already distinguishes by flag and code; reconstructing from E.164 does not.

- [ ] **TODO(text-secondary-light-scope)** — Staged `TextSecondaryLight` changed from `#575F9E` to `#3A3F69` (`Color.kt`, `ColorTokensFrozenTest`). That recolors captions across the app, separate from phone invites. Confirm it stays, or revert it.

## Last commit (`1bbb662`)

- [ ] **TODO(empty-state-theme-mode)** — Empty-state colors follow the system theme, not the in-app theme. `MainActivity` picks light or dark from `ThemeMode`. `SplitEaseColors.EmptyStateTint`, border, icon, title, hint, secondary button, and shadow all call `isSystemInDarkTheme()`. With the app forced to dark and the phone in light mode, the card stays on the light palette. Resolve those from the scheme `SplitEaseTheme` installed (`MaterialTheme.colorScheme`), the same way `Navy` and `Background` already do. `BannerCircleIconButton` in `GroupsScreens.kt` has the same system-theme split and was already there before this commit.

- [ ] **TODO(primary-button-height-scope)** — `SePrimaryButton` dropped from a fixed `56.dp` height to `heightIn(min = 48.dp)` for every primary button, not only the empty-group card. `SeSecondaryButton` is only used on that card, so its outline style stays local. Confirm the shorter primary buttons are intended.
