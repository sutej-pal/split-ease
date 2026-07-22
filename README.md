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
./gradlew :app:assembleDebug
./gradlew ktlintCheck
```

On Windows: `gradlew.bat :app:assembleDebug`

## Docs

- [PROGRESS.md](PROGRESS.md) — phase checklist
- [docs/ROADMAP.md](docs/ROADMAP.md) — feature distribution across phases
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [CHANGELOG.md](CHANGELOG.md)
- [docs/](docs/)