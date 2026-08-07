package com.splitease.app.domain.spending

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

/**
 * One calendar month of group spending for charts / period totals.
 *
 * @property year Full year (e.g. 2026).
 * @property month Zero-based month ([Calendar.MONTH]).
 * @property totalSpent Sum of expense amounts in [currencyCode].
 * @property yourShare Viewer’s owed share in [currencyCode].
 * @property currencyCode ISO 4217 code used for the bucket.
 */
data class GroupMonthSpending(
    val year: Int,
    val month: Int,
    val totalSpent: BigDecimal,
    val yourShare: BigDecimal,
    val currencyCode: String,
)

/**
 * Aggregates group expense totals and the viewer’s share for a period / chart.
 */
object GroupSpendingCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

    /**
     * Sums total spent and the viewer’s share for expenses in [fromEpochMs]..[toEpochMs]
     * matching [currencyCode].
     */
    fun periodTotals(
        viewerUserId: String,
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        currencyCode: String,
        fromEpochMs: Long,
        toEpochMs: Long,
    ): Pair<BigDecimal, BigDecimal> {
        var total = ZERO
        var share = ZERO
        expenses.forEach { expense ->
            if (expense.currencyCode != currencyCode) return@forEach
            if (expense.expenseDateEpochMs < fromEpochMs || expense.expenseDateEpochMs > toEpochMs) {
                return@forEach
            }
            total = total.add(expense.amount.setScale(2, RoundingMode.HALF_UP))
            val owed =
                splitsByExpenseId[expense.id]
                    .orEmpty()
                    .firstOrNull { it.userId == viewerUserId }
                    ?.owedAmount
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?: ZERO
            share = share.add(owed)
        }
        return total to share
    }

    /**
     * Builds [monthCount] consecutive month buckets ending at [endYear]/[endMonth]
     * (inclusive), using [currencyCode] only.
     */
    fun monthlyBuckets(
        viewerUserId: String,
        expenses: List<Expense>,
        splitsByExpenseId: Map<String, List<ExpenseSplit>>,
        currencyCode: String,
        endYear: Int,
        endMonth: Int,
        monthCount: Int = 3,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<GroupMonthSpending> {
        require(monthCount > 0)
        val cal =
            Calendar.getInstance(timeZone).apply {
                set(Calendar.YEAR, endYear)
                set(Calendar.MONTH, endMonth)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        // Walk backwards then reverse so list is chronological.
        val months = ArrayList<Pair<Int, Int>>(monthCount)
        repeat(monthCount) {
            months.add(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
            cal.add(Calendar.MONTH, -1)
        }
        months.reverse()
        return months.map { (year, month) ->
            val (from, to) = monthBounds(year, month, timeZone)
            val (total, share) =
                periodTotals(
                    viewerUserId = viewerUserId,
                    expenses = expenses,
                    splitsByExpenseId = splitsByExpenseId,
                    currencyCode = currencyCode,
                    fromEpochMs = from,
                    toEpochMs = to,
                )
            GroupMonthSpending(
                year = year,
                month = month,
                totalSpent = total,
                yourShare = share,
                currencyCode = currencyCode,
            )
        }
    }

    /**
     * Inclusive start / end epoch ms for a calendar month.
     */
    fun monthBounds(
        year: Int,
        month: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Pair<Long, Long> {
        val start =
            Calendar.getInstance(timeZone).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val end =
            Calendar.getInstance(timeZone).apply {
                timeInMillis = start.timeInMillis
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
        return start.timeInMillis to end.timeInMillis
    }

    /**
     * Percent of [totalSpent] represented by [yourShare], or null when total is zero.
     */
    fun sharePercent(
        totalSpent: BigDecimal,
        yourShare: BigDecimal,
    ): Int? {
        if (totalSpent.compareTo(BigDecimal.ZERO) == 0) return null
        return yourShare
            .multiply(BigDecimal(100))
            .divide(totalSpent, 0, RoundingMode.HALF_UP)
            .toInt()
            .coerceIn(0, 100)
    }
}
