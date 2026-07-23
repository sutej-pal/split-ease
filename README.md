# SplitEase

Native Android expense-sharing app (Kotlin + Jetpack Compose). Inspired by Splitwise's feature set — original architecture and UI.

## Status

See [PROGRESS.md](PROGRESS.md) for phase status and [docs/ROADMAP.md](docs/ROADMAP.md) for the full feature → phase map.

**Next:** Phase 4 — Expense Creation & Splitting Logic.

## Requirements

- JDK 17+
- Android SDK (compile/target SDK 36)
- Android Studio Otter / recent stable recommended

## Build

```bash
./gradlew :app:assembleStandardDebug
./gradlew ktlintCheck
```

On Windows: `gradlew.bat :app:assembleStandardDebug`

### Twin install (testing only)

Install a second copy beside the main app to test multi-device sync on one phone.
`clone` uses `applicationId` `com.splitease.app.clone` and is **debug-only** (no release variant).

```bash
./gradlew :app:installStandardDebug :app:installCloneDebug
```

## Docs

- [PROGRESS.md](PROGRESS.md) — phase checklist
- [docs/ROADMAP.md](docs/ROADMAP.md) — feature distribution across phases
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [CHANGELOG.md](CHANGELOG.md)
- [docs/](docs/)