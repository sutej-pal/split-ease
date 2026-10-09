# Replace Raw Text() Usages with SeText Helpers

## Goal Description
Refactor Compose UI files across the SplitEase application to replace standard Material `Text(...)` composables with the standardized `SeText` helper functions defined in `SeText.kt` (such as `SeBodyLarge`, `SeBodyMedium`, `SeTitleMedium`, `SeLabelMedium`, `SeHeadlineLarge`, etc.). This enforces design system consistency for typography, font weights, and default text colors.

## User Review Required

> [!IMPORTANT]
> This refactoring touches numerous screen and component files across the presentation layer. We will map raw `Text` calls that use Material typography styles and SplitEase colors to their corresponding `SeText` counterparts (e.g. `SeBodyMedium`, `SeTitleLarge`, `SeLabelMedium`). Where `Text` is used inside buttons, tabs, or specialized components where text parameters or custom styling are required by upstream components, we will evaluate case-by-case.

## Open Questions

- Should `Text` inside standard Material components (like Button or Tab labels where the component accepts `content = { Text(...) }`) be converted to `SeText` helpers? Yes, wherever applicable and cleaner.
- Are there any custom styles or dynamic parameters where raw `Text` should be retained? Yes, if dynamic fontSize or custom inline styling prevents clean mapping, but standard typography/color combinations will be mapped to `Se*` helpers.

## Proposed Changes

### Presentation Layer (`com.splitease.app.presentation`)

We will systematically review and refactor presentation packages/files:
- `presentation/account/`
- `presentation/activity/`
- `presentation/ads/`
- `presentation/auth/`
- `presentation/balances/`
- `presentation/changelog/`
- `presentation/expenses/`
- `presentation/friends/`
- `presentation/navigation/`
- `presentation/profile/`
- `presentation/settings/`
- `presentation/ui/`

#### [MODIFY] Screen and Component Files
Replace `Text(...)` calls matching Material typography and SplitEase colors with `SeDisplay*`, `SeHeadline*`, `SeTitle*`, `SeBody*`, and `SeLabel*` helpers from `com.splitease.app.presentation.theme.*`.

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation across all modified files.
- Run unit tests if any (`app:testDebugUnitTest`).

### Manual Verification
- Verify Compose previews render correctly and UI screens appear consistent.
