# Phase 0 — Project Setup, Foundations & Brand Theme

Scaffolded the Android app (Gradle, Compose, Hilt, Navigation, docs, Welcome screen) and locked in the indigo/amber Material 3 brand theme for light and dark mode. No product features yet — this phase only makes the project runnable and on-brand.

## Phase Goal

Establish the SplitEase Android project skeleton (Gradle, Compose Material 3, Hilt/Room/Navigation stubs, docs, Welcome screen) and wire the finalized indigo/amber brand palette into a hand-authored Material 3 `ColorScheme` (light + dark).

## Scope (In / Out)

**In**
- Android app module `com.splitease.app`, version catalog, Gradle Kotlin DSL
- Compose Material 3 theme: dynamic color opt-in + fixed brand indigo/amber `ColorScheme`
- Hilt, Navigation Compose, Room dependency stubs (no domain entities in this phase)
- Welcome screen as the initial UI destination
- Documentation roots: `docs/`, `PROGRESS.md`, `CHANGELOG.md`, `ARCHITECTURE.md`, `docs/data-dictionary.md`, `docs/design-tokens.md`
- ktlint + Cursor project rules
- Contrast (WCAG AA) checks for approved text/background pairs
- `SplitEaseColors` aliases aligned to brand tokens

**Out**
- Domain models, Room entities, DAOs, repositories (Phase 1)
- Auth / cloud (Phase 2+)
- Expense, group, or friend UI
- Custom fonts / typography overhaul
- Final semantic "you owe" / "you're owed" / pending colors (placeholders only)
- Material You dynamic color as the default
- detekt (deferred)

## Architecture Decisions

| Decision                                          | Rationale                                                                     |
| ------------------------------------------------- | ----------------------------------------------------------------------------- |
| Single `:app` module for MVP                      | Fastest path; multi-module can wait                                           |
| Package layout `presentation` / `domain` / `data` | Clean Architecture from day one                                               |
| Hand-authored `ColorScheme`                       | Brand indigo/amber stay fixed; no Material tonal substitution for those roles |
| Dynamic color opt-in only                         | `SplitEaseTheme(dynamicColor = false)` by default                             |
| Accent → `tertiary`                               | Amber maps to tertiary; primary stays indigo                                  |
| Design tokens in `design-tokens.md`               | Color roles stay out of the schema dictionary                                 |
| Firebase / Supabase deferred to Phase 2           | Auth not needed for Welcome launch                                            |
| ktlint + version catalog                          | CI-friendly style; centralized dependency versions                            |
| compileSdk / targetSdk **36**, minSdk **26**      | Platform targets at scaffold time                                             |

## Data Model Changes

None. Room on classpath only; theme-only work later in this phase.

## Key deliverables

**Scaffold**
- Gradle Wrapper **8.11.1**; AGP / Kotlin / Compose BOM via catalog
- `SplitEaseApplication`, `MainActivity` (edge-to-edge), Welcome + Nav host
- `.editorconfig`, `.cursor/rules`, living docs

**Theme**
- `presentation/theme/Color.kt`, `Theme.kt`, `SplitEaseColors.kt`
- System bars via theme `SideEffect` + `WindowCompat`
- Token doc: [design-tokens.md](design-tokens.md)

## Screens/UI

| Screen          | Description                                                            |
| --------------- | ---------------------------------------------------------------------- |
| `WelcomeScreen` | Brand hero; later gained Get started / Log in (Phase 2 session gating) |

## Contrast notes (WCAG 2.1 AA)

Primary text on light/dark background and surface pairs pass (≥4.5:1). Amber as body text on light backgrounds fails AA — use amber for fills/icons/dividers, not body copy. See [design-tokens.md](design-tokens.md) for hex roles.

## How to Test

1. `./gradlew.bat :app:assembleDebug` and `ktlintCheck`
2. Launch Welcome; toggle light/dark — indigo primary, amber tertiary, readable body text
3. Confirm brand hex on background/surface (light `#E8EAFE` / white; dark `#14121F` / `#201C33`)

## Known Issues / TODOs carried forward

- Semantic balance colors still placeholders — confirm before shipping
- Some screens still hardcode colors / `SplitEaseColors` light aliases instead of `MaterialTheme.colorScheme`
- Adaptive launcher denser assets deferred to release polish
- Capture Welcome light/dark screenshots when useful

## Outcome

**Status:** Done (scaffold 2026-07-22; brand theme follow-up same era)

Runnable app with Compose/Hilt/Nav skeleton, Welcome start destination, and fixed brand light/dark theme. Auth navigation and Supabase followed in Phase 2.

**Next historically:** Phase 1 — Data Layer Foundations.
