package com.splitease.app.domain.imports

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * One row parsed from a CSV bank/statement export.
 *
 * @property dateEpochMs Business date (UTC midnight of the parsed date when possible).
 * @property description Merchant / memo.
 * @property amount Absolute amount (always positive).
 * @property currencyCode ISO code or null to use app default.
 * @property categoryName Optional category label to match or create.
 * @property rawLine Original CSV line for debugging.
 */
data class ImportedTransaction(
    val dateEpochMs: Long,
    val description: String,
    val amount: BigDecimal,
    val currencyCode: String? = null,
    val categoryName: String? = null,
    val rawLine: String,
)

/**
 * Parses a simple CSV of transactions.
 *
 * Expected header (case-insensitive):
 * `date,description,amount[,currency][,category]`
 *
 * Dates: `yyyy-MM-dd` or `dd/MM/yyyy`. Amounts may include a leading `-` (ignored for magnitude).
 */
object CsvTransactionParser {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * @param csv Full file text.
     * @param defaultCurrency Used when a row omits currency.
     * @return Parsed rows (empty lines skipped).
     * @throws IllegalArgumentException on missing header or unreadable rows.
     */
    fun parse(
        csv: String,
        defaultCurrency: String = "INR",
    ): List<ImportedTransaction> {
        val lines =
            csv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        require(lines.isNotEmpty()) { "CSV is empty." }
        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        require(header.contains("date") && header.contains("description") && header.contains("amount")) {
            "CSV header must include date, description, amount."
        }
        val dateIdx = header.indexOf("date")
        val descIdx = header.indexOf("description")
        val amountIdx = header.indexOf("amount")
        val currencyIdx = header.indexOf("currency").takeIf { it >= 0 }
        val categoryIdx = header.indexOf("category").takeIf { it >= 0 }

        return lines.drop(1).mapIndexed { index, line ->
            val cols = splitCsvLine(line)
            fun col(i: Int): String = cols.getOrNull(i)?.trim().orEmpty()
            val description = col(descIdx)
            require(description.isNotBlank()) { "Row ${index + 2}: description is blank." }
            val amountRaw = col(amountIdx).replace(",", "").removePrefix("+")
            val amount =
                runCatching {
                    BigDecimal(amountRaw).abs().setScale(2, RoundingMode.HALF_UP)
                }.getOrElse {
                    throw IllegalArgumentException("Row ${index + 2}: invalid amount '$amountRaw'")
                }
            require(amount > BigDecimal.ZERO) { "Row ${index + 2}: amount must be > 0." }
            val currency =
                currencyIdx?.let { col(it) }?.uppercase()?.ifBlank { null }
                    ?: defaultCurrency.uppercase()
            val category = categoryIdx?.let { col(it) }?.ifBlank { null }
            ImportedTransaction(
                dateEpochMs = parseDate(col(dateIdx), index + 2),
                description = description,
                amount = amount,
                currencyCode = currency,
                categoryName = category,
                rawLine = line,
            )
        }
    }

    private fun parseDate(raw: String, rowNumber: Int): Long {
        val date =
            try {
                LocalDate.parse(raw, iso)
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(raw, dmy)
                } catch (_: DateTimeParseException) {
                    throw IllegalArgumentException("Row $rowNumber: invalid date '$raw'")
                }
            }
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    /** Splits a CSV line on commas, respecting simple double quotes. */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
