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
    fun payer_not_on_split_is_owed_the_full_amount() {
        val expense = expense(id = "e1", amount = "80.00", paidBy = "a", currency = "INR")
        val splits =
            mapOf(
                "e1" to listOf(split("e1", "b", "80.00")),
            )
        val nets = BalanceCalculator.netBalances(listOf(expense), splits)
        assertEquals(BigDecimal("80.00"), nets["a"])
        assertEquals(BigDecimal("-80.00"), nets["b"])
        assertEquals(
            BigDecimal("80.00"),
            BalanceCalculator.viewerNetForExpense("a", expense, splits.getValue("e1")),
        )
        assertEquals(
            BigDecimal("-80.00"),
            BalanceCalculator.viewerNetForExpense("b", expense, splits.getValue("e1")),
        )
    }

    @Test
    fun viewer_net_single_payer_matches_legacy() {
        val expense = expense(id = "e1", amount = "100.00", paidBy = "a", currency = "INR")
        val splits =
            listOf(
                split("e1", "a", "50.00"),
                split("e1", "b", "50.00"),
            )
        assertEquals(
            BigDecimal("50.00"),
            BalanceCalculator.viewerNetForExpense("a", expense, splits),
        )
        assertEquals(
            BigDecimal("-50.00"),
            BalanceCalculator.viewerNetForExpense("b", expense, splits),
        )
    }

    @Test
    fun viewer_net_multi_payer_credits_each_paid_amount() {
        val expense = expense(id = "e1", amount = "100.00", paidBy = "a", currency = "INR")
        val splits =
            listOf(
                split("e1", "a", owed = "50.00", paid = "70.00"),
                split("e1", "b", owed = "50.00", paid = "30.00"),
            )
        // Primary paidBy is still "a", but b also paid — nets must use paidAmount.
        assertEquals(
            BigDecimal("20.00"),
            BalanceCalculator.viewerNetForExpense("a", expense, splits),
        )
        assertEquals(
            BigDecimal("-20.00"),
            BalanceCalculator.viewerNetForExpense("b", expense, splits),
        )
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
                listOf(payment(amount = "50.00")),
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
                payments = listOf(payment(amount = "30.00")),
            )
        assertTrue(pair.isEmpty())
    }

    private fun payment(amount: String) =
        Payment(
            id = "p-b-a",
            fromUserId = "b",
            toUserId = "a",
            amount = BigDecimal(amount),
            currencyCode = "INR",
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
        paid: String? = null,
    ) = ExpenseSplit(
        id = "$expenseId-$userId",
        expenseId = expenseId,
        userId = userId,
        owedAmount = BigDecimal(owed),
        paidAmount = paid?.let { BigDecimal(it) },
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
