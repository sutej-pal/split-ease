package com.splitease.app.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppCurrenciesTest {
    @Test
    fun catalog_covers_common_iso_set() {
        val codes = AppCurrencies.OPTIONS.map { it.first }
        assertTrue(AppCurrencies.OPTIONS.size >= 20)
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.containsAll(listOf(AppCurrencies.INR, AppCurrencies.USD, "EUR", "GBP", "JPY")))
        assertEquals(AppCurrencies.INR, AppCurrencies.DEFAULT)
        assertEquals(AppCurrencies.INR, codes.first())
    }

    @Test
    fun filter_matches_code_or_name() {
        val inr = AppCurrencies.filter("inr")
        assertTrue(inr.any { it.first == AppCurrencies.INR })
        val rupee = AppCurrencies.filter("rupee")
        assertTrue(rupee.any { it.first == AppCurrencies.INR })
        val usd = AppCurrencies.filter("dollar")
        assertTrue(usd.any { it.first == AppCurrencies.USD })
        val euro = AppCurrencies.filter("euro")
        assertTrue(euro.any { it.first == "EUR" })
    }

    @Test
    fun normalizeOrDefault_rejects_unknown() {
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault(null))
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault(" "))
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault("XYZ"))
        assertEquals("EUR", AppCurrencies.normalizeOrDefault("eur"))
        assertEquals(AppCurrencies.USD, AppCurrencies.normalizeOrDefault("usd"))
        assertFalse(AppCurrencies.isSupported("XYZ"))
        assertTrue(AppCurrencies.isSupported("EUR"))
        assertTrue(AppCurrencies.isSupported(AppCurrencies.INR))
    }

    @Test
    fun symbol_falls_back_to_code_when_unknown() {
        assertEquals("د.إ", AppCurrencies.symbol("AED"))
        assertTrue(AppCurrencies.symbol(AppCurrencies.INR).isNotBlank())
        assertTrue(AppCurrencies.symbol(AppCurrencies.USD, java.util.Locale.US).isNotBlank())
        assertEquals("XYZ", AppCurrencies.symbol("XYZ"))
        assertEquals(AppCurrencies.INR, AppCurrencies.symbol(" "))
    }
}
