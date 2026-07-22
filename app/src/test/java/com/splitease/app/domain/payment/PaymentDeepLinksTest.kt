package com.splitease.app.domain.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaymentDeepLinksTest {
    @Test
    fun inr_prefers_upi() {
        val actions = PaymentDeepLinks.actionsForCurrency("INR")
        assertEquals(PayActionKind.UPI, actions.first())
    }

    @Test
    fun usd_prefers_venmo() {
        assertEquals(PayActionKind.VENMO, PaymentDeepLinks.actionsForCurrency("usd").first())
    }

    @Test
    fun upi_uri_contains_amount_and_currency() {
        val uri =
            PaymentDeepLinks.upiPayUri(
                amount = BigDecimal("42.50"),
                payeeName = "Alex",
            )
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("am=42.50"))
        assertTrue(uri.contains("cu=INR"))
    }

    @Test
    fun paypal_me_with_username() {
        val uri =
            PaymentDeepLinks.paypalUri(
                amount = BigDecimal("10.00"),
                currencyCode = "USD",
                paypalUsername = "alex",
            )
        assertEquals("https://www.paypal.me/alex/10.00USD", uri)
    }

    @Test
    fun share_text_includes_amount() {
        val text =
            PaymentDeepLinks.shareText(
                amount = BigDecimal("5"),
                currencyCode = "EUR",
                counterpartyLabel = "Sam",
            )
        assertTrue(text.contains("EUR"))
        assertTrue(text.contains("5.00"))
        assertTrue(text.contains("Sam"))
    }
}
