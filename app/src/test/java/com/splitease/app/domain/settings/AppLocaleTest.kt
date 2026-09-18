package com.splitease.app.domain.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLocaleTest {
    @Test
    fun fromStorage_parsesNameAndTag() {
        assertEquals(AppLocale.SYSTEM, AppLocale.fromStorage(null))
        assertEquals(AppLocale.ENGLISH, AppLocale.fromStorage("ENGLISH"))
        assertEquals(AppLocale.HINDI, AppLocale.fromStorage("hi"))
        assertEquals(AppLocale.SYSTEM, AppLocale.fromStorage("unknown"))
    }
}
