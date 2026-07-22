package com.splitease.app.domain.balance

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.SplitType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DebtSimplifierTest {
    @Test
    fun two_person_single_transfer() {
        val nets =
            mapOf(
                "a" to BigDecimal("50.00"),
                "b" to BigDecimal("-50.00"),
            )
        val transfers = DebtSimplifier.simplify(nets, "INR")
        assertEquals(1, transfers.size)
        assertEquals("b", transfers[0].fromUserId)
        assertEquals("a", transfers[0].toUserId)
        assertEquals(BigDecimal("50.00"), transfers[0].amount)
        assertEquals("INR", transfers[0].currencyCode)
    }

    @Test
    fun three_person_minimizes_to_two_transfers() {
        // a is owed 80, b owes 30, c owes 50
        val nets =
            mapOf(
                "a" to BigDecimal("80.00"),
                "b" to BigDecimal("-30.00"),
                "c" to BigDecimal("-50.00"),
            )
        val transfers = DebtSimplifier.simplify(nets, "INR")
        assertEquals(2, transfers.size)
        val totalPaid = transfers.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
        assertEquals(BigDecimal("80.00"), totalPaid)
        assertTrue(transfers.all { it.toUserId == "a" })
    }

    @Test
    fun cycle_of_three_collapses_to_one_or_two() {
        // Classic: A owes B 10, B owes C 10, C owes A 10 → all zero after netting at person level
        // Represented as nets already (all zero) → no transfers
        val settled =
            mapOf(
                "a" to BigDecimal.ZERO.setScale(2),
                "b" to BigDecimal.ZERO.setScale(2),
                "c" to BigDecimal.ZERO.setScale(2),
            )
        assertTrue(DebtSimplifier.simplify(settled, "INR").isEmpty())
    }

    @Test
    fun simplify_all_keeps_currencies_separate() {
        val byCurrency =
            mapOf(
                "INR" to mapOf("a" to BigDecimal("10.00"), "b" to BigDecimal("-10.00")),
                "USD" to mapOf("a" to BigDecimal("-5.00"), "b" to BigDecimal("5.00")),
            )
        val transfers = DebtSimplifier.simplifyAll(byCurrency)
        assertEquals(2, transfers.size)
        assertTrue(transfers.any { it.currencyCode == "INR" && it.fromUserId == "b" })
        assertTrue(transfers.any { it.currencyCode == "USD" && it.fromUserId == "a" })
    }

    @Test
    fun empty_nets_yield_no_transfers() {
        assertTrue(DebtSimplifier.simplify(emptyMap(), "INR").isEmpty())
    }

    @Test
    fun from_expenses_keeps_pairwise_edges() {
        val expense =
            Expense(
                id = "e1",
                description = "Dinner",
                amount = BigDecimal("90.00"),
                currencyCode = "INR",
                paidByUserId = "a",
                expenseDateEpochMs = 0L,
                splitType = SplitType.EQUAL,
                createdAtEpochMs = 0L,
                updatedAtEpochMs = 0L,
            )
        val splits =
            mapOf(
                "e1" to
                    listOf(
                        ExpenseSplit("s1", "e1", "a", BigDecimal("30.00")),
                        ExpenseSplit("s2", "e1", "b", BigDecimal("30.00")),
                        ExpenseSplit("s3", "e1", "c", BigDecimal("30.00")),
                    ),
            )
        val transfers = DebtSimplifier.fromExpenses(listOf(expense), splits)
        assertEquals(2, transfers.size)
        assertTrue(transfers.any { it.fromUserId == "b" && it.toUserId == "a" && it.amount == BigDecimal("30.00") })
        assertTrue(transfers.any { it.fromUserId == "c" && it.toUserId == "a" && it.amount == BigDecimal("30.00") })
    }
}
