package com.splitease.app.domain.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountDeletionEligibilityTest {
    @Test
    fun zero_scale_2_does_not_block() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(slice("g1", "Roommates", mapOf("INR" to BigDecimal("0.00")))),
            )
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun sub_cent_rounds_to_zero_and_does_not_block() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(slice("g1", "Roommates", mapOf("INR" to BigDecimal("0.001")))),
            )
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun half_up_cent_blocks() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(slice("g1", "Roommates", mapOf("INR" to BigDecimal("0.005")))),
            )
        assertEquals(listOf(AccountDeletionBlockingGroup("g1", "Roommates")), blocked)
    }

    @Test
    fun compare_to_not_equals_operator_for_same_numeric_value() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(slice("g1", "Trip", mapOf("USD" to BigDecimal("0.0")))),
            )
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun negative_cent_blocks() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(slice("g1", "Home", mapOf("INR" to BigDecimal("-0.01")))),
            )
        assertEquals(1, blocked.size)
        assertEquals("Home", blocked[0].groupName)
    }

    @Test
    fun lists_only_groups_with_nonzero_nets() {
        val blocked =
            AccountDeletionEligibility.blockingGroups(
                listOf(
                    slice("g1", "Settled", mapOf("INR" to BigDecimal("0.00"))),
                    slice("g2", "Open", mapOf("INR" to BigDecimal("12.50"))),
                    slice("", "Non-group expenses", mapOf("USD" to BigDecimal("0.001"))),
                ),
            )
        assertEquals(listOf(AccountDeletionBlockingGroup("g2", "Open")), blocked)
    }

    private fun slice(
        id: String,
        name: String,
        nets: Map<String, BigDecimal>,
    ) = AccountDeletionBalanceSlice(groupId = id, groupName = name, netByCurrency = nets)
}
