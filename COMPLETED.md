# SplitEase — completed on emulator

Checked on `emulator-5554` (`com.splitease.app` 1.0.0, build 2) while signed in. These items were marked done in `TODO.md` and held up on the device.

## Auth & onboarding

- [x] **In-app account deletion** — Account → Account settings → Close your account → Delete account. The screen blocked deletion with “Settle these first” and listed Trip to Goa. Tapping that row opened the group. The typed `DELETE` confirm did not appear while the balance was non-zero.
- [x] **Onboarding-complete cloud flag** — The signed-in session opened on Groups. No post-signup setup wizard appeared.

## Review follow-ups (2026-10-01)

- [x] **TODO(text-secondary-light-scope)** — On Groups, the “You owe” and “You are owed” labels sampled as `#585C7E`.
- [x] **TODO(empty-state-theme-mode)** — A new solo group showed “You're the only one here”. After Appearance → Dark, the empty card, icon well, banner, chips, and bottom bar switched to the dark theme. Appearance was set back to Light, and that temporary group was deleted.

## Review follow-ups (2026-10-13)

- [x] **TODO(theme-row-a11y)** — Appearance rows are exposed as checkable controls. The selected theme’s row was `checked=true` (Light, then Dark).

## Review follow-ups (2026-10-16)

- [x] **TODO(password-row-duplicate-error)** — Sign in and contact → Password. A blank current password showed “Enter your password.” only under Current password. A wrong current password showed “That password isn't right.” only under Current password. The new-password field did not repeat it. The password was not changed.

## Sync, live updates & notifications

- [x] **A6 — Pull-to-refresh** — Swiping down on the Trip to Goa ledger did not show a refresh control.
- [x] **B8 — Notification preferences** — Account → Notifications showed Group updates switched on. Group settings → Mute notifications turned on, stayed on after leaving the screen, and was turned off again.
- [x] **Add Expense currency picker** — On Add expense, the amount symbol opened the currency picker.
- [x] **Expand `AppCurrencies.OPTIONS`** — The picker listed the expanded set, including SAR via search (INR, USD, AED, AUD, BRL, CAD, CHF, CNY, DKK, EGP, EUR, GBP, HKD, IDR, ILS, JPY, KRW, MXN, MYR, NOK, NZD, PHP, PLN, RUB, SAR, SEK, SGD, THB, TRY, TWD, VND, ZAR).
- [x] **FX snapshot on add expense** — Choosing USD against the INR group showed Exchange Rate, Live, and `1 USD = 94.9907 INR`. The expense was not saved.
- [x] **Group totals per currency** — Trip to Goa → Totals for October 2026 showed total spent ₹493.00 and your share ₹244.00 in the group currency. No extra currency breakdown appeared, which matches a period that only has INR.
- [x] **Activity events cross-device** — The Activity feed listed expenses added and updated in Trip to Goa by the other member.
- [x] **Pin board offline cache / server refresh** — Trip to Goa → Pin Board opened an editor with Save. The board was empty, so there was no existing note to overwrite.
- [x] **Group live updates & push** — A notification tap intent opened Trip to Goa. The Activity tab showed 3 unread from the other member before the feed was opened. A later new remote event posts the tray item; events already on the device are treated as history and are not posted again.
- [x] **B6 — Activity badges** — The Activity tab showed a 3 badge on Groups, Account, and Trip to Goa while those remote events were still unseen.

## Payments & stretch

- [x] **CSV import** — Account has no bank CSV import. Group settings still has Export as CSV, which is separate from import.

## Design / theme

- [x] **TODO(design) — Semantic balance colors** — The non-zero “you owe” amount on Groups sampled as `#C43D5A`. The owed card used a separate pale container. A zero owed amount stayed dark rather than teal.
- [x] **Light text-secondary token** — Same Groups labels sampled as `#585C7E`.
- [x] **Hardcoded colors outside theme** — Switching Appearance to Dark recolored the empty-group card, banner, chips, and bottom bar together. Theme was restored to Light.
- [x] **Migrate `Se` screens to `MaterialTheme.colorScheme`** — Group detail, group settings, account, totals, and the empty group stayed on the in-app theme surfaces in both Light and Dark.
- [x] **Dynamic color settings opt-in** — Appearance only offers Light, Dark, and System default. Buttons stayed brand purple. There is no dynamic-color toggle.

## Store / release

- [x] **Support contact** — Account → Contact support is present, with the subtitle “Email the SplitEase team.” The composer was not opened.
- [x] **Privacy policy URL** — Account → About and Help center → Legal both show `https://splitease-server-eight.vercel.app/privacy`. Tapping the Help row opened that page in Chrome.
