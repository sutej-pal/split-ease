package com.splitease.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostgrestInFilterTest {
    @Test
    fun multi_parent_in_filter_shares_one_row_cap_and_drops_later_parents() {
        val store = FakePostgrest(rowCap = 5)
        store.seed("g1", count = 6)
        store.seed("g2", count = 2)

        val truncated = store.fetchPage(listOf("g1", "g2"))

        assertEquals(5, truncated.size)
        assertTrue(truncated.all { it.parentId == "g1" })
        assertFalse(truncated.any { it.parentId == "g2" })
    }

    @Test
    fun complete_fetch_splits_capped_in_filter_and_returns_every_parent() =
        runTest {
            val store = FakePostgrest(rowCap = 5)
            store.seed("g1", count = 6)
            store.seed("g2", count = 2)

            val rows =
                fetchCompleteInFilter(
                    ids = listOf("g1", "g2"),
                    rowCap = 5,
                    idChunk = 100,
                    fetchPage = store::fetchPage,
                    fetchOffsetPage = store::fetchOffsetPage,
                )

            assertEquals(8, rows.size)
            assertEquals(6, rows.count { it.parentId == "g1" })
            assertEquals(2, rows.count { it.parentId == "g2" })
            assertTrue(store.inFilterCalls.any { it.toSet() == setOf("g1", "g2") })
            assertTrue(store.offsetCalls.any { it.id == "g1" && it.offset == 5 })
        }

    @Test
    fun complete_fetch_pages_a_single_parent_past_the_row_cap() =
        runTest {
            val store = FakePostgrest(rowCap = 5)
            store.seed("e1", count = 12)

            val rows =
                fetchCompleteInFilter(
                    ids = listOf("e1"),
                    rowCap = 5,
                    idChunk = 100,
                    fetchPage = store::fetchPage,
                    fetchOffsetPage = store::fetchOffsetPage,
                )

            assertEquals(12, rows.size)
            assertEquals(listOf(5, 10), store.offsetCalls.map { it.offset })
        }

    @Test
    fun truncated_child_page_drops_later_parents_splits() {
        val store = FakePostgrest(rowCap = 10)
        store.seed("a", count = 8)
        store.seed("b", count = 4)

        val truncated = store.fetchPage(listOf("a", "b"))

        assertEquals(10, truncated.size)
        assertEquals(8, truncated.count { it.parentId == "a" })
        assertEquals(2, truncated.count { it.parentId == "b" })
        assertFalse(truncated.any { it.id == "b-3" || it.id == "b-4" })
    }

    @Test
    fun complete_fetch_returns_every_child_row_after_a_truncated_split_page() =
        runTest {
            val store = FakePostgrest(rowCap = 10)
            store.seed("a", count = 8)
            store.seed("b", count = 4)

            val rows =
                fetchCompleteInFilter(
                    ids = listOf("a", "b"),
                    rowCap = 10,
                    idChunk = 100,
                    fetchPage = store::fetchPage,
                    fetchOffsetPage = store::fetchOffsetPage,
                )

            assertEquals(12, rows.size)
            assertEquals(8, rows.count { it.parentId == "a" })
            assertEquals(4, rows.count { it.parentId == "b" })
        }

    private data class Row(
        val id: String,
        val parentId: String,
    )

    private data class OffsetCall(
        val id: String,
        val offset: Int,
        val limit: Int,
    )

    /**
     * PostgREST stand-in: `in.(ids)` returns matching rows in insertion order,
     * cut off at [rowCap] — the same global max-rows the real API applies.
     */
    private class FakePostgrest(
        private val rowCap: Int,
    ) {
        private val rows = mutableListOf<Row>()
        val inFilterCalls = mutableListOf<List<String>>()
        val offsetCalls = mutableListOf<OffsetCall>()

        fun seed(
            parentId: String,
            count: Int,
        ) {
            repeat(count) { index ->
                rows += Row(id = "$parentId-${index + 1}", parentId = parentId)
            }
        }

        fun fetchPage(ids: List<String>): List<Row> {
            inFilterCalls += ids
            val idSet = ids.toSet()
            return rows.filter { it.parentId in idSet }.take(rowCap)
        }

        fun fetchOffsetPage(
            id: String,
            offset: Int,
            limit: Int,
        ): List<Row> {
            offsetCalls += OffsetCall(id, offset, limit)
            return rows.filter { it.parentId == id }.drop(offset).take(limit)
        }
    }
}
