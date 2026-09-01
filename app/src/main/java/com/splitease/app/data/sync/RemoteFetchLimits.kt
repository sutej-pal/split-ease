package com.splitease.app.data.sync

/**
 * PostgREST projects commonly cap SELECT rows (default 1000).
 * Remote-delete prune must not run when the fetch may be truncated.
 */
internal const val REMOTE_FETCH_ROW_CAP = 1000

/**
 * Max ids per PostgREST `in.()` filter. Keeps URLs short and each SELECT under the
 * typical 1000-row cap when a parent has a handful of child rows.
 */
internal const val POSTGREST_IN_FILTER_CHUNK = 100

/** True when [remoteRowCount] is below the cap and safe to treat as a complete remote set. */
internal fun isCompleteRemoteFetch(remoteRowCount: Int): Boolean = remoteRowCount < REMOTE_FETCH_ROW_CAP
