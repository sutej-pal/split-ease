# SplitEase

Native Android expense-sharing app (Kotlin + Jetpack Compose). Inspired by Splitwise's feature set — original architecture and UI.

## Status

Phases **0–12** are complete (split letter phases folded into 0 and 10). See [PROGRESS.md](PROGRESS.md) and the feature map in [docs/ROADMAP.md](docs/ROADMAP.md).

Carried-forward work (not a numbered phase) is listed under **Carried-forward TODOs** in `PROGRESS.md`.

## Requirements

- JDK 17+
- Android SDK (compile SDK 37 / target SDK 36)
- Android Studio recent stable recommended
- Supabase credentials in gitignored `local.properties` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`)
- Optional `GOOGLE_WEB_CLIENT_ID` in `local.properties` for **Continue with Google** ([docs/google-sign-in.md](docs/google-sign-in.md))
- Optional AdMob IDs in `local.properties` for release ads (`ADMOB_APP_ID`, `ADMOB_GROUP_DETAIL_BANNER_UNIT_ID`, `ADMOB_ADD_EXPENSE_BANNER_UNIT_ID`). Debug builds use Google test ad units automatically.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew ktlintCheck
./gradlew :app:testDebugUnitTest
```

On Windows: `gradlew.bat :app:assembleDebug`

Release builds enable **R8 minify + resource shrinking**.

Number a testing or store build (increments `versionCode`, bumps SemVer, records history). Current APKs are **testing only** — production Play release is still [TODO(release)](TODO.md):

```bash
./gradlew newRelease
./gradlew newRelease -Pbump=minor -Pnotes="Short summary"
```

See [RELEASES.md](RELEASES.md).

## Docs

Start here: [docs/README.md](docs/README.md)

| Doc                                                    | Purpose                      |
| ------------------------------------------------------ | ---------------------------- |
| [PROGRESS.md](PROGRESS.md)                             | Phase checklist + open TODOs |
| [docs/ROADMAP.md](docs/ROADMAP.md)                     | Feature → phase map          |
| [ARCHITECTURE.md](ARCHITECTURE.md)                     | Living architecture          |
| [CHANGELOG.md](CHANGELOG.md)                           | Keep a Changelog             |
| [RELEASES.md](RELEASES.md)                             | Build counter + version log  |
| [docs/data-dictionary.md](docs/data-dictionary.md)     | Schema / entities            |
| [docs/release-checklist.md](docs/release-checklist.md) | Ship checklist               |
