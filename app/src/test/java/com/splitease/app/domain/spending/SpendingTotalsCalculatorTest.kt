package com.splitease.app.domain.spending

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SpendingTotalsCalculatorTest {
    @Test
    fun groups_viewer_owed_by_category_and_currency() {
        val food = expense("e1", "INR", "cat-food", "100.00")
        val travel = expense("e2", "INR", "cat-travel", "40.00")
        val other = expense("e3", "USD", null, "20.00")
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "me", "50.00"), split("e1", "you", "50.00")),
                "e2" to listOf(split("e2", "me", "40.00")),
                "e3" to listOf(split("e3", "me", "10.00"), split("e3", "you", "10.00")),
            )
        val result =
            SpendingTotalsCalculator.byCategory(
                viewerUserId = "me",
                expenses = listOf(food, travel, other),
                splitsByExpenseId = splits,
                categoryNames = mapOf("cat-food" to "Food", "cat-travel" to "Travel"),
            )
        assertEquals(3, result.size)
        assertEquals(BigDecimal("50.00"), result.first { it.categoryId == "cat-food" }.total)
        assertEquals(BigDecimal("40.00"), result.first { it.categoryId == "cat-travel" }.total)
        assertEquals(BigDecimal("10.00"), result.first { it.categoryId == null }.total)
        assertEquals("USD", result.first { it.categoryId == null }.currencyCode)
    }

    private fun expense(
        id: String,
        currency: String,
        categoryId: String?,
        amount: String,
    ) = Expense(
        id = id,
        description = id,
        amount = BigDecimal(amount),
        currencyCode = currency,
        categoryId = categoryId,
        paidByUserId = "me",
        expenseDateEpochMs = 0L,
        splitType = SplitType.EQUAL,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
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
