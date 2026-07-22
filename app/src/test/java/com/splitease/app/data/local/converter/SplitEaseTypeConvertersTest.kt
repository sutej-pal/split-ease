package com.splitease.app.data.local.converter

import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SplitEaseTypeConvertersTest {
    private val converters = SplitEaseTypeConverters()

    @Test
    fun `bigDecimal round-trips with plain string preserving scale`() {
        val amount = BigDecimal("100.00")
        val stored = converters.fromBigDecimal(amount)
        assertEquals("100.00", stored)
        assertEquals(0, amount.compareTo(converters.toBigDecimal(stored)))
    }

    @Test
    fun `bigDecimal handles repeating thirds without binary float error`() {
        val third = BigDecimal("33.333333333333333333")
        val restored = converters.toBigDecimal(converters.fromBigDecimal(third))
        assertEquals(0, third.compareTo(restored))
    }

    @Test
    fun `null bigDecimal maps to null`() {
        assertNull(converters.fromBigDecimal(null))
        assertNull(converters.toBigDecimal(null))
    }

    @Test
    fun `enums round-trip by name`() {
        assertEquals(SplitType.PERCENTAGE, converters.toSplitType(converters.fromSplitType(SplitType.PERCENTAGE)))
        assertEquals(SyncStatus.PENDING, converters.toSyncStatus(converters.fromSyncStatus(SyncStatus.PENDING)))
    }
}
