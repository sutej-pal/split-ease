# Room schema exports

Room writes one JSON snapshot per database version under
`com.splitease.app.data.local.db.SplitEaseDatabase/`.

`SplitEaseSchemaExportGuardTest` asserts that a file exists for **every version
from the lowest JSON currently in this folder through the live `version` in
`SplitEaseDatabase.kt`**. Bumping the Room version without committing the new
export fails CI.

## Permanently missing: versions 1–4

`1.json`–`4.json` were never committed. Room only emits schema JSON for the
version compiled at that point in history, so those files cannot be regenerated
or hand-written retroactively. Do not spend time chasing them.

The oldest export in this folder is `5.json`. The guard starts there.
