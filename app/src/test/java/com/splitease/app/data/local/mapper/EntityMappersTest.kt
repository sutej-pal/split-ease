package com.splitease.app.data.local.mapper

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EntityMappersTest {
    @Test
    fun `expense domain entity round-trip preserves BigDecimal amount`() {
        val expense = Expense(
            id = "e1",
            description = "Dinner",
            amount = BigDecimal("99.99"),
            currencyCode = "INR",
            paidByUserId = "u1",
            groupId = "g1",
            expenseDateEpochMs = 1_700_000_000_000L,
            splitType = SplitType.EQUAL,
            isRecurring = false,
            recurrenceFrequency = RecurrenceFrequency.NONE,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )

        val restored = expense.toEntity().toDomain()
        assertEquals(expense, restored)
        assertEquals(0, expense.amount.compareTo(restored.amount))
    }
}
