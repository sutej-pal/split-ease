# Phase 11 — Group Pin Board

Added a shared per-group pin board: one notepad (auto-save, last-edited footer) readable and editable by all members.

**Current editor:** plain text with a **Save** label in the top bar. Typing autosaves after ~2 seconds (Room first, then Supabase). Opening, returning to, or idling on the board fetches the server copy so another member’s work shows up; unsaved typing on this device is kept. No live cursor.

## Phase Goal

Add a shared per-group "Pin Board" — a single rich-text notepad visible and editable by all group members.

## Scope

### In
- Supabase `pin_boards` table (one row per group, RLS by membership)
- Online-only read/write via PostgREST
- Markdown-based content with toolbar (bold, italic, checklist, gallery image)
- Explicit Save (toolbar button); load on screen open
- "Last edited by" footer
- Accessible via action chip on group detail screen

### Out
- Offline (Room) cache for the board
- Conflict resolution / real-time collaborative editing
- Rich text rendering (content stored/displayed as Markdown source)
- Cloud image uploads (gallery images are stored on-device and referenced in Markdown)

## Architecture Decisions

- **Online-only (hard boundary):** The pin board is a lightweight collaborative surface. Always-latest content matters more than offline availability. **No Room entity, no `SyncStatus`, no [SyncInteractor](../app/src/main/java/com/splitease/app/data/sync/SyncInteractor.kt) flush/pull.** Code guardrail: [PinBoardPolicy.kt](../app/src/main/java/com/splitease/app/data/pinboard/PinBoardPolicy.kt). Re-open the screen to refresh from cloud; second device does not get Realtime updates.
- **Markdown storage:** Avoids heavy rich-text serialization libraries. The toolbar inserts Markdown syntax; content is displayed/edited as source text.
- **Single board per group:** `pin_boards.group_id` is the PK — one document, not a collection of notes.
- **Explicit Save:** User taps Save to persist (no debounced auto-save queue).

Later the UI dropped the Markdown toolbar; the field is a plain `BasicTextField`. Persistence is Room first, then cloud sync, with a 2s autosave plus **Save**. Load fetches Supabase on open/resume/idle poll (see [PinBoardPolicy.kt](../app/src/main/java/com/splitease/app/data/pinboard/PinBoardPolicy.kt)).

## Data Model Changes

### New Supabase table: `pin_boards`

| Column     | Type                                      | Notes               |
| ---------- | ----------------------------------------- | ------------------- |
| group_id   | uuid PK, FK → groups.id ON DELETE CASCADE | One board per group |
| content    | text, default ''                          | Plain-text notepad  |
| updated_by | uuid FK → auth.users, ON DELETE SET NULL  | Last editor         |
| updated_at | timestamptz, default now()                | Last edit timestamp |

SQL: included in [sql/migration_db.sql](sql/migration_db.sql)

RLS: SELECT / INSERT / UPDATE allowed when user is a member of the group.

## Files Added

- `docs/sql/migration_db.sql` (embedded `pin_boards` section)
- `data/pinboard/PinBoardRemoteDataSource.kt` — Supabase PostgREST fetch + upsert
- `data/pinboard/PinBoardInteractor.kt` — load / save orchestration
- `presentation/pinboard/PinBoardViewModel.kt` — UI state, debounced auto-save
- `presentation/pinboard/PinBoardScreen.kt` — Compose notepad + last-edited footer

## Files Modified

- `presentation/navigation/SplitEaseNavHost.kt` — added `PIN_BOARD` route + composable
- `presentation/groups/GroupsScreens.kt` — added `onOpenPinBoard` callback + action chip
- `res/values/strings.xml` — pin board string resources

## Screens / UI Added

- **PinBoardScreen** — full-screen notepad with:
  - `SeTopBar` with back arrow and **Save** text action
  - `BasicTextField` for plain-text editing
  - Footer showing saving state or last editor name

## How to Test

1. Run `docs/sql/migration_db.sql` in Supabase SQL Editor.
2. Open a group with 2+ members (both signed in on separate devices).
3. Tap the "Pin Board" chip on the group detail screen.
4. Type content; **Save** or wait ~2 seconds (or leave the screen).  
5. Open the same group's pin board on the second device — content should appear after open (server fetch).
6. Edit on device 2, wait for save; on device 1 return to the board or wait for the idle refresh — latest content visible without a live cursor.

## Known Issues / TODOs

- No live collaborative cursor — updates arrive on open, resume, or idle poll (~15s), not keystroke-by-keystroke.
- Content is plain text (no formatting toolbar). Existing `![](…)` image markdown in stored content is shown as source, not rendered.

## Screenshots

*(placeholder)*
