# SplitEase — failed on emulator

Checked on `emulator-5554` (`com.splitease.app` 1.0.0, build 2) while signed in. These items were marked done in `TODO.md` and did not hold up, or the claimed behavior never appeared.

The session was left signed in, on Light theme, with group mute off. A temporary group created for the empty-state check was deleted.

## Auth & onboarding

- [ ] **OTP ops checklist** — No confirm-email or 6-digit OTP screen appeared. Mail delivery and OTP length were not exercised.
- [ ] **Resend domain** — Nothing on screen shows whether production OTP goes through SplitEase Server and Brevo.
- [ ] **Google Sign-In** — The signed-in session never showed “Continue with Google”, and signing out would have dropped the session.
- [ ] **Password reset UX** — The in-app 6-digit recovery OTP and set-new-password screens were not opened.
- [ ] **Profile photo in onboarding** — The sign-up avatar step was not opened. Account settings does have “Change profile photo”, which is a different screen.

## Review follow-ups (2026-10-16)

- [ ] **TODO(secondary-email-rpc-error-check)** — Sign in and contact was opened, but a secondary email was not submitted, so the taken-email error path was not shown.
- [ ] **TODO(secondary-email-db-error-masking)** — No unexpected database error or “That email can't be added.” message appeared.
- [ ] **TODO(signin-contact-unused-inset)** — Sign in and contact laid out normally. A removed inset constant has no on-screen proof.
- [ ] **TODO(dialog-vertical-actions-tone)** — The delete-group confirm was a horizontal Cancel / Delete group pair. A vertical dialog with a custom confirm color was not shown.
- [ ] **TODO(password-error-heuristic)** — The current-password error was checked separately. The secondary-email classifier that used to treat the word “right” as a password error was not exercised.
- [ ] **TODO(auth-unconfirmed-fallback-otp)** — Login with an unconfirmed email was not attempted, so the fallback OTP error was not shown.

## Invites & App Links

- [ ] **Invite email delivery** — Group settings includes “Add people to group” and the empty group offered “Add group members”. No invite was sent, so mail delivery was not confirmed. Phone share-sheet fallback was not opened.

## Sync, live updates & notifications

- [ ] **A5 — Remote delete tombstones** — No remote delete was available to check whether a synced expense disappears on pull. The pull path already drops local SYNCED rows that are missing remotely and keeps PENDING rows.
- [ ] **Conflict policy** — No older remote row versus a local pending edit was available to check. Pull already keeps a local PENDING row when the remote copy is the same age or older.
- [ ] **Category sync** — Expense rows show category icons. Stable `cat_` ids and legacy remapping are not visible on screen. Built-in categories already sync as `cat_*` ids.
- [ ] **Social PENDING flush** — Creating a group succeeded, but the order of groups, members, and invites versus expenses was not visible. Flush already sends groups, members, and invites before expenses.
