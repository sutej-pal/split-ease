# Release history

Each Play / sideload build increments **versionCode** (the integer counter Play requires to go up every upload) and a SemVer **versionName**.

| Field | Meaning |
| --- | --- |
| Build | `versionCode` — monotonic counter, never reused |
| Version | `versionName` — `MAJOR.MINOR.PATCH` |

## Create the next release

```bash
./gradlew newRelease
./gradlew newRelease -Pbump=minor -Pnotes="Settle-up screen + mail templates"
./gradlew newRelease -Pbump=major
```

Windows:

```powershell
.\scripts\new-release.ps1
.\scripts\new-release.ps1 -Bump minor -Notes "Settle-up screen + mail templates"
```

That command:

1. Increments `versionCode` by 1
2. Bumps `versionName` (`patch` default, or `minor` / `major`)
3. Prepends a row here
4. Cuts `CHANGELOG.md` `[Unreleased]` into the new version section
5. Updates `app/build.gradle.kts` via `version.properties` (Gradle reads it at sync)

Then assemble / bundle as usual (`assembleRelease` / `bundleRelease`).

**These rows are testing / sideload builds, not a Play Store production release.** Production ship is [TODO(release)](TODO.md#store--release).

## Builds

| Build | Version | Date | Notes |
| ---: | --- | --- | --- |
| 2 | 1.0.0 | 2026-07-23 | Phase 9 — i18n, Room migrations, Play prep |
| — | 0.9.0 | 2026-07-22 | Phase 8 — pay actions, CSV import, charts |
| — | 0.8.0 | 2026-07-22 | Phase 7 — search, categories, offline sync |
| — | 0.7.0 | 2026-07-22 | Phase 6 — settle up, recurring expenses |
| — | 0.6.0 | 2026-07-22 | Phase 5 — balances and debt simplification |
| — | 0.5.0 | 2026-07-22 | Phase 4 — expense create and splits |
| — | 0.4.1 | 2026-07-22 | Phase 3 — email invites |
| — | 0.4.0 | 2026-07-22 | Phase 3 — friends and groups |
| — | 0.3.0 | 2026-07-22 | Phase 2 — Supabase auth |
| — | 0.2.0 | 2026-07-22 | Phase 1 — Room data layer |
| — | 0.1.0 | 2026-07-22 | Phase 0 — project skeleton |
