package com.splitease.app.domain.imports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CsvTransactionParserTest {
    @Test
    fun parses_header_and_rows() {
        val csv =
            """
            date,description,amount,currency,category
            2026-01-15,Coffee,-120.50,INR,Food
            15/02/2026,"Lunch, cafe",40,USD,Food
            """.trimIndent()
        val rows = CsvTransactionParser.parse(csv, defaultCurrency = "INR")
        assertEquals(2, rows.size)
        assertEquals("Coffee", rows[0].description)
        assertEquals(BigDecimal("120.50"), rows[0].amount)
        assertEquals("INR", rows[0].currencyCode)
        assertEquals("Food", rows[0].categoryName)
        assertEquals("Lunch, cafe", rows[1].description)
        assertEquals("USD", rows[1].currencyCode)
    }

    @Test
    fun rejects_missing_header() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvTransactionParser.parse("a,b,c\n1,2,3")
        }
    }
}
