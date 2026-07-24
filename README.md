# SplitEase

Native Android expense-sharing app (Kotlin + Jetpack Compose). Inspired by Splitwise's feature set — original architecture and UI.

## Status

Phases **0–9** and **0b / 10a** are complete. See [PROGRESS.md](PROGRESS.md) and the feature map in [docs/ROADMAP.md](docs/ROADMAP.md).

Carried-forward work (not a numbered phase) is listed under **Carried-forward TODOs** in `PROGRESS.md`.

## Requirements

- JDK 17+
- Android SDK (compile SDK 37 / target SDK 36)
- Android Studio recent stable recommended
- Supabase credentials in gitignored `local.properties` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`)

## Build

```bash
./gradlew :app:assembleStandardDebug
./gradlew :app:assembleStandardRelease
./gradlew ktlintCheck
./gradlew :app:testStandardDebugUnitTest
```

On Windows: `gradlew.bat :app:assembleStandardDebug`

Release builds enable **R8 minify + resource shrinking**.

### Twin install (testing only)

Install a second copy beside the main app to test multi-device sync on one phone.
`clone` uses `applicationId` `com.splitease.app.clone` and is **debug-only** (no release variant).

```bash
./gradlew :app:installStandardDebug :app:installCloneDebug
```

## Docs

Start here: [docs/README.md](docs/README.md)

| Doc | Purpose |
|---|---|
| [PROGRESS.md](PROGRESS.md) | Phase checklist + open TODOs |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Feature → phase map |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Living architecture |
| [CHANGELOG.md](CHANGELOG.md) | Keep a Changelog |
| [docs/data-dictionary.md](docs/data-dictionary.md) | Schema / entities |
| [docs/release-checklist.md](docs/release-checklist.md) | Ship checklist |
