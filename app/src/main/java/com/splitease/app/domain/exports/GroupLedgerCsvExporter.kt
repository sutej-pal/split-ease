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
 * Header: `Date,Description,Category,Cost,Currency` plus one column per member.
 * Row 2 is blank. Following rows are expenses then payments, oldest-first.
 * Member cells use the signed net from [BalanceCalculator] (positive = gets back).
 */
object GroupLedgerCsvExporter {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val ZERO = BigDecimal.ZERO.setScale(2)

    private val FIXED_HEADERS =
        listOf(
            "Date",
            "Description",
            "Category",
            "Cost",
            "Currency",
        )

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
        val header = FIXED_HEADERS + memberHeaders
        val lines = mutableListOf<String>()
        lines += csvRow(header)
        lines += csvRow(List(header.size) { "" })

        activityRows(input)
            .sortedWith(compareBy<ActivityRow> { it.sortEpochMs }.thenBy { it.stableId })
            .forEach { row ->
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
                        ) + nets,
                    )
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
                    description = payment.note.orEmpty().ifBlank { "Settlement" },
                    category = "",
                    amount = payment.amount,
                    currencyCode = payment.currencyCode,
                    memberNets = nets,
                )
            }
        return expenses + payments
    }

    private fun labelOf(
        userId: String,
        labels: Map<String, String>,
    ): String = labels[userId]?.ifBlank { null } ?: userId.take(8)

    private fun money(amount: BigDecimal): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP)
        return if (scaled.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            scaled.toBigInteger().toString()
        } else {
            scaled.toPlainString()
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
