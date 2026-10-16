package com.splitease.app.domain.exports

import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Snapshot used to render a group ledger CSV.
 *
 * @property groupName Group display name (used by the filename, not the CSV body).
 * @property exportedAtEpochMs When the export was generated.
 * @property memberIdsInOrder Column order for per-member nets.
 * @property memberLabels userId → unique display label.
 * @property expenses Group expenses.
 * @property payments Group settlements.
 * @property splitsByExpenseId Split lines keyed by expense id.
 * @property categoryNamesById Category id → name.
 */
data class GroupLedgerExportInput(
    val groupName: String,
    val exportedAtEpochMs: Long,
    val memberIdsInOrder: List<String>,
    val memberLabels: Map<String, String>,
    val expenses: List<Expense>,
    val payments: List<Payment>,
    val splitsByExpenseId: Map<String, List<ExpenseSplit>>,
    val categoryNamesById: Map<String, String>,
)

/**
 * Builds a UTF-8 CSV of a group ledger as a **single table**.
 *
 * Header: `Date,Description,Category,Cost,Currency,Paid by`, then one column per
 * group member, then `Notes`.
 * Row 2 is blank. Following rows are expenses and payments mixed, oldest-first.
 * A blank row, then one total row per currency with the last ledger date and
 * each member's net in that currency (positive = gets back).
 * Member cells use the signed net from [BalanceCalculator] (positive = gets back).
 */
object GroupLedgerCsvExporter {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val ZERO = BigDecimal.ZERO.setScale(2)

    private val LEADING_HEADERS =
        listOf(
            "Date",
            "Description",
            "Category",
            "Cost",
            "Currency",
            "Paid by",
        )
    private const val NOTES_HEADER = "Notes"
    private const val TOTAL_BALANCE_DESCRIPTION = "Total balance"

    /**
     * @param input Ledger snapshot.
     * @param zoneId Time zone for activity dates.
     * @return CSV text without a BOM (callers may prepend one for Excel).
     */
    fun export(
        input: GroupLedgerExportInput,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val memberHeaders =
            input.memberIdsInOrder.map { id -> csvText(labelOf(id, input.memberLabels)) }
        val header = LEADING_HEADERS + memberHeaders + NOTES_HEADER
        val lines = mutableListOf<String>()
        lines += csvRow(header)
        lines += csvRow(List(header.size) { "" })

        val activities =
            activityRows(input)
                .sortedWith(compareBy<ActivityRow> { it.sortEpochMs }.thenBy { it.stableId })
        activities.forEach { row ->
            val nets =
                input.memberIdsInOrder.map { id -> money(row.memberNets[id] ?: ZERO) }
            lines +=
                csvRow(
                    listOf(
                        formatDate(row.sortEpochMs, zoneId),
                        csvText(row.description),
                        csvText(row.category),
                        money(row.amount),
                        row.currencyCode,
                        csvText(row.paidBy),
                    ) + nets + csvText(row.notes),
                )
        }
        lastActivityEpochMs(activities)?.let { lastActivityAt ->
            lines += csvRow(List(header.size) { "" })
            totalBalanceRows(
                activities = activities,
                memberIds = input.memberIdsInOrder,
                lastActivityAt = lastActivityAt,
                zoneId = zoneId,
            ).forEach { row -> lines += csvRow(row) }
        }
        return lines.joinToString("\n") + "\n"
    }

    private data class ActivityRow(
        val stableId: String,
        val sortEpochMs: Long,
        val description: String,
        val category: String,
        val amount: BigDecimal,
        val currencyCode: String,
        val paidBy: String,
        val notes: String,
        val memberNets: Map<String, BigDecimal>,
    )

