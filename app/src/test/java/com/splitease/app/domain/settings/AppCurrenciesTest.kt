package com.splitease.app.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppCurrenciesTest {
    @Test
    fun catalog_only_usd_and_inr() {
        assertEquals(2, AppCurrencies.OPTIONS.size)
        assertEquals(
            setOf(AppCurrencies.INR, AppCurrencies.USD),
            AppCurrencies.OPTIONS.map { it.first }.toSet(),
        )
        assertEquals(AppCurrencies.INR, AppCurrencies.DEFAULT)
    }

    @Test
    fun filter_matches_code_or_name() {
        val inr = AppCurrencies.filter("inr")
        assertTrue(inr.any { it.first == AppCurrencies.INR })
        val rupee = AppCurrencies.filter("rupee")
        assertTrue(rupee.any { it.first == AppCurrencies.INR })
        val usd = AppCurrencies.filter("dollar")
        assertTrue(usd.any { it.first == AppCurrencies.USD })
    }

    @Test
    fun normalizeOrDefault_rejects_unknown() {
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault(null))
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault(" "))
        assertEquals(AppCurrencies.INR, AppCurrencies.normalizeOrDefault("EUR"))
        assertEquals(AppCurrencies.USD, AppCurrencies.normalizeOrDefault("usd"))
        assertFalse(AppCurrencies.isSupported("EUR"))
        assertTrue(AppCurrencies.isSupported(AppCurrencies.INR))
    }
}
