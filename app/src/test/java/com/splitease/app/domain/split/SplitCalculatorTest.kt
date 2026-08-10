package com.splitease.app.domain.split

import com.splitease.app.domain.model.SplitType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SplitCalculatorTest {
    @Test
    fun equal_splits_rupees_one_hundred_by_three_with_remainder() {
        val result =
            SplitCalculator.calculate(
                total = BigDecimal("100.00"),
                splitType = SplitType.EQUAL,
                participantIds = listOf("a", "b", "c"),
            )
        assertEquals(BigDecimal("33.34"), result["a"])
        assertEquals(BigDecimal("33.33"), result["b"])
        assertEquals(BigDecimal("33.33"), result["c"])
        assertEquals(BigDecimal("100.00"), result.values.reduce { acc, v -> acc.add(v) })
    }

    @Test
    fun unequal_requires_exact_sum() {
        val result =
            SplitCalculator.calculate(
                total = BigDecimal("50.00"),
                splitType = SplitType.UNEQUAL,
                participantIds = listOf("a", "b"),
                unequalAmounts = mapOf("a" to BigDecimal("20.00"), "b" to BigDecimal("30.00")),
            )
        assertEquals(BigDecimal("20.00"), result["a"])
        assertEquals(BigDecimal("30.00"), result["b"])
    }

    @Test
    fun unequal_rejects_bad_sum() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitCalculator.calculate(
                total = BigDecimal("50.00"),
                splitType = SplitType.UNEQUAL,
                participantIds = listOf("a", "b"),
                unequalAmounts = mapOf("a" to BigDecimal("20.00"), "b" to BigDecimal("20.00")),
            )
        }
    }

    @Test
    fun percentage_splits_even() {
        val result =
            SplitCalculator.calculate(
                total = BigDecimal("100.00"),
                splitType = SplitType.PERCENTAGE,
                participantIds = listOf("a", "b"),
                percentages =
                    mapOf(
                        "a" to BigDecimal("40"),
                        "b" to BigDecimal("60"),
                    ),
            )
        assertEquals(BigDecimal("40.00"), result["a"])
        assertEquals(BigDecimal("60.00"), result["b"])
    }

    @Test
    fun shares_proportional() {
        val result =
            SplitCalculator.calculate(
                total = BigDecimal("90.00"),
                splitType = SplitType.SHARES,
                participantIds = listOf("a", "b", "c"),
                shares = mapOf("a" to 1, "b" to 2, "c" to 3),
            )
        assertEquals(BigDecimal("15.00"), result["a"])
        assertEquals(BigDecimal("30.00"), result["b"])
        assertEquals(BigDecimal("45.00"), result["c"])
    }

    @Test
    fun adjustment_adds_extra_then_splits_remainder() {
        val result =
            SplitCalculator.calculate(
                total = BigDecimal("100.00"),
                splitType = SplitType.ADJUSTMENT,
                participantIds = listOf("a", "b", "c", "d"),
                adjustments =
                    mapOf(
                        "a" to BigDecimal("10.00"),
                        "b" to BigDecimal("0"),
                        "c" to BigDecimal("0"),
                        "d" to BigDecimal("0"),
                    ),
            )
        assertEquals(BigDecimal("32.50"), result["a"])
        assertEquals(BigDecimal("22.50"), result["b"])
        assertEquals(BigDecimal("22.50"), result["c"])
        assertEquals(BigDecimal("22.50"), result["d"])
    }
}
