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
    fun exports_header_blank_row_then_activity() {
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
                    ),
                zoneId = ZoneOffset.UTC,
            )

        val lines = csv.trimEnd().lines()
        val header = CsvTransactionParser.splitCsvLine(lines.first())
        assertEquals(
            listOf(
                "Date",
                "Description",
                "Category",
                "Cost",
                "Currency",
                "Paid by",
                "Alice",
                "Bob",
                "Notes",
            ),
            header,
        )
        assertEquals(csvRow(List(header.size) { "" }), lines[1])
        assertEquals(csvRow(List(header.size) { "" }), lines[4])

        fun col(
            row: List<String>,
            name: String,
        ): String = row[header.indexOf(name)]

        val expenseRow = CsvTransactionParser.splitCsvLine(lines[2])
        assertEquals("2026-01-15", col(expenseRow, "Date"))
        assertEquals("Dinner, cafe", col(expenseRow, "Description"))
        assertEquals("Food", col(expenseRow, "Category"))
        assertEquals("100.00", col(expenseRow, "Cost"))
        assertEquals("INR", col(expenseRow, "Currency"))
        assertEquals("Alice", col(expenseRow, "Paid by"))
        assertEquals("50.00", col(expenseRow, "Alice"))
        assertEquals("-50.00", col(expenseRow, "Bob"))
        assertEquals("", col(expenseRow, "Notes"))

        val paymentRow = CsvTransactionParser.splitCsvLine(lines[3])
        assertEquals("2026-01-20", col(paymentRow, "Date"))
        assertEquals("Settlement", col(paymentRow, "Description"))
        assertEquals("", col(paymentRow, "Category"))
        assertEquals("50.00", col(paymentRow, "Cost"))
        assertEquals("Bob", col(paymentRow, "Paid by"))
        assertEquals("-50.00", col(paymentRow, "Alice"))
        assertEquals("50.00", col(paymentRow, "Bob"))
        assertEquals("UPI", col(paymentRow, "Notes"))

        val totalRow = CsvTransactionParser.splitCsvLine(lines[5])
        assertEquals("2026-01-20", col(totalRow, "Date"))
        assertEquals("Total balance", col(totalRow, "Description"))
        assertEquals("", col(totalRow, "Category"))
        assertEquals("", col(totalRow, "Cost"))
        assertEquals("INR", col(totalRow, "Currency"))
        assertEquals("", col(totalRow, "Paid by"))
        assertEquals("0.00", col(totalRow, "Alice"))
        assertEquals("0.00", col(totalRow, "Bob"))
        assertEquals("", col(totalRow, "Notes"))
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
                                    amount = "10.50",
                                    paidBy = "a",
                                    date = noonUtc,
                                ),
                            ),
                        payments = emptyList(),
                        splitsByExpenseId =
                            mapOf("e1" to listOf(split("e1", "a", "10.50"))),
                        categoryNamesById = emptyMap(),
                    ),
                zoneId = ZoneOffset.UTC,
            )
        val header = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines().first())
        val expenseRow = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines()[2])
        assertEquals("'=1+1", expenseRow[header.indexOf("Description")])
        assertEquals("10.50", expenseRow[header.indexOf("Cost")])
        assertEquals("0.00", expenseRow[header.indexOf("Alice")])
        val totalRow = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines().last())
        assertEquals("Total balance", totalRow[header.indexOf("Description")])
        assertEquals("0.00", totalRow[header.indexOf("Alice")])
    }

    @Test
    fun multi_payer_member_columns_are_nets() {
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
                    ),
                zoneId = ZoneOffset.UTC,
            )
        val header = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines().first())
        val expenseRow = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines()[2])
        assertEquals("Taxi", expenseRow[header.indexOf("Description")])
        assertEquals("100.00", expenseRow[header.indexOf("Cost")])
        assertEquals("Alice:60.00; Bob:40.00", expenseRow[header.indexOf("Paid by")])
        assertEquals("10.00", expenseRow[header.indexOf("Alice")])
        assertEquals("-10.00", expenseRow[header.indexOf("Bob")])
        val totalRow = CsvTransactionParser.splitCsvLine(csv.trimEnd().lines().last())
        assertEquals("Total balance", totalRow[header.indexOf("Description")])
        assertEquals("2026-01-15", totalRow[header.indexOf("Date")])
        assertEquals("10.00", totalRow[header.indexOf("Alice")])
        assertEquals("-10.00", totalRow[header.indexOf("Bob")])
    }

    @Test
    fun total_balance_rows_are_per_currency() {
        val usdDate =
            LocalDate.of(2026, 2, 1).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val inrDate =
            LocalDate.of(2026, 2, 5).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
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
                                    description = "Hotel",
                                    amount = "100.00",
                                    paidBy = "a",
                                    date = usdDate,
                                    currency = "USD",
                                ),
                                expense(
                                    id = "e2",
                                    description = "Taxi",
                                    amount = "200.00",
                                    paidBy = "a",
                                    date = inrDate,
                                    currency = "INR",
                                ),
                            ),
                        payments = emptyList(),
                        splitsByExpenseId =
                            mapOf(
                                "e1" to
                                    listOf(
                                        split("e1", "a", "50.00"),
                                        split("e1", "b", "50.00"),
                                    ),
                                "e2" to
                                    listOf(
                                        split("e2", "a", "100.00"),
                                        split("e2", "b", "100.00"),
                                    ),
                            ),
                        categoryNamesById = emptyMap(),
                    ),
                zoneId = ZoneOffset.UTC,
            )

        val lines = csv.trimEnd().lines()
        val header = CsvTransactionParser.splitCsvLine(lines.first())
        fun col(row: List<String>, name: String): String = row[header.indexOf(name)]

        val totalRows = lines.drop(2).filter { col(CsvTransactionParser.splitCsvLine(it), "Description").startsWith("Total balance") }
        assertEquals(2, totalRows.size)

        val inrTotal = CsvTransactionParser.splitCsvLine(totalRows.first { col(CsvTransactionParser.splitCsvLine(it), "Currency") == "INR" })
        val usdTotal = CsvTransactionParser.splitCsvLine(totalRows.first { col(CsvTransactionParser.splitCsvLine(it), "Currency") == "USD" })
        assertEquals("Total balance (INR)", col(inrTotal, "Description"))
        assertEquals("Total balance (USD)", col(usdTotal, "Description"))
        assertEquals("2026-02-05", col(inrTotal, "Date"))
        assertEquals("2026-02-05", col(usdTotal, "Date"))
        assertEquals("100.00", col(inrTotal, "Alice"))
        assertEquals("-100.00", col(inrTotal, "Bob"))
        assertEquals("50.00", col(usdTotal, "Alice"))
        assertEquals("-50.00", col(usdTotal, "Bob"))
    }

    private fun expense(
        id: String,
        description: String,
        amount: String,
        paidBy: String,
        date: Long,
        currency: String = "INR",
    ) = Expense(
        id = id,
        description = description,
        amount = BigDecimal(amount),
        currencyCode = currency,
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
