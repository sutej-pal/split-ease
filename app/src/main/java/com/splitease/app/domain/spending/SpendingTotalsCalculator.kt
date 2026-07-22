package com.splitease.app.domain.spending

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One spending bucket for a category within a currency.
 *
 * @property categoryId Category id or null for uncategorized.
 * @property categoryName Display label.
 * @property currencyCode ISO 4217 code.
 * @property total Sum of the viewer's owed amounts.
 * @property expenseCount Number of expenses contributing.
 */
data class CategorySpending(
    val categoryId: String?,
    val categoryName: String,
    val currencyCode: String,
    val total: BigDecimal,
    val expenseCount: Int,
)

/**
 * Aggregates the viewer's share of expenses (split owed amounts) by category and currency.
 */
object SpendingTotalsCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * @param viewerUserId Whose spending to sum.
     * @param expenses Expenses in the period (already filtered).
     * @param splitsByExpenseId Split lines.
     * @param categoryNames categoryId → name.
     * @param uncategorizedLabel Label when [Expense.categoryId] is null.
     */
    fun byCategory(
        viewerUserId: String,
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        categoryNames: Map<String, String>,
        uncategorizedLabel: String = "Uncategorized",
    ): List<CategorySpending> {
        data class Acc(var total: BigDecimal = ZERO, var count: Int = 0)

        val buckets = mutableMapOf<Pair<String?, String>, Acc>()
        expenses.forEach { expense ->
            val owed =
                splitsByExpenseId[expense.id]
                    .orEmpty()
                    .firstOrNull { it.userId == viewerUserId }
                    ?.owedAmount
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?: return@forEach
            if (owed.compareTo(ZERO) == 0) return@forEach
            val key = expense.categoryId to expense.currencyCode
            val acc = buckets.getOrPut(key) { Acc() }
            acc.total = acc.total.add(owed)
            acc.count += 1
        }
        return buckets
            .map { (key, acc) ->
                val (categoryId, currency) = key
                CategorySpending(
                    categoryId = categoryId,
                    categoryName = categoryId?.let { categoryNames[it] } ?: uncategorizedLabel,
                    currencyCode = currency,
                    total = acc.total.setScale(2, RoundingMode.HALF_UP),
                    expenseCount = acc.count,
                )
            }.sortedWith(
                compareBy<CategorySpending> { it.currencyCode }
                    .thenByDescending { it.total },
            )
    }
}
