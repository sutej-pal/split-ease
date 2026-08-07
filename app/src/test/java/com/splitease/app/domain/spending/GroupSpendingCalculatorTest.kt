package com.splitease.app.domain.spending

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

class GroupSpendingCalculatorTest {
    private val tz = TimeZone.getTimeZone("UTC")

    @Test
    fun period_totals_sum_amount_and_viewer_share() {
        val augStart = GroupSpendingCalculator.monthBounds(2026, Calendar.AUGUST, tz).first
        val e1 = expense("e1", "100.00", augStart + 1)
        val e2 = expense("e2", "50.00", augStart + 2)
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "me", "40.00"), split("e1", "you", "60.00")),
                "e2" to listOf(split("e2", "me", "25.00"), split("e2", "you", "25.00")),
            )
        val (total, share) =
            GroupSpendingCalculator.periodTotals(
                viewerUserId = "me",
                expenses = listOf(e1, e2),
                splitsByExpenseId = splits,
                currencyCode = "INR",
                fromEpochMs = augStart,
                toEpochMs = GroupSpendingCalculator.monthBounds(2026, Calendar.AUGUST, tz).second,
            )
        assertEquals(BigDecimal("150.00"), total)
        assertEquals(BigDecimal("65.00"), share)
    }

    @Test
    fun monthly_buckets_are_chronological_ending_at_selected() {
        val jul = GroupSpendingCalculator.monthBounds(2026, Calendar.JULY, tz).first + 10
        val aug = GroupSpendingCalculator.monthBounds(2026, Calendar.AUGUST, tz).first + 10
        val expenses =
            listOf(
                expense("e1", "10.00", jul),
                expense("e2", "30.00", aug),
            )
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "me", "10.00")),
                "e2" to listOf(split("e2", "me", "15.00")),
            )
        val bars =
            GroupSpendingCalculator.monthlyBuckets(
                viewerUserId = "me",
                expenses = expenses,
                splitsByExpenseId = splits,
                currencyCode = "INR",
                endYear = 2026,
                endMonth = Calendar.AUGUST,
                monthCount = 3,
                timeZone = tz,
            )
        assertEquals(3, bars.size)
        assertEquals(Calendar.JUNE, bars[0].month)
        assertEquals(Calendar.JULY, bars[1].month)
        assertEquals(Calendar.AUGUST, bars[2].month)
        assertEquals(BigDecimal("0.00"), bars[0].totalSpent)
        assertEquals(BigDecimal("10.00"), bars[1].totalSpent)
        assertEquals(BigDecimal("30.00"), bars[2].totalSpent)
        assertEquals(BigDecimal("15.00"), bars[2].yourShare)
    }

    @Test
    fun share_percent_null_when_total_zero() {
        assertNull(
            GroupSpendingCalculator.sharePercent(BigDecimal.ZERO, BigDecimal("10.00")),
        )
        assertEquals(
            40,
            GroupSpendingCalculator.sharePercent(BigDecimal("100.00"), BigDecimal("40.00")),
        )
    }

    private fun expense(
        id: String,
        amount: String,
        dateMs: Long,
    ) = Expense(
        id = id,
        description = id,
        amount = BigDecimal(amount),
        currencyCode = "INR",
        paidByUserId = "me",
        groupId = "g1",
        expenseDateEpochMs = dateMs,
        splitType = SplitType.EQUAL,
        createdAtEpochMs = dateMs,
        updatedAtEpochMs = dateMs,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun split(
        expenseId: String,
        userId: String,
        owed: String,
    ) = ExpenseSplit(
        id = "$expenseId-$userId",
        expenseId = expenseId,
        userId = userId,
        owedAmount = BigDecimal(owed),
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
