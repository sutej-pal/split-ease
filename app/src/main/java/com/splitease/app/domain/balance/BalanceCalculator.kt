package com.splitease.app.domain.balance

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Derives net balances from expenses and splits.
 *
 * **Convention:** net > 0 ⇒ user is owed money; net < 0 ⇒ user owes money.
 * Per expense: payer is credited [Expense.amount]; each split participant is
 * debited their [ExpenseSplit.owedAmount].
 *
 * Multi-currency: nets are computed separately per [Expense.currencyCode]
 * (no FX conversion).
 */
object BalanceCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * Computes net balance per user for expenses already filtered to one currency.
     *
     * @param expenses Expenses in a single currency.
     * @param splitsByExpenseId Split lines keyed by expense id.
     * @return userId → net (scale 2); zeros omitted.
     */
    fun netBalances(
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
    ): Map<String, BigDecimal> {
        val nets = mutableMapOf<String, BigDecimal>()
        expenses.forEach { expense ->
            val credit = expense.amount.setScale(2, RoundingMode.HALF_UP)
            nets[expense.paidByUserId] =
                nets.getOrDefault(expense.paidByUserId, ZERO).add(credit)
            val splits = splitsByExpenseId[expense.id].orEmpty()
            splits.forEach { split ->
                val debit = split.owedAmount.setScale(2, RoundingMode.HALF_UP)
                nets[split.userId] = nets.getOrDefault(split.userId, ZERO).subtract(debit)
            }
        }
        return nets
            .mapValues { (_, v) -> v.setScale(2, RoundingMode.HALF_UP) }
            .filterValues { it.compareTo(ZERO) != 0 }
    }

    /**
     * Groups [expenses] by currency, then computes nets for each bucket.
     *
     * @param expenses Any mix of currencies.
     * @param splitsByExpenseId Split lines keyed by expense id.
     * @return currencyCode → (userId → net).
     */
    fun netBalancesByCurrency(
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
    ): Map<String, Map<String, BigDecimal>> {
        return expenses
            .groupBy { it.currencyCode }
            .mapValues { (_, currencyExpenses) ->
                netBalances(currencyExpenses, splitsByExpenseId)
            }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Pairwise net for [viewerUserId] relative to [otherUserId] from shared expenses.
     *
     * Positive ⇒ [otherUserId] owes [viewerUserId]; negative ⇒ viewer owes other.
     * Only expenses where both users appear (as payer or split participant) are included.
     *
     * @return currencyCode → net from viewer perspective.
     */
    fun pairwiseNetByCurrency(
        viewerUserId: String,
        otherUserId: String,
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
    ): Map<String, BigDecimal> {
        val shared =
            expenses.filter { expense ->
                val participants =
                    buildSet {
                        add(expense.paidByUserId)
                        addAll(splitsByExpenseId[expense.id].orEmpty().map { it.userId })
                    }
                viewerUserId in participants && otherUserId in participants
            }
        return netBalancesByCurrency(shared, splitsByExpenseId)
            .mapNotNull { (currency, nets) ->
                val net = nets[viewerUserId] ?: return@mapNotNull null
                if (net.compareTo(ZERO) == 0) null else currency to net
            }.toMap()
    }
}
