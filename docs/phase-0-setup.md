# Phase 0 — Project Setup & Foundations

## Phase Goal

Establish the SplitEase Android project skeleton with Gradle Kotlin DSL, Jetpack Compose Material 3 theming, Hilt/Room/Navigation wiring stubs, CI-friendly lint tooling, and the documentation scaffolding required for all later phases — delivering a runnable app that opens to a Welcome screen.

## Scope (In / Out)

**In**
- Android application module with package `com.splitease.app`
- Version catalog (`libs.versions.toml`), Gradle Kotlin DSL
- Compose Material 3 theme (dynamic color + brand palette)
- Hilt, Navigation Compose, Room dependency stubs (no domain entities yet)
- Welcome screen as the sole UI destination
- Documentation roots: `docs/`, `PROGRESS.md`, `CHANGELOG.md`, `ARCHITECTURE.md`, `docs/data-dictionary.md`
- ktlint for formatting/lint (Compose-aware `.editorconfig`)
- Cursor project rules capturing the SplitEase working agreement

**Out**
- Domain models, Room entities, DAOs, repositories (Phase 1)
- Firebase Auth / Firestore (Phase 2+)
- Any expense, group, or friend UI
- Charts, OCR, or other Phase 8 features
- detekt (deferred; ktlint covers Phase 0 CI formatting needs)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Single `:app` module for MVP | Fastest path to a compilable increment; multi-module can wait until layers stabilize |
| Package layout `presentation` / `domain` / `data` (placeholders) | Aligns with Clean Architecture from day one without premature module splits |
| Material 3 + dynamic color with teal/emerald brand accents | Distinct from Splitwise branding while feeling modern and trustworthy for finance UX |
| Firebase deferred to Phase 2 | Auth/cloud not needed for empty Welcome launch; avoid blocking setup on Firebase console |
| ktlint via Gradle plugin + `.editorconfig` | Lightweight, CI-friendly Kotlin style; Compose PascalCase allowed via `ktlint_function_naming_ignore_when_annotated_with` |
| Version catalog (`libs.versions.toml`) | Centralized dependency versions as mandated by tech stack |
| `kapt` for Hilt/Room (Phase 0) | Stable toolchain for AGP 8.9; KSP migration can be revisited later if compile times hurt |
| compileSdk / targetSdk **36** | Latest stable platform installed on the machine (`android-36`); minSdk **26** as specified |

## Data Model Changes

None in Phase 0. Room dependency is on the classpath only; no entities or migrations yet.

See `docs/data-dictionary.md` (empty tables reserved for Phase 1+).

## Files Added/Modified

