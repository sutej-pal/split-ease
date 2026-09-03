package com.splitease.app.presentation.common

import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Formats money using the app-wide currency code (symbol when available).
 */
object MoneyFormat {
    fun format(
        amount: BigDecimal,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val symbol =
            runCatching {
                Currency.getInstance(currencyCode).getSymbol(locale)
            }.getOrElse { currencyCode }
        val normalized = amount.abs().setScale(2, java.math.RoundingMode.HALF_UP)
        val value = normalized.toPlainString()
        return "$symbol$value"
    }
}
