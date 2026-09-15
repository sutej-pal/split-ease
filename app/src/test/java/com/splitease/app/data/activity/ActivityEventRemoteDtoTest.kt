package com.splitease.app.data.activity

import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActivityEventRemoteDtoTest {
    @Test
    fun delete_event_omits_related_expense_id_so_upsert_survives_hard_delete() {
        val dto =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = "exp-1")
                .toRemoteDto()
        assertNull(dto.relatedExpenseId)
        assertEquals("EXPENSE_DELETED", dto.kind)
    }

    @Test
    fun add_and_update_events_keep_related_expense_id() {
        val added =
            event(kind = ActivityEventKind.EXPENSE_ADDED, relatedExpenseId = "exp-1")
                .toRemoteDto()
        val updated =
            event(kind = ActivityEventKind.EXPENSE_UPDATED, relatedExpenseId = "exp-1")
                .toRemoteDto()
        assertEquals("exp-1", added.relatedExpenseId)
        assertEquals("exp-1", updated.relatedExpenseId)
    }

    private fun event(
        kind: ActivityEventKind,
        relatedExpenseId: String?,
    ) = ActivityEvent(
        id = "act-1",
        kind = kind,
        title = "Deleted: Dinner",
        subtitle = "Cabin · Sep 7, 2026",
        amountLabel = "INR 120.00",
        actorUserId = "user-1",
        relatedExpenseId = relatedExpenseId,
        involvedUserIds = ",user-1,user-2,",
        sortEpochMs = 1L,
        syncStatus = SyncStatus.PENDING,
        isSeen = true,
    )
}
