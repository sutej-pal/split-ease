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
 * @property groupName Group display name.
 * @property exportedAtEpochMs When the export was generated.
 * @property memberIdsInOrder Column order for per-member activity nets.
 * @property memberLabels userId → unique display label.
 * @property expenses Group expenses.
 * @property payments Group settlements.
 * @property splitsByExpenseId Split lines keyed by expense id.
 * @property categoryNamesById Category id → name.
 * @property balances Current member nets (signed; positive = gets back).
 * @property suggestedSettlements Minimized who-owes-whom rows.
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
    val balances: List<GroupLedgerExportBalance>,
    val suggestedSettlements: List<GroupLedgerExportSettlement>,
)

/**
 * One member's current net in a currency.
 *
 * @property memberLabel Display name.
 * @property currencyCode ISO 4217 code.
 * @property net Positive = gets back; negative = owes.
 */
data class GroupLedgerExportBalance(
    val memberLabel: String,
    val currencyCode: String,
    val net: BigDecimal,
)

/**
 * A suggested settlement transfer.
 *
 * @property fromLabel Debtor.
 * @property toLabel Creditor.
 * @property amount Positive amount to transfer.
 * @property currencyCode ISO 4217 code.
 */
data class GroupLedgerExportSettlement(
    val fromLabel: String,
    val toLabel: String,
    val amount: BigDecimal,
    val currencyCode: String,
)

/**
 * Builds a UTF-8 CSV of group activities (expenses + payments) with per-member
 * balance impact, plus current nets and suggested settlements.
 *
 * Activity rows are oldest-first. Member columns use the signed net convention
 * from [BalanceCalculator] (positive = owed to that member).
 */
object GroupLedgerCsvExporter {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val EXPORTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * @param input Ledger snapshot.
     * @param zoneId Time zone for activity dates and the export timestamp.
     * @return CSV text without a BOM (callers may prepend one for Excel).
     */
    fun export(
        input: GroupLedgerExportInput,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val lines = mutableListOf<String>()
        lines += csvRow("Group", csvText(input.groupName))
        lines += csvRow("Exported", formatExportedAt(input.exportedAtEpochMs, zoneId))
        lines += ""
        lines += "Activities"
        val memberHeaders =
            input.memberIdsInOrder.map { id -> csvText(labelOf(id, input.memberLabels)) }
        lines +=
            csvRow(
                listOf(
                    "date",
                    "type",
                    "description",
                    "category",
                    "amount",
                    "currency",
                    "paid_by",
                    "from",
                    "to",
                    "notes",
                    "split_type",
                ) + memberHeaders,
            )
        activityRows(input)
            .sortedWith(compareBy<ActivityRow> { it.sortEpochMs }.thenBy { it.stableId })
            .forEach { row ->
                val memberNets =
                    input.memberIdsInOrder.map { id -> money(row.memberNets[id] ?: ZERO) }
                lines +=
                    csvRow(
                        listOf(
                            formatDate(row.sortEpochMs, zoneId),
                            row.type,
                            csvText(row.description),
                            csvText(row.category),
                            money(row.amount),
                            row.currencyCode,
                            csvText(row.paidBy),
                            csvText(row.from),
                            csvText(row.to),
                            csvText(row.notes),
                            row.splitType,
                        ) + memberNets,
                    )
            }
        lines += ""
        lines += "Balances"
        lines += csvRow("member", "currency", "net", "status")
        input.balances
            .sortedWith(
                compareBy<GroupLedgerExportBalance> { it.memberLabel.lowercase() }
                    .thenBy { it.currencyCode },
            ).forEach { balance ->
                lines +=
                    csvRow(
                        csvText(balance.memberLabel),
                        balance.currencyCode,
                        money(balance.net),
                        statusOf(balance.net),
                    )
            }
        lines += ""
        lines += "Suggested settlements"
        lines += csvRow("from", "to", "amount", "currency")
        input.suggestedSettlements.forEach { settlement ->
            lines +=
                csvRow(
                    csvText(settlement.fromLabel),
                    csvText(settlement.toLabel),
                    money(settlement.amount),
                    settlement.currencyCode,
                )
        }
        return lines.joinToString("\n") + "\n"
    }

    private data class ActivityRow(
        val stableId: String,
        val sortEpochMs: Long,
        val type: String,
        val description: String,
        val category: String,
        val amount: BigDecimal,
        val currencyCode: String,
        val paidBy: String,
        val from: String,
        val to: String,
        val notes: String,
        val splitType: String,
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
                    type = "expense",
                    description = expense.description,
                    category = expense.categoryId?.let { input.categoryNamesById[it] }.orEmpty(),
                    amount = expense.amount,
                    currencyCode = expense.currencyCode,
                    paidBy = paidByLabel(expense, splits, input.memberLabels),
                    from = "",
                    to = "",
                    notes = expense.notes.orEmpty(),
                    splitType = expense.splitType.name.lowercase(),
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
                    type = "payment",
                    description = payment.note.orEmpty().ifBlank { "Settlement" },
                    category = "",
                    amount = payment.amount,
                    currencyCode = payment.currencyCode,
                    paidBy = "",
                    from = labelOf(payment.fromUserId, input.memberLabels),
                    to = labelOf(payment.toUserId, input.memberLabels),
                    notes = payment.note.orEmpty(),
                    splitType = "",
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
                if (paid.compareTo(ZERO) == 0) null
                else "${labelOf(split.userId, labels)}:${money(paid)}"
            }
        return payers.joinToString("; ").ifBlank { labelOf(expense.paidByUserId, labels) }
    }

    private fun labelOf(
        userId: String,
        labels: Map<String, String>,
    ): String = labels[userId]?.ifBlank { null } ?: userId.take(8)

    private fun money(amount: BigDecimal): String =
        amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun statusOf(net: BigDecimal): String =
        when {
            net.compareTo(ZERO) > 0 -> "gets_back"
            net.compareTo(ZERO) < 0 -> "owes"
            else -> "settled"
        }

    private fun formatDate(
        epochMs: Long,
        zoneId: ZoneId,
    ): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(DATE)

    private fun formatExportedAt(
        epochMs: Long,
        zoneId: ZoneId,
    ): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(EXPORTED_AT)
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
