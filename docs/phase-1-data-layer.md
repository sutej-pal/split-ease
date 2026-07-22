# Phase 1 — Data Layer Foundations

## Phase Goal

Define SplitEase's core domain models and an offline-first Room database (entities, DAOs, type converters, repository interfaces and Room-backed implementations) so later phases can persist and query users, friends, groups, expenses, splits, payments, and categories with money stored as precise `BigDecimal` values.

## Scope (In / Out)

**In**
- Domain models: `User`, `Group`, `GroupMember`, `Friend`, `Expense`, `ExpenseSplit`, `Payment`, `Category`
- Supporting enums (`SplitType`, `MemberRole`, etc.) and money-safe amount types
- Room entities, DAOs, `SplitEaseDatabase`, BigDecimal/enum converters
- Domain repository interfaces + Room implementations + Hilt wiring
- Entity ↔ domain mappers
- Unit tests for type converters/mappers; in-memory Room DAO instrumented tests
- Updates to `docs/data-dictionary.md` and architecture notes

**Out**
- Any new Compose screens beyond Phase 0 Welcome
- Firebase / remote sync (Phase 2+)
- Split calculation algorithms (Phase 4)
- Balance / debt simplification (Phase 5)
- WorkManager recurring scheduler (Phase 6)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Store monetary amounts as `TEXT` via `BigDecimal` converters | Avoid Float/Double rounding; Room has no native BigDecimal |
| Epoch millis (`Long`) for timestamps in Room | Simple, comparable, timezone-agnostic storage |
| `GroupMember` entity (not listed by name in MVP model list) | Required for group membership many-to-many without denormalizing into Group |
| UUID strings as primary keys | Stable IDs for future Firestore sync without autoincrement conflicts |
| `remoteId` + `updatedAtEpochMs` + `syncStatus` columns now | Offline-first ready; sync logic deferred to Phase 7 |
| Repository interfaces in `domain`, impls in `data` | Clean Architecture dependency rule |
| DAO tests as `androidTest` with in-memory Room | Uses Room Testing (stack-mandated Room); no extra third-party DB test lib |
| `junit-platform-launcher` explicit dependency | Fixes JUnit 5.12 discovery under Gradle 8.11 |
| Destructive migration fallback for v1 | Acceptable pre-release; proper migrations start when schema ships to users |

## Data Model Changes

Room database `splitease.db` **v1** with tables: `users`, `friends`, `groups`, `group_members`, `categories`, `expenses`, `expense_splits`, `payments`.

Schema export: `app/schemas/com.splitease.app.data.local.db.SplitEaseDatabase/1.json`

Full column dictionary: [data-dictionary.md](./data-dictionary.md)

Domain money example:

```kotlin
data class Expense(
    val id: String,
    val amount: BigDecimal, // never Float/Double
    // ...
)
```

## Files Added/Modified

| File path | Purpose |
|---|---|
| `domain/model/*.kt` | Domain models + enums |
| `domain/repository/*.kt` | Repository interfaces with KDoc |
| `data/local/entity/*.kt` | Room entities |
| `data/local/dao/*.kt` | Room DAOs |
| `data/local/converter/SplitEaseTypeConverters.kt` | BigDecimal + enum converters |
| `data/local/mapper/EntityMappers.kt` | Entity ↔ domain mapping |
| `data/local/db/SplitEaseDatabase.kt` | Room database definition |
| `data/repository/Room*Repository.kt` | Room-backed repository impls |
| `data/di/DatabaseModule.kt` | Hilt DB/DAO providers |
| `data/di/RepositoryModule.kt` | Hilt repository bindings |
| `app/schemas/.../1.json` | Exported Room schema |
| `src/test/.../SplitEaseTypeConvertersTest.kt` | Converter unit tests |
| `src/test/.../EntityMappersTest.kt` | Mapper unit tests |
| `src/androidTest/.../DaoInstrumentedTest.kt` | In-memory DAO tests |
| `gradle/libs.versions.toml` | room-testing, coroutines-test, junit-platform-launcher |
| `app/build.gradle.kts` | Test deps, Room schemaLocation, versionName 0.2.0 |
| `docs/data-dictionary.md` | Schema tables |
| `.editorconfig` | Relaxed ktlint rules that fight Kotlin/Compose idioms |

## Screens/UI Added

None — Welcome screen unchanged.

## How to Test This Phase

### Manual
1. Launch the app — Welcome screen still appears (no UI regression).
2. (Optional) Use App Inspection / Database Inspector after Phase 3 seeds data; Phase 1 has no UI writers.

### Automated
```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat ktlintCheck
.\gradlew.bat :app:assembleDebug
```

- Unit: BigDecimal converter round-trip + expense mapper round-trip — **passed**
- Instrumented: users, friends cascade, group members cascade, expense+splits (₹100 / 3), payments — **passed** on connected device

## Known Issues / TODOs carried forward

- `fallbackToDestructiveMigration` will wipe local data on schema bumps until Phase 7+/9 add real migrations.
- Default categories are seeded only when `CategoryRepository.ensureDefaults()` is called (wire from app start / home in Phase 2–3).
- Friend rows do not FK `friendUserId` to `users` (allows invite placeholders before the friend has a local user row).
- No Firestore yet — `remoteId` / `syncStatus` unused until Phase 2–7.
- Firebase free-tier N/A this phase (local only).
- Capture optional screenshot of Database Inspector into `docs/screenshots/phase-1.png` if desired.

## Screenshots placeholder

![phase-1-screenshot](./screenshots/phase-1.png)

---

## Plan

1. Add domain models + repository interfaces with KDoc.
2. Add Room entities, converters, DAOs, database, mappers.
3. Implement Room repositories and Hilt `DatabaseModule` / `RepositoryModule`.
4. Add converter unit tests and in-memory DAO instrumented tests; add `room-testing` + `coroutines-test`.
5. Update data dictionary and phase Outcome; mark Phase 1 Done in `PROGRESS.md`.
6. Stop — do not start Phase 2.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 1 delivered a fully wired offline-first data layer:

- Domain models + repository interfaces (KDoc on public APIs)
- Room v1 schema with BigDecimal TEXT storage and sync bookmark columns
- Hilt `DatabaseModule` + `RepositoryModule`
- Unit tests (converters/mappers) and instrumented in-memory DAO tests — both green
- Debug APK still builds; Welcome UI unchanged
- Docs: data dictionary filled; architecture updated

**Next:** Phase 2 — Authentication (Firebase Auth, login/signup screens). Do not start until instructed to continue.
