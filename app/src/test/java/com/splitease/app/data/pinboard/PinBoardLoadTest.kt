package com.splitease.app.data.pinboard

import com.splitease.app.data.local.entity.PinBoardEntity
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinBoardLoadTest {
    @Test
    fun empty_when_no_local_or_remote() {
        val decision = resolvePinBoardLoad("g1", local = null, remote = null)
        assertEquals("", decision.dto.content)
        assertEquals("g1", decision.dto.groupId)
        assertFalse(decision.writeRemoteToCache)
    }

    @Test
    fun uses_local_when_offline() {
        val local = entity(content = "draft", status = SyncStatus.SYNCED)
        val decision = resolvePinBoardLoad("g1", local, remote = null)
        assertEquals("draft", decision.dto.content)
        assertFalse(decision.writeRemoteToCache)
    }

    @Test
    fun prefers_remote_when_local_is_synced() {
        val local = entity(content = "old", status = SyncStatus.SYNCED)
        val remote = PinBoardDto(groupId = "g1", content = "from server", updatedBy = "u2")
        val decision = resolvePinBoardLoad("g1", local, remote)
        assertEquals("from server", decision.dto.content)
        assertTrue(decision.writeRemoteToCache)
    }

    @Test
    fun keeps_pending_local_instead_of_clobbering_with_remote() {
        val local = entity(content = "my unsaved", status = SyncStatus.PENDING)
        val remote = PinBoardDto(groupId = "g1", content = "someone else", updatedBy = "u2")
        val decision = resolvePinBoardLoad("g1", local, remote)
        assertEquals("my unsaved", decision.dto.content)
        assertFalse(decision.writeRemoteToCache)
    }

    @Test
    fun parses_iso_updated_at() {
        assertEquals(1_704_067_200_000L, parsePinBoardUpdatedAtEpochMs("2024-01-01T00:00:00Z"))
        assertEquals(1_704_067_200_000L, parsePinBoardUpdatedAtEpochMs("2024-01-01T00:00:00+00:00"))
        assertEquals(null, parsePinBoardUpdatedAtEpochMs(null))
        assertEquals(null, parsePinBoardUpdatedAtEpochMs("not-a-date"))
    }

    private fun entity(
        content: String,
        status: SyncStatus,
    ) = PinBoardEntity(
        groupId = "g1",
        content = content,
        updatedByUserId = "u1",
        updatedAtEpochMs = 1L,
        syncStatus = status,
    )
}