| File path | Purpose |
|---|---|
| `settings.gradle.kts` | Root project name + `:app` include |
| `build.gradle.kts` | Root plugin aliases |
| `gradle/libs.versions.toml` | Version catalog for AGP, Kotlin, Compose, Hilt, Room, etc. |
| `gradle.properties` | JVM args, AndroidX, Kotlin style |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/*` | Gradle Wrapper 8.11.1 |
| `app/build.gradle.kts` | App module: Compose, Hilt, Room, Nav, ktlint, JUnit5 |
| `app/proguard-rules.pro` | Placeholder ProGuard rules |
| `app/src/main/AndroidManifest.xml` | Application + MainActivity launcher |
| `app/src/main/java/.../SplitEaseApplication.kt` | `@HiltAndroidApp` entry |
| `app/src/main/java/.../MainActivity.kt` | Compose host with edge-to-edge |
| `app/src/main/java/.../presentation/theme/*` | Material 3 theme, typography, brand colors |
| `app/src/main/java/.../presentation/navigation/SplitEaseNavHost.kt` | Nav host (Welcome start) |
| `app/src/main/java/.../presentation/welcome/WelcomeScreen.kt` | Welcome UI |
| `app/src/main/java/.../domain/DomainLayerMarker.kt` | Domain package placeholder |
| `app/src/main/java/.../data/DataLayerMarker.kt` | Data package placeholder |
| `app/src/main/res/**` | Strings, colors, theme, adaptive launcher icon |
| `.editorconfig` | ktlint / Kotlin style (Compose-aware) |
| `.gitignore` | Build, IDE, secrets |
| `.cursor/rules/splitease.mdc` | Always-on agent working agreement |
| `PROGRESS.md` / `CHANGELOG.md` / `ARCHITECTURE.md` / `README.md` | Project docs |
| `docs/phase-0-setup.md` | This phase doc |
| `docs/data-dictionary.md` | Schema dictionary (empty for Phase 0) |
| `docs/screenshots/.gitkeep` | Screenshot folder placeholder |
| `local.properties` | Local SDK path (gitignored) |

## Screens/UI Added

| Screen | Description |
|---|---|
| `WelcomeScreen` | Brand mark ("SE"), hero title **SplitEase**, tagline on a teal gradient surface confirming Material 3 + Navigation |

## How to Test This Phase

### Manual
1. Open the project in Android Studio (or use CLI below).
2. Sync Gradle; ensure JDK 17 is selected.
3. Run `./gradlew.bat :app:assembleDebug` — expect `app/build/outputs/apk/debug/app-debug.apk`.
4. Install on an emulator/device (API 26+): `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Launch **SplitEase** — Welcome screen shows brand title and tagline with teal theme.
6. Toggle system dark mode — dynamic color (API 31+) or dark brand scheme should apply without crash.
7. Run `./gradlew.bat ktlintCheck` — should pass.

### Automated
- None beyond `ktlintCheck` in Phase 0 (DAO/unit tests start Phase 1).

## Known Issues / TODOs carried forward

- Adaptive launcher uses `mipmap-anydpi-v26` only (OK for minSdk 26); Phase 9 may add denser marketing icons.
- Hilt/Room are on the classpath but unused beyond `@HiltAndroidApp` / `@AndroidEntryPoint` — Phase 1 will add modules/entities.
- `google-services.json` not present yet (Firebase Phase 2).
- SDK warning about platform-tools duplicate folder (`platform-tools-2`) on this machine — environment noise, not app code.
- kapt warns about unrecognized dagger options when no processors run heavily yet — expected until Room/Hilt modules appear.
- detekt not added; introduce in Phase 9 polish if static analysis beyond ktlint is desired.
- Capture Welcome screenshot into `docs/screenshots/phase-0.png` (see placeholder below).

## Screenshots placeholder

![phase-0-screenshot](./screenshots/phase-0.png)

---

## Plan

1. Scaffold root Gradle project (settings, version catalog, wrappers) targeting minSdk 26 and latest stable compile/target SDK.
2. Create `:app` with Compose, Hilt, Navigation, Room, Coroutines dependencies via version catalog.
3. Apply Material 3 theme with brand colors and dynamic color where available.
4. Implement `WelcomeScreen` and wire it as the start destination via Navigation Compose.
5. Add ktlint Gradle plugin and a basic check task.
6. Finalize documentation Outcome section, update `PROGRESS.md` / `CHANGELOG.md` / `ARCHITECTURE.md`.
7. Stop after Phase 0 — do not begin Phase 1.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 0 delivered a compilable SplitEase app (`com.splitease.app`) with:

- Gradle Kotlin DSL + version catalog; Wrapper **8.11.1**; AGP **8.9.1**; Kotlin **2.1.20**; Compose BOM **2025.05.00**
- minSdk **26**, compile/targetSdk **36**
- Hilt application/activity wiring, Navigation Compose start → Welcome
- Material 3 theme (dynamic color + teal brand fallback)
- ktlint configured and passing (`ktlintCheck`)
- Full documentation scaffolding + Cursor rule `.cursor/rules/splitease.mdc`
- Verified: `:app:assembleDebug` → `app-debug.apk`; `ktlintCheck` green

**Next:** Phase 1 — Data Layer Foundations (domain models, Room entities/DAOs, repository interfaces). Do not start until instructed to continue.

### Revisited on 2026-07-22 (Phase 2)

Welcome screen gained Get started / Log in CTAs; navigation is now session-gated (Supabase Auth). Backend default overridden to Supabase.
