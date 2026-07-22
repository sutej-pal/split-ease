package com.splitease.app.domain.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppCurrenciesTest {
    @Test
    fun catalog_has_at_least_100_currencies() {
        assertTrue(AppCurrencies.OPTIONS.size >= 100)
    }

    @Test
    fun filter_matches_code_or_name() {
        val inr = AppCurrencies.filter("inr")
        assertTrue(inr.any { it.first == "INR" })
        val rupee = AppCurrencies.filter("rupee")
        assertTrue(rupee.any { it.first == "INR" })
    }
}
