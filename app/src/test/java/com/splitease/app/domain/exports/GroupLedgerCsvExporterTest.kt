package com.splitease.app.domain.exports

import com.splitease.app.domain.imports.CsvTransactionParser
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class GroupLedgerCsvExporterTest {
    private val noonUtc =
        LocalDate.of(2026, 1, 15).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val laterUtc =
        LocalDate.of(2026, 1, 20).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val exportedAt =
        LocalDateTime.of(2026, 8, 19, 1, 33).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun exports_activities_with_member_nets_and_balances() {
        val csv =
            GroupLedgerCsvExporter.export(
                input =
                    GroupLedgerExportInput(
                        groupName = "Weekend, trip",
                        exportedAtEpochMs = exportedAt,
                        memberIdsInOrder = listOf("a", "b"),
                        memberLabels = mapOf("a" to "Alice", "b" to "Bob"),
                        expenses =
                            listOf(
                                expense(
                                    id = "e1",
                                    description = "Dinner, cafe",
                                    amount = "100.00",
                                    paidBy = "a",
                                    date = noonUtc,
                                ),
                            ),
                        payments =
                            listOf(
                                payment(
                                    id = "p1",
                                    from = "b",
                                    to = "a",
                                    amount = "50.00",
                                    date = laterUtc,
                                    note = "UPI",
                                ),
                            ),
                        splitsByExpenseId =
                            mapOf(
                                "e1" to
                                    listOf(
                                        split("e1", "a", "50.00"),
                                        split("e1", "b", "50.00"),
                                    ),
                            ),
                        categoryNamesById = mapOf("cat_food" to "Food"),
                        balances =
                            listOf(
                                GroupLedgerExportBalance("Alice", "INR", BigDecimal("0.00")),
                                GroupLedgerExportBalance("Bob", "INR", BigDecimal("0.00")),
                            ),
                        suggestedSettlements = emptyList(),
                    ),
                zoneId = ZoneOffset.UTC,
            )

        val lines = csv.trim().lines()
        assertEquals(listOf("Group", "Weekend, trip"), CsvTransactionParser.splitCsvLine(lines[0]))
        assertEquals("Exported", CsvTransactionParser.splitCsvLine(lines[1])[0])
        assertEquals("Activities", lines[3])

        val header = CsvTransactionParser.splitCsvLine(lines[4])
        assertEquals(
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
                "Alice",
                "Bob",
            ),
            header,
        )

        val expenseRow = CsvTransactionParser.splitCsvLine(lines[5])
        assertEquals("2026-01-15", expenseRow[0])
        assertEquals("expense", expenseRow[1])
        assertEquals("Dinner, cafe", expenseRow[2])
        assertEquals("Food", expenseRow[3])
        assertEquals("100.00", expenseRow[4])
        assertEquals("INR", expenseRow[5])
        assertEquals("Alice", expenseRow[6])
        assertEquals("equal", expenseRow[10])
        assertEquals("50.00", expenseRow[11])
        assertEquals("-50.00", expenseRow[12])

        val paymentRow = CsvTransactionParser.splitCsvLine(lines[6])
        assertEquals("2026-01-20", paymentRow[0])
        assertEquals("payment", paymentRow[1])
        assertEquals("UPI", paymentRow[2])
        assertEquals("50.00", paymentRow[4])
        assertEquals("Bob", paymentRow[7])
        assertEquals("Alice", paymentRow[8])
        assertEquals("-50.00", paymentRow[11])
        assertEquals("50.00", paymentRow[12])

        assertTrue(lines.contains("Balances"))
        val balanceHeaderIndex = lines.indexOf("Balances") + 1
        assertEquals(
            listOf("member", "currency", "net", "status"),
            CsvTransactionParser.splitCsvLine(lines[balanceHeaderIndex]),
        )
        assertEquals(
            listOf("Alice", "INR", "0.00", "settled"),
            CsvTransactionParser.splitCsvLine(lines[balanceHeaderIndex + 1]),
        )
    }

    @Test
    fun quotes_commas_and_quotes_in_fields() {
        val encoded = csvEncode("Dinner, \"cafe\"")
        assertEquals("\"Dinner, \"\"cafe\"\"\"", encoded)
        assertEquals(
            listOf("Dinner, \"cafe\""),
            CsvTransactionParser.splitCsvLine(encoded),
        )
    }

    @Test
    fun prefixes_formula_injection_in_text_but_not_signed_amounts() {
        assertEquals("'=CMD|'/C calc'!A0", guardCsvFormula("=CMD|'/C calc'!A0"))
        assertEquals("'+1+1", guardCsvFormula("+1+1"))
        val csv =
            GroupLedgerCsvExporter.export(
                input =
                    GroupLedgerExportInput(
                        groupName = "Trip",
                        exportedAtEpochMs = exportedAt,
                        memberIdsInOrder = listOf("a"),
                        memberLabels = mapOf("a" to "Alice"),
                        expenses =
                            listOf(
                                expense(
                                    id = "e1",
                                    description = "=1+1",
                                    amount = "10.00",
                                    paidBy = "a",
                                    date = noonUtc,
                                ),
                            ),
                        payments = emptyList(),
                        splitsByExpenseId =
                            mapOf("e1" to listOf(split("e1", "a", "10.00"))),
                        categoryNamesById = emptyMap(),
                        balances =
                            listOf(GroupLedgerExportBalance("Alice", "INR", BigDecimal("-10.00"))),
                        suggestedSettlements = emptyList(),
                    ),
                zoneId = ZoneOffset.UTC,
            )
        val expenseRow = CsvTransactionParser.splitCsvLine(csv.lines().first { it.startsWith("2026-01-15,expense") })
        assertEquals("'=1+1", expenseRow[2])
        assertEquals("0.00", expenseRow[11])
        assertTrue(csv.lines().any { it == "Alice,INR,-10.00,owes" })
    }

    @Test
    fun multi_payer_lists_each_payer_amount() {
        val csv =
            GroupLedgerCsvExporter.export(
                input =
                    GroupLedgerExportInput(
                        groupName = "Trip",
                        exportedAtEpochMs = exportedAt,
                        memberIdsInOrder = listOf("a", "b"),
                        memberLabels = mapOf("a" to "Alice", "b" to "Bob"),
                        expenses =
                            listOf(
                                expense(
                                    id = "e1",
                                    description = "Taxi",
                                    amount = "100.00",
                                    paidBy = "a",
                                    date = noonUtc,
                                ),
                            ),
                        payments = emptyList(),
                        splitsByExpenseId =
                            mapOf(
                                "e1" to
                                    listOf(
                                        split("e1", "a", "50.00", paid = "60.00"),
                                        split("e1", "b", "50.00", paid = "40.00"),
                                    ),
                            ),
                        categoryNamesById = mapOf("cat_food" to "Food"),
                        balances = emptyList(),
                        suggestedSettlements = emptyList(),
                    ),
                zoneId = ZoneOffset.UTC,
            )
        val expenseRow = CsvTransactionParser.splitCsvLine(csv.lines().first { it.startsWith("2026-01-15,expense") })
        assertEquals("Alice:60.00; Bob:40.00", expenseRow[6])
        assertEquals("10.00", expenseRow[11])
        assertEquals("-10.00", expenseRow[12])
    }

    @Test
    fun suggested_settlements_use_signed_status() {
        val csv =
            GroupLedgerCsvExporter.export(
                input =
                    GroupLedgerExportInput(
                        groupName = "Trip",
                        exportedAtEpochMs = exportedAt,
                        memberIdsInOrder = listOf("a", "b"),
                        memberLabels = mapOf("a" to "Alice", "b" to "Bob"),
                        expenses = emptyList(),
                        payments = emptyList(),
                        splitsByExpenseId = emptyMap(),
                        categoryNamesById = emptyMap(),
                        balances =
                            listOf(
                                GroupLedgerExportBalance("Alice", "INR", BigDecimal("40.00")),
                                GroupLedgerExportBalance("Bob", "INR", BigDecimal("-40.00")),
                            ),
                        suggestedSettlements =
                            listOf(
                                GroupLedgerExportSettlement(
                                    fromLabel = "Bob",
                                    toLabel = "Alice",
                                    amount = BigDecimal("40.00"),
                                    currencyCode = "INR",
                                ),
                            ),
                    ),
                zoneId = ZoneOffset.UTC,
            )
        val lines = csv.trim().lines()
        assertTrue(lines.any { it == "Bob,INR,-40.00,owes" })
        assertTrue(lines.any { it == "Alice,INR,40.00,gets_back" })
        val settleIndex = lines.indexOf("Suggested settlements")
        assertEquals(
            listOf("Bob", "Alice", "40.00", "INR"),
            CsvTransactionParser.splitCsvLine(lines[settleIndex + 2]),
        )
    }

    private fun expense(
        id: String,
        description: String,
        amount: String,
        paidBy: String,
        date: Long,
    ) = Expense(
        id = id,
        description = description,
        amount = BigDecimal(amount),
        currencyCode = "INR",
        categoryId = "cat_food",
        paidByUserId = paidBy,
        groupId = "g1",
        expenseDateEpochMs = date,
        splitType = SplitType.EQUAL,
        createdAtEpochMs = date,
        updatedAtEpochMs = date,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun payment(
        id: String,
        from: String,
        to: String,
        amount: String,
        date: Long,
        note: String,
    ) = Payment(
        id = id,
        fromUserId = from,
        toUserId = to,
        amount = BigDecimal(amount),
        currencyCode = "INR",
        groupId = "g1",
        note = note,
        paidAtEpochMs = date,
        createdAtEpochMs = date,
        updatedAtEpochMs = date,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun split(
        expenseId: String,
        userId: String,
        owed: String,
        paid: String? = null,
    ) = ExpenseSplit(
        id = "$expenseId-$userId",
        expenseId = expenseId,
        userId = userId,
        owedAmount = BigDecimal(owed),
        paidAmount = paid?.let { BigDecimal(it) },
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
}
