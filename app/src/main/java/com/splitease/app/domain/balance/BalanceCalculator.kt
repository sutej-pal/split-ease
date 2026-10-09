package com.splitease.app.domain.balance

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Derives net balances from expenses and splits.
 *
 * **Convention:** net > 0 ⇒ user is owed money; net < 0 ⇒ user owes money.
 * Per expense: each payer is credited their paid amount (or the full
 * [Expense.amount] to [Expense.paidByUserId] in legacy single-payer mode);
 * each split participant is debited their [ExpenseSplit.owedAmount].
 *
 * Settlements ([Payment]): [Payment.fromUserId] gains +amount (debt reduced);
 * [Payment.toUserId] gains −amount (credit reduced).
 *
 * Multi-currency: nets are computed separately per [Expense.currencyCode]
 * (no FX conversion).
 */
object BalanceCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * Net for [userId] on a single expense (paid − owed).
     *
     * Multi-payer: credits [ExpenseSplit.paidAmount] when any split has a non-null paid
     * amount; otherwise credits the full [Expense.amount] to [Expense.paidByUserId].
     * Debits the viewer's [ExpenseSplit.owedAmount] (zero if not on the split list).
     *
     * @return Scale-2 net; may be zero when the viewer is fully settled on this expense.
     */
    fun viewerNetForExpense(
        userId: String,
        expense: Expense,
        splits: List<ExpenseSplit>,
    ): BigDecimal {
        val multiPayer = splits.any { it.paidAmount != null }
        val mySplit = splits.firstOrNull { it.userId == userId }
        val paid =
            if (multiPayer) {
                (mySplit?.paidAmount ?: ZERO).setScale(2, RoundingMode.HALF_UP)
            } else if (expense.paidByUserId == userId) {
                expense.amount.setScale(2, RoundingMode.HALF_UP)
            } else {
                ZERO
            }
        val owed =
            mySplit?.owedAmount?.setScale(2, RoundingMode.HALF_UP) ?: ZERO
        return paid.subtract(owed).setScale(2, RoundingMode.HALF_UP)
    }

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
            val splits = splitsByExpenseId[expense.id].orEmpty()
            val multiPayer = splits.any { it.paidAmount != null }
            if (multiPayer) {
                splits.forEach { split ->
                    val credit =
                        (split.paidAmount ?: ZERO).setScale(2, RoundingMode.HALF_UP)
                    if (credit.compareTo(ZERO) != 0) {
                        nets[split.userId] = nets.getOrDefault(split.userId, ZERO).add(credit)
                    }
                }
            } else {
                val credit = expense.amount.setScale(2, RoundingMode.HALF_UP)
                nets[expense.paidByUserId] =
                    nets.getOrDefault(expense.paidByUserId, ZERO).add(credit)
            }
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
            }.filterValues { it.isNotEmpty() }
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
        payments: List<Payment> = emptyList(),
    ): Map<String, BigDecimal> {
        val shared =
            expenses.filter { expense ->
                val participants =
                    buildSet {
                        add(expense.paidByUserId)
                        addAll(splitsByExpenseId[expense.id].orEmpty().map { it.userId })
                        splitsByExpenseId[expense.id].orEmpty().forEach { split ->
                            if (split.paidAmount != null &&
                                split.paidAmount.compareTo(BigDecimal.ZERO) != 0
                            ) {
                                add(split.userId)
                            }
                        }
                    }
                viewerUserId in participants && otherUserId in participants
            }
        val pairPayments =
            payments.filter { payment ->
                val pair = setOf(payment.fromUserId, payment.toUserId)
                pair == setOf(viewerUserId, otherUserId)
            }
        return applyPayments(netBalancesByCurrency(shared, splitsByExpenseId), pairPayments)
            .mapNotNull { (currency, nets) ->
                val net = nets[viewerUserId] ?: return@mapNotNull null
                if (net.compareTo(ZERO) == 0) null else currency to net
            }.toMap()
    }

    /**
     * Applies settlements on top of expense nets.
     *
     * @param netsByCurrency Expense-derived nets by currency.
     * @param payments Settlements to apply (already scoped by caller).
     * @return Adjusted nets; zero entries omitted.
     */
    fun applyPayments(
        netsByCurrency: Map<String, Map<String, BigDecimal>>,
        payments: List<Payment>,
    ): Map<String, Map<String, BigDecimal>> {
        if (payments.isEmpty()) return netsByCurrency
        val mutable =
            netsByCurrency
                .mapValues { (_, nets) -> nets.toMutableMap() }
                .toMutableMap()
        payments.forEach { payment ->
            val amount = payment.amount.setScale(2, RoundingMode.HALF_UP)
            val bucket = mutable.getOrPut(payment.currencyCode) { mutableMapOf() }
            bucket[payment.fromUserId] =
                bucket.getOrDefault(payment.fromUserId, ZERO).add(amount)
            bucket[payment.toUserId] =
                bucket.getOrDefault(payment.toUserId, ZERO).subtract(amount)
        }
        return mutable
            .mapValues { (_, nets) ->
                nets
                    .mapValues { (_, v) -> v.setScale(2, RoundingMode.HALF_UP) }
                    .filterValues { it.compareTo(ZERO) != 0 }
            }.filterValues { it.isNotEmpty() }
    }
}
