package com.splitease.app.domain.balance

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Minimizes the number of settlement transfers for a set of net balances.
 *
 * Uses greedy matching of largest debtors to largest creditors. Deterministic
 * when user ids are sorted as a tie-break.
 */
object DebtSimplifier {
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * Converts net balances into a minimal list of [DebtTransfer]s.
     *
     * @param netByUser userId → net (positive = creditor, negative = debtor).
     * @param currencyCode Currency for resulting transfers.
     * @return Suggested payments (from owes → to is owed). Empty when settled.
     */
    fun simplify(
        netByUser: Map<String, BigDecimal>,
        currencyCode: String,
    ): List<DebtTransfer> {
        val debtors =
            netByUser
                .filterValues { it < ZERO }
                .map { (id, net) -> id to net.abs().setScale(2, RoundingMode.HALF_UP) }
                .sortedWith(compareByDescending<Pair<String, BigDecimal>> { it.second }.thenBy { it.first })
                .map { it.first to it.second.toMutableAmount() }
                .toMutableList()

        val creditors =
            netByUser
                .filterValues { it > ZERO }
                .map { (id, net) -> id to net.setScale(2, RoundingMode.HALF_UP) }
                .sortedWith(compareByDescending<Pair<String, BigDecimal>> { it.second }.thenBy { it.first })
                .map { it.first to it.second.toMutableAmount() }
                .toMutableList()

        val transfers = mutableListOf<DebtTransfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (debtorId, debtLeft) = debtors[i]
            val (creditorId, creditLeft) = creditors[j]
            val pay = minOf(debtLeft.value, creditLeft.value)
            if (pay > ZERO) {
                transfers +=
                    DebtTransfer(
                        fromUserId = debtorId,
                        toUserId = creditorId,
                        amount = pay.setScale(2, RoundingMode.HALF_UP),
                        currencyCode = currencyCode,
                    )
                debtLeft.value = debtLeft.value.subtract(pay)
                creditLeft.value = creditLeft.value.subtract(pay)
            }
            if (debtLeft.value.compareTo(ZERO) == 0) i++
            if (creditLeft.value.compareTo(ZERO) == 0) j++
        }
        return transfers
    }

    /**
     * Simplifies each currency bucket independently.
     *
     * @param netsByCurrency currencyCode → (userId → net).
     * @return All suggested transfers across currencies.
     */
    fun simplifyAll(netsByCurrency: Map<String, Map<String, BigDecimal>>): List<DebtTransfer> =
        netsByCurrency.flatMap { (currency, nets) -> simplify(nets, currency) }

    private class MutableAmount(var value: BigDecimal)

    private fun BigDecimal.toMutableAmount() = MutableAmount(this)
}
