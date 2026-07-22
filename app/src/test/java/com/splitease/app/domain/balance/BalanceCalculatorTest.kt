package com.splitease.app.domain.balance

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BalanceCalculatorTest {
    @Test
    fun equal_split_two_people_payer_is_owed_half() {
        val expense = expense(id = "e1", amount = "100.00", paidBy = "a", currency = "INR")
        val splits =
            mapOf(
                "e1" to
                    listOf(
                        split("e1", "a", "50.00"),
                        split("e1", "b", "50.00"),
                    ),
            )
        val nets = BalanceCalculator.netBalances(listOf(expense), splits)
        assertEquals(BigDecimal("50.00"), nets["a"])
        assertEquals(BigDecimal("-50.00"), nets["b"])
    }

    @Test
    fun three_way_unequal_nets() {
        val expense = expense(id = "e1", amount = "90.00", paidBy = "a", currency = "INR")
        val splits =
            mapOf(
                "e1" to
                    listOf(
                        split("e1", "a", "10.00"),
                        split("e1", "b", "30.00"),
                        split("e1", "c", "50.00"),
                    ),
            )
        val nets = BalanceCalculator.netBalances(listOf(expense), splits)
        assertEquals(BigDecimal("80.00"), nets["a"])
        assertEquals(BigDecimal("-30.00"), nets["b"])
        assertEquals(BigDecimal("-50.00"), nets["c"])
        val sum = nets.values.reduce { acc, v -> acc.add(v) }
        assertEquals(0, sum.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun multi_currency_buckets_separate() {
        val inr = expense(id = "e1", amount = "100.00", paidBy = "a", currency = "INR")
        val usd = expense(id = "e2", amount = "40.00", paidBy = "b", currency = "USD")
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "a", "50.00"), split("e1", "b", "50.00")),
                "e2" to listOf(split("e2", "a", "20.00"), split("e2", "b", "20.00")),
            )
        val byCurrency =
            BalanceCalculator.netBalancesByCurrency(listOf(inr, usd), splits)
        assertEquals(BigDecimal("50.00"), byCurrency["INR"]!!["a"])
        assertEquals(BigDecimal("-50.00"), byCurrency["INR"]!!["b"])
        assertEquals(BigDecimal("-20.00"), byCurrency["USD"]!!["a"])
        assertEquals(BigDecimal("20.00"), byCurrency["USD"]!!["b"])
    }

    @Test
    fun pairwise_uses_only_shared_expenses() {
        val shared = expense(id = "e1", amount = "60.00", paidBy = "a", currency = "INR")
        val solo = expense(id = "e2", amount = "30.00", paidBy = "a", currency = "INR", groupId = "g1")
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "a", "30.00"), split("e1", "b", "30.00")),
                "e2" to listOf(split("e2", "a", "30.00")),
            )
        val pair =
            BalanceCalculator.pairwiseNetByCurrency(
                viewerUserId = "a",
                otherUserId = "b",
                expenses = listOf(shared, solo),
                splitsByExpenseId = splits,
            )
        assertEquals(BigDecimal("30.00"), pair["INR"])
        assertTrue(!pair.containsKey("USD"))
    }

    @Test
    fun payment_settles_equal_split_debt() {
        val expense = expense(id = "e1", amount = "100.00", paidBy = "a", currency = "INR")
        val splits =
            mapOf(
                "e1" to
                    listOf(
                        split("e1", "a", "50.00"),
                        split("e1", "b", "50.00"),
                    ),
            )
        val expenseNets = BalanceCalculator.netBalancesByCurrency(listOf(expense), splits)
        val settled =
            BalanceCalculator.applyPayments(
                expenseNets,
                listOf(payment(from = "b", to = "a", amount = "50.00", currency = "INR")),
            )
        assertTrue(settled.isEmpty())
    }

    @Test
    fun pairwise_includes_settlement() {
        val expense = expense(id = "e1", amount = "60.00", paidBy = "a", currency = "INR")
        val splits =
            mapOf("e1" to listOf(split("e1", "a", "30.00"), split("e1", "b", "30.00")))
        val pair =
            BalanceCalculator.pairwiseNetByCurrency(
                viewerUserId = "a",
                otherUserId = "b",
                expenses = listOf(expense),
                splitsByExpenseId = splits,
                payments = listOf(payment(from = "b", to = "a", amount = "30.00", currency = "INR")),
            )
        assertTrue(pair.isEmpty())
    }

    private fun payment(
        from: String,
        to: String,
        amount: String,
        currency: String,
    ) = Payment(
        id = "p-$from-$to",
        fromUserId = from,
        toUserId = to,
        amount = BigDecimal(amount),
        currencyCode = currency,
        paidAtEpochMs = 0L,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun expense(
        id: String,
        amount: String,
        paidBy: String,
        currency: String,
        groupId: String? = null,
    ) = Expense(
        id = id,
        description = "test",
        amount = BigDecimal(amount),
        currencyCode = currency,
        paidByUserId = paidBy,
        groupId = groupId,
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
