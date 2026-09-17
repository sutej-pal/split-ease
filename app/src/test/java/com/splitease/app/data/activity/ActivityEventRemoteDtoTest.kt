package com.splitease.app.data.activity

import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.settings.AppCurrencies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

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
    fun delete_event_keeps_related_expense_id_after_restore_when_expense_exists() {
        val dto =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = "exp-restored")
                .toRemoteDto(relatedExpenseExists = true)
        assertEquals("exp-restored", dto.relatedExpenseId)
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

    @Test
    fun remote_dto_includes_snapshot_fields() {
        val dto =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = "exp-1")
                .copy(
                    snapshotDescription = "Dinner",
                    snapshotAmount = "120.00",
                    snapshotCurrency = "INR",
                    snapshotGroupId = "g1",
                    snapshotGroupName = "Cabin",
                    snapshotCreatorUserId = "user-1",
                    snapshotCreatedAtEpochMs = 99L,
                    snapshotParticipantUserIds = ",user-1,user-2,",
                ).toRemoteDto()
        assertEquals("Dinner", dto.snapshotDescription)
        assertEquals("120.00", dto.snapshotAmount)
        assertEquals("INR", dto.snapshotCurrency)
        assertEquals("g1", dto.snapshotGroupId)
        assertEquals("Cabin", dto.snapshotGroupName)
        assertEquals("user-1", dto.snapshotCreatorUserId)
        assertEquals(99L, dto.snapshotCreatedAtEpochMs)
        assertEquals(",user-1,user-2,", dto.snapshotParticipantUserIds)
    }

    @Test
    fun refresh_preserves_local_snapshots_when_remote_omits_them() {
        val existing =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = "exp-1")
                .copy(
                    snapshotDescription = "Dinner",
                    snapshotAmount = "120.00",
                    snapshotCurrency = "INR",
                    syncStatus = SyncStatus.SYNCED,
                )
        val remote =
            existing.toRemoteDto().copy(
                snapshotDescription = null,
                snapshotAmount = null,
                snapshotCurrency = null,
                relatedExpenseId = null,
            )
        val merged = remote.toDomain(existing)
        assertEquals("Dinner", merged.snapshotDescription)
        assertEquals("120.00", merged.snapshotAmount)
        assertEquals("INR", merged.snapshotCurrency)
        assertEquals("exp-1", merged.relatedExpenseId)
    }

    @Test
    fun restore_amount_prefers_snapshot_then_amount_label() {
        val fromSnapshot =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = null)
                .copy(snapshotAmount = "12.50", amountLabel = "INR 0.00")
        assertEquals(BigDecimal("12.50"), fromSnapshot.restoreAmount())
        val fromLabel =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = null)
                .copy(snapshotAmount = null, amountLabel = "INR 120.00")
        assertEquals(BigDecimal("120.00"), fromLabel.restoreAmount())
        assertEquals("INR", fromLabel.restoreCurrency())
        val empty =
            event(kind = ActivityEventKind.EXPENSE_DELETED, relatedExpenseId = null)
                .copy(snapshotAmount = null, snapshotCurrency = null, amountLabel = "")
        assertNull(empty.restoreAmount())
        assertEquals(AppCurrencies.DEFAULT, empty.restoreCurrency())
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
