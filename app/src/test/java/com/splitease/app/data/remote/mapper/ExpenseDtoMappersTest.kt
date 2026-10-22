package com.splitease.app.data.remote.mapper

import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.domain.model.ExchangeRateSource
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExpenseDtoMappersTest {
    @Test
    fun toDomainExpense_keeps_local_fx_when_remote_omits_snapshot() {
        val existing = localExpense()
        val mapped =
            remoteDto().toDomainExpense(
                existing = existing,
                categoryId = "cat_food",
                createdAtEpochMs = 1_000L,
            )

        assertEquals(BigDecimal("40"), mapped.originalAmount)
        assertEquals("USD", mapped.originalCurrencyCode)
        assertEquals(BigDecimal("94.832"), mapped.rateToDefaultCurrency)
        assertEquals(ExchangeRateSource.LIVE, mapped.rateSource)
        assertEquals("cat_food", mapped.categoryId)
        assertEquals(SyncStatus.SYNCED, mapped.syncStatus)
        assertEquals("Dinner", mapped.description)
    }

    @Test
    fun toDomainExpense_prefers_remote_fx_when_present() {
        val existing = localExpense()
        val mapped =
            remoteDto(
                originalAmount = "40.00",
                originalCurrencyCode = "USD",
                rateToDefaultCurrency = "110",
                rateSource = "CUSTOM",
            ).toDomainExpense(
                existing = existing,
                categoryId = null,
                createdAtEpochMs = 1_000L,
            )

        assertEquals(BigDecimal("110"), mapped.rateToDefaultCurrency)
        assertEquals(ExchangeRateSource.CUSTOM, mapped.rateSource)
    }

    @Test
    fun toDomainExpense_takes_remote_fx_tuple_atomically() {
        val mapped =
            remoteDto(
                originalAmount = "40.00",
                originalCurrencyCode = "USD",
            ).toDomainExpense(
                existing = localExpense(),
                categoryId = null,
                createdAtEpochMs = 1_000L,
            )

        assertEquals(BigDecimal("40.00"), mapped.originalAmount)
        assertEquals("USD", mapped.originalCurrencyCode)
        assertNull(mapped.rateToDefaultCurrency)
        assertNull(mapped.rateSource)
    }

    @Test
    fun toDomainExpense_rejects_invalid_amount() {
        assertThrows(IllegalArgumentException::class.java) {
            remoteDto().copy(amount = "nope").toDomainExpense(
                existing = null,
                categoryId = null,
                createdAtEpochMs = 1_000L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            remoteDto().copy(amount = " ").toDomainExpense(
                existing = null,
                categoryId = null,
                createdAtEpochMs = 1_000L,
            )
        }
    }

    @Test
    fun toDomainExpense_without_local_or_remote_fx_leaves_snapshot_null() {
        val mapped =
            remoteDto().toDomainExpense(
                existing = null,
                categoryId = null,
                createdAtEpochMs = 1_000L,
            )

        assertNull(mapped.originalAmount)
        assertNull(mapped.originalCurrencyCode)
        assertNull(mapped.rateToDefaultCurrency)
        assertNull(mapped.rateSource)
    }

    @Test
    fun toExpenseDto_round_trips_fx_snapshot() {
        val dto = localExpense().toExpenseDto("cat_food")

        assertEquals("40", dto.originalAmount)
        assertEquals("USD", dto.originalCurrencyCode)
        assertEquals("94.832", dto.rateToDefaultCurrency)
        assertEquals("LIVE", dto.rateSource)
        assertEquals("cat_food", dto.categoryId)
    }

    @Test
    fun withoutFxSnapshot_clears_fx_fields() {
        val cleared = localExpense().toExpenseDto(null).withoutFxSnapshot()

        assertNull(cleared.originalAmount)
        assertNull(cleared.originalCurrencyCode)
        assertNull(cleared.rateToDefaultCurrency)
        assertNull(cleared.rateSource)
    }

    private fun remoteDto(
        originalAmount: String? = null,
        originalCurrencyCode: String? = null,
        rateToDefaultCurrency: String? = null,
        rateSource: String? = null,
    ) = ExpenseDto(
        id = "e1",
        description = "Dinner",
        amount = "40.00",
        currencyCode = "USD",
        paidByUserId = "u1",
        groupId = "g1",
        expenseDateEpochMs = 1_000L,
        splitType = "EQUAL",
        updatedAtEpochMs = 3_000L,
        originalAmount = originalAmount,
        originalCurrencyCode = originalCurrencyCode,
        rateToDefaultCurrency = rateToDefaultCurrency,
        rateSource = rateSource,
    )

    private fun localExpense() =
        Expense(
            id = "e1",
            description = "Dinner",
            amount = BigDecimal("40"),
            currencyCode = "USD",
            paidByUserId = "u1",
            groupId = "g1",
            expenseDateEpochMs = 1_000L,
            splitType = SplitType.EQUAL,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
            syncStatus = SyncStatus.SYNCED,
            originalAmount = BigDecimal("40"),
            originalCurrencyCode = "USD",
            rateToDefaultCurrency = BigDecimal("94.832"),
            rateSource = ExchangeRateSource.LIVE,
        )
}