    private fun activityRows(input: GroupLedgerExportInput): List<ActivityRow> {
        val expenses =
            input.expenses.map { expense ->
                val splits = input.splitsByExpenseId[expense.id].orEmpty()
                val nets =
                    input.memberIdsInOrder.associateWith { userId ->
                        BalanceCalculator.viewerNetForExpense(userId, expense, splits)
                    }
                ActivityRow(
                    stableId = "expense-${expense.id}",
                    sortEpochMs =
                        expense.expenseDateEpochMs.takeIf { it > 0L }
                            ?: expense.createdAtEpochMs,
                    description = expense.description,
                    category = expense.categoryId?.let { input.categoryNamesById[it] }.orEmpty(),
                    amount = expense.amount,
                    currencyCode = expense.currencyCode,
                    paidBy = paidByLabel(expense, splits, input.memberLabels),
                    notes = expense.notes.orEmpty(),
                    memberNets = nets,
                )
            }
        val payments =
            input.payments.map { payment ->
                val nets =
                    input.memberIdsInOrder.associateWith { userId ->
                        when (userId) {
                            payment.fromUserId -> payment.amount.setScale(2, RoundingMode.HALF_UP)
                            payment.toUserId ->
                                payment.amount.negate().setScale(2, RoundingMode.HALF_UP)
                            else -> ZERO
                        }
                    }
                ActivityRow(
                    stableId = "payment-${payment.id}",
                    sortEpochMs = payment.paidAtEpochMs.coerceAtLeast(payment.createdAtEpochMs),
                    description = "Settlement",
                    category = "",
                    amount = payment.amount,
                    currencyCode = payment.currencyCode,
                    paidBy = labelOf(payment.fromUserId, input.memberLabels),
                    notes = payment.note.orEmpty(),
                    memberNets = nets,
                )
            }
        return expenses + payments
    }

    private fun paidByLabel(
        expense: Expense,
        splits: List<ExpenseSplit>,
        labels: Map<String, String>,
    ): String {
        val multiPayer = splits.any { it.paidAmount != null }
        if (!multiPayer) return labelOf(expense.paidByUserId, labels)
        val payers =
            splits.mapNotNull { split ->
                val paid = split.paidAmount?.setScale(2, RoundingMode.HALF_UP) ?: return@mapNotNull null
                if (paid.compareTo(ZERO) == 0) {
                    null
                } else {
                    "${labelOf(split.userId, labels)}:${money(paid)}"
                }
            }
        return payers.joinToString("; ").ifBlank { labelOf(expense.paidByUserId, labels) }
    }

    private fun labelOf(
        userId: String,
        labels: Map<String, String>,
    ): String = labels[userId]?.ifBlank { null } ?: userId.take(8)

    private fun money(amount: BigDecimal): String =
        amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun lastActivityEpochMs(activities: List<ActivityRow>): Long? =
        activities.maxOfOrNull { it.sortEpochMs }

    private fun totalBalanceRows(
        activities: List<ActivityRow>,
        memberIds: List<String>,
        lastActivityAt: Long,
        zoneId: ZoneId,
    ): List<List<String>> {
        if (activities.isEmpty()) return emptyList()
        val currencies =
            activities.map { it.currencyCode }.distinct().sorted()
        val date = formatDate(lastActivityAt, zoneId)
        return currencies.map { currency ->
            val inCurrency = activities.filter { it.currencyCode == currency }
            val totals =
                memberIds.map { id ->
                    money(
                        inCurrency.fold(ZERO) { acc, row ->
                            acc.add(row.memberNets[id] ?: ZERO)
                        },
                    )
                }
            val description =
                if (currencies.size == 1) {
                    TOTAL_BALANCE_DESCRIPTION
                } else {
                    "$TOTAL_BALANCE_DESCRIPTION ($currency)"
                }
            listOf(
                date,
                csvText(description),
                "",
                "",
                currency,
                "",
            ) + totals + ""
        }
    }

    private fun formatDate(
        epochMs: Long,
        zoneId: ZoneId,
    ): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(DATE)
}

internal fun csvRow(vararg cells: String): String = csvRow(cells.asList())

internal fun csvRow(cells: List<String>): String = cells.joinToString(",") { csvEncode(it) }

/**
 * Prefixes Excel/LibreOffice formula characters so user text cannot execute as a formula.
 * Numeric cells must not use this (signed amounts start with `-`).
 */
internal fun csvText(value: String): String = guardCsvFormula(value)

internal fun guardCsvFormula(value: String): String {
    val first = value.firstOrNull() ?: return value
    return if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
        "'$value"
    } else {
        value
    }
}

internal fun csvEncode(value: String): String {
    val needsQuotes =
        value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
