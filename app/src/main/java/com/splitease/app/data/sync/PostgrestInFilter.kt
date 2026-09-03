package com.splitease.app.data.sync

/**
 * Fetches every row for a PostgREST `in.(ids)` filter when each SELECT is capped
 * at [rowCap] (project default 1000).
 *
 * Strategy:
 * - Try the id list (chunked for URL length).
 * - If a page comes back at [rowCap], it may be truncated — split the id list
 *   and retry rather than keeping the truncated page.
 * - If a **single** id still fills [rowCap], page that id with [fetchOffsetPage].
 *
 * @param ids Parent ids for the `in.` column.
 * @param rowCap Max rows PostgREST returns per SELECT.
 * @param idChunk Max ids per `in.` URL.
 * @param fetchPage One uncapped-offset SELECT for the given ids (server still
 *   applies [rowCap]).
 * @param fetchOffsetPage Inclusive range page for one id (`offset`..`offset+limit-1`).
 */
internal suspend fun <T> fetchCompleteInFilter(
    ids: List<String>,
    rowCap: Int = REMOTE_FETCH_ROW_CAP,
    idChunk: Int = POSTGREST_IN_FILTER_CHUNK,
    fetchPage: suspend (chunkIds: List<String>) -> List<T>,
    fetchOffsetPage: suspend (id: String, offset: Int, limit: Int) -> List<T>,
): List<T> {
    require(rowCap > 0) { "rowCap must be positive" }
    require(idChunk > 0) { "idChunk must be positive" }
    val distinct = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (distinct.isEmpty()) return emptyList()
    return distinct.chunked(idChunk).flatMap { chunk ->
        fetchCompleteInFilterChunk(
            ids = chunk,
            rowCap = rowCap,
            fetchPage = fetchPage,
            fetchOffsetPage = fetchOffsetPage,
        )
    }
}

private suspend fun <T> fetchCompleteInFilterChunk(
    ids: List<String>,
    rowCap: Int,
    fetchPage: suspend (chunkIds: List<String>) -> List<T>,
    fetchOffsetPage: suspend (id: String, offset: Int, limit: Int) -> List<T>,
): List<T> {
    if (ids.isEmpty()) return emptyList()
    if (ids.size == 1) {
        return fetchAllPagesForSingleId(
            id = ids.single(),
            rowCap = rowCap,
            fetchPage = fetchPage,
            fetchOffsetPage = fetchOffsetPage,
        )
    }
    val page = fetchPage(ids)
    if (page.size < rowCap) return page
    val mid = ids.size / 2
    return fetchCompleteInFilterChunk(ids.take(mid), rowCap, fetchPage, fetchOffsetPage) +
        fetchCompleteInFilterChunk(ids.drop(mid), rowCap, fetchPage, fetchOffsetPage)
}

private suspend fun <T> fetchAllPagesForSingleId(
    id: String,
    rowCap: Int,
    fetchPage: suspend (chunkIds: List<String>) -> List<T>,
    fetchOffsetPage: suspend (id: String, offset: Int, limit: Int) -> List<T>,
): List<T> {
    val all = ArrayList<T>()
    var offset = 0
    while (true) {
        val page =
            if (offset == 0) {
                fetchPage(listOf(id))
            } else {
                fetchOffsetPage(id, offset, rowCap)
            }
        all += page
        if (page.size < rowCap) break
        offset += page.size
    }
    return all
}
