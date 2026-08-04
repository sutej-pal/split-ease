package com.splitease.app.domain.balance

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
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

    /**
     * Builds who-owes-whom from individual expenses (no cross-member simplification).
     *
     * Each split participant owes the payer their share. Opposing edges for the same
     * pair+currency are netted. Settlements adjust the pairwise bags.
     *
     * @param expenses Group (or scoped) expenses.
     * @param splitsByExpenseId Split lines keyed by expense id.
     * @param payments Settlements in the same scope.
     * @return Pairwise transfers sorted by currency then from/to ids.
     */
    fun fromExpenses(
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        payments: List<Payment> = emptyList(),
    ): List<DebtTransfer> {
        val bags = mutableMapOf<PairKey, BigDecimal>()
        expenses.forEach { expense ->
            val payer = expense.paidByUserId
            splitsByExpenseId[expense.id].orEmpty().forEach { split ->
                if (split.userId == payer) return@forEach
                val amount = split.owedAmount.setScale(2, RoundingMode.HALF_UP)
                if (amount.compareTo(ZERO) == 0) return@forEach
                addEdge(bags, split.userId, payer, expense.currencyCode, amount)
            }
        }
        payments.forEach { payment ->
            val amount = payment.amount.setScale(2, RoundingMode.HALF_UP)
            // fromUser paid toUser ⇒ reduces from→to debt (or creates to→from credit)
            addEdge(bags, payment.toUserId, payment.fromUserId, payment.currencyCode, amount)
        }
        return bags
            .mapNotNull { (key, amount) ->
                val net = amount.setScale(2, RoundingMode.HALF_UP)
                when {
                    net > ZERO ->
                        DebtTransfer(key.fromUserId, key.toUserId, net, key.currencyCode)
                    net < ZERO ->
                        DebtTransfer(key.toUserId, key.fromUserId, net.abs(), key.currencyCode)
                    else -> null
                }
            }.sortedWith(
                compareBy<DebtTransfer> { it.currencyCode }
                    .thenBy { it.fromUserId }
                    .thenBy { it.toUserId },
            )
    }

    private data class PairKey(
        val fromUserId: String,
        val toUserId: String,
        val currencyCode: String,
    )

    /** Canonicalizes undirected pair storage so A→B and B→A share one bag. */
    private fun addEdge(
        bags: MutableMap<PairKey, BigDecimal>,
        fromUserId: String,
        toUserId: String,
        currencyCode: String,
        amount: BigDecimal,
    ) {
        if (fromUserId == toUserId) return
        val (low, high, sign) =
            if (fromUserId < toUserId) {
                Triple(fromUserId, toUserId, BigDecimal.ONE)
            } else {
                Triple(toUserId, fromUserId, BigDecimal.ONE.negate())
            }
        val key = PairKey(low, high, currencyCode)
        // Positive bag value means low owes high.
        val delta = amount.multiply(sign).setScale(2, RoundingMode.HALF_UP)
        bags[key] = bags.getOrDefault(key, ZERO).add(delta)
    }

    private class MutableAmount(
        var value: BigDecimal
    )

    private fun BigDecimal.toMutableAmount() = MutableAmount(this)
}
