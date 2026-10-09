package com.splitease.app.domain.payment

import com.splitease.app.domain.settings.AppCurrencies
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Region-aware payment deep-link / share payloads for settlements.
 *
 * These do not complete a payment — they open external apps or the share sheet.
 */
object PaymentDeepLinks {
    /**
     * Recommended pay actions for a settlement currency.
     */
    fun actionsForCurrency(currencyCode: String): List<PayActionKind> {
        val code = AppCurrencies.normalizeOrDefault(currencyCode)
        return when (code) {
            AppCurrencies.INR -> listOf(PayActionKind.UPI, PayActionKind.PAYPAL, PayActionKind.SHARE)
            AppCurrencies.USD -> listOf(PayActionKind.VENMO, PayActionKind.PAYPAL, PayActionKind.SHARE)
            else -> listOf(PayActionKind.PAYPAL, PayActionKind.SHARE)
        }
    }

    /**
     * UPI intent URI. [payeeVpa] may be blank — some UPI apps still open with amount only.
     */
    fun upiPayUri(
        amount: BigDecimal,
        currencyCode: String = AppCurrencies.INR,
        payeeName: String,
        payeeVpa: String = "",
        note: String = "SplitEase settlement",
    ): String {
        val am = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val pn = encode(payeeName)
        val tn = encode(note)
        val pa = encode(payeeVpa)
        val cu = AppCurrencies.normalizeOrDefault(currencyCode)
        return "upi://pay?pa=$pa&pn=$pn&am=$am&cu=$cu&tn=$tn"
    }

    /**
     * PayPal.me style HTTPS link (opens browser / PayPal app).
     *
     * @param paypalUsername Without `@`; when blank, falls back to PayPal send money page.
     */
    fun paypalUri(
        amount: BigDecimal,
        currencyCode: String,
        paypalUsername: String = "",
    ): String {
        val am = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val cu = AppCurrencies.normalizeOrDefault(currencyCode)
        val user = paypalUsername.trim().removePrefix("@")
        return if (user.isNotEmpty()) {
            "https://www.paypal.me/$user/$am$cu"
        } else {
            "https://www.paypal.com/myaccount/transfer/homepage/send"
        }
    }

    /**
     * Venmo deep link with amount note.
     *
     * @param venmoUsername Without `@`; blank opens Venmo home.
     */
    fun venmoUri(
        amount: BigDecimal,
        venmoUsername: String = "",
        note: String = "SplitEase",
    ): String {
        val am = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val user = venmoUsername.trim().removePrefix("@")
        val tn = encode(note)
        return if (user.isNotEmpty()) {
            "venmo://paycharge?txn=pay&recipients=$user&amount=$am&note=$tn"
        } else {
            "https://venmo.com/"
        }
    }

    /**
     * Plain-text payment request for the system share sheet.
     */
    fun shareText(
        amount: BigDecimal,
        currencyCode: String,
        counterpartyLabel: String,
        note: String? = null,
    ): String {
        val am = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val cu = AppCurrencies.normalizeOrDefault(currencyCode)
        val base = "SplitEase settlement: please pay $cu $am to settle up with $counterpartyLabel."
        return if (note.isNullOrBlank()) base else "$base Note: ${note.trim()}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

enum class PayActionKind {
    UPI,
    PAYPAL,
    VENMO,
    SHARE,
}
