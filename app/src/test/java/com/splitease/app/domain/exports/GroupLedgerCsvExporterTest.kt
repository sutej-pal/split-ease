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
            listOf("Date", "Description", "Category", "Cost", "Currency", "Alice", "Bob"),
            header,
        )
        assertEquals(",,,,,,", lines[1])
        assertTrue(lines.drop(2).none { it.isBlank() })

        fun col(
            row: List<String>,
            name: String,
        ): String = row[header.indexOf(name)]

        val expenseRow = CsvTransactionParser.splitCsvLine(lines[2])
        assertEquals("2026-01-15", col(expenseRow, "Date"))
        assertEquals("Dinner, cafe", col(expenseRow, "Description"))
        assertEquals("Food", col(expenseRow, "Category"))
        assertEquals("100", col(expenseRow, "Cost"))
        assertEquals("INR", col(expenseRow, "Currency"))
        assertEquals("50", col(expenseRow, "Alice"))
        assertEquals("-50", col(expenseRow, "Bob"))

        val paymentRow = CsvTransactionParser.splitCsvLine(lines[3])
        assertEquals("2026-01-20", col(paymentRow, "Date"))
        assertEquals("UPI", col(paymentRow, "Description"))
        assertEquals("", col(paymentRow, "Category"))
        assertEquals("50", col(paymentRow, "Cost"))
        assertEquals("-50", col(paymentRow, "Alice"))
        assertEquals("50", col(paymentRow, "Bob"))
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
        assertEquals("0", expenseRow[header.indexOf("Alice")])
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
        assertEquals("100", expenseRow[header.indexOf("Cost")])
        assertEquals("10", expenseRow[header.indexOf("Alice")])
        assertEquals("-10", expenseRow[header.indexOf("Bob")])
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
