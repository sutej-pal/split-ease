package com.splitease.app.data.social

import com.splitease.app.core.DialCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactIdentifierTest {
    @Test
    fun classify_email_phone_and_invalid() {
        assertEquals(ContactKind.EMAIL, ContactIdentifier.classify("Ada@Example.com"))
        assertEquals(ContactKind.EMAIL, ContactIdentifier.classify(" user@example.com "))
        assertEquals(ContactKind.INVALID, ContactIdentifier.classify("a@b"))
        assertEquals(ContactKind.INVALID, ContactIdentifier.classify("not-an-email"))
        assertEquals(ContactKind.INVALID, ContactIdentifier.classify("1234567"))
        assertEquals(ContactKind.PHONE, ContactIdentifier.classify("1234 5678"))
        assertEquals(ContactKind.PHONE, ContactIdentifier.classify("98765 43210"))
        assertEquals(ContactKind.PHONE, ContactIdentifier.classify("+44 7700 900123"))
    }

    @Test
    fun hasExplicitCountryCode_detects_plus_and_00() {
        assertTrue(ContactIdentifier.hasExplicitCountryCode("+44 7700 900123"))
        assertTrue(ContactIdentifier.hasExplicitCountryCode("  +91 98765"))
        assertTrue(ContactIdentifier.hasExplicitCountryCode("00919876543210"))
        assertTrue(ContactIdentifier.hasExplicitCountryCode("00 91 98765 43210"))
        assertFalse(ContactIdentifier.hasExplicitCountryCode("98765 43210"))
        assertFalse(ContactIdentifier.hasExplicitCountryCode("09876543210"))
        assertFalse(ContactIdentifier.hasExplicitCountryCode("ada@example.com"))
    }

    @Test
    fun normalizePhone_cases() {
        assertEquals("+919876543210", ContactIdentifier.normalizePhone("98765 43210"))
        assertEquals("+919876543210", ContactIdentifier.normalizePhone("+91 98765 43210"))
        assertEquals("+919876543210", ContactIdentifier.normalizePhone("09876543210"))
        assertEquals("+919876543210", ContactIdentifier.normalizePhone("0091 98765 43210"))
        assertEquals(
            "+447700900123",
            ContactIdentifier.normalizePhone("7700 900123", dialCode = "+44"),
        )
        assertEquals("+447700900123", ContactIdentifier.normalizePhone("+44 7700 900123"))
        assertNull(ContactIdentifier.normalizePhone("12345"))
        assertNull(ContactIdentifier.normalizePhone("abcdefgh"))
        assertNull(ContactIdentifier.normalizePhone("98abc76543210"))
    }

    @Test
    fun toStoredContact_keeps_email_case_and_wraps_phone() {
        assertEquals("Ada@Example.com", ContactIdentifier.toStoredContact(" Ada@Example.com "))
        assertEquals(
            "+919876543210@${ContactIdentifier.MOBILE_CONTACT_DOMAIN}",
            ContactIdentifier.toStoredContact("98765 43210"),
        )
        assertEquals(
            "+447700900123@${ContactIdentifier.MOBILE_CONTACT_DOMAIN}",
            ContactIdentifier.toStoredContact("7700900123", dialCode = "+44"),
        )
        val stored = "+919876543210@${ContactIdentifier.MOBILE_CONTACT_DOMAIN}"
        assertEquals(stored, ContactIdentifier.toStoredContact(stored))
        assertNull(ContactIdentifier.toStoredContact("nope"))
        assertNull(ContactIdentifier.toStoredContact(""))
    }

    @Test
    fun placeholder_real_email_and_legacy_phone() {
        val stored = "+919876543210@mobile.splitease.com"
        assertTrue(ContactIdentifier.isMobilePlaceholder(stored))
        assertTrue(ContactIdentifier.isMobilePlaceholder(stored.uppercase()))
        assertFalse(ContactIdentifier.isMobilePlaceholder("ada@example.com"))
        assertFalse(ContactIdentifier.isMobilePlaceholder("abc@mobile.splitease.com"))
        assertFalse(ContactIdentifier.isRealEmail(stored))
        assertFalse(ContactIdentifier.isRealEmail("group-share@splitease.invalid"))
        assertFalse(ContactIdentifier.isRealEmail("9876543210"))
        assertTrue(ContactIdentifier.isRealEmail("Ada@Example.com"))
        assertTrue(ContactIdentifier.isLegacyRawPhone("9876543210"))
        assertTrue(ContactIdentifier.isLegacyRawPhone("+919876543210"))
        assertFalse(ContactIdentifier.isLegacyRawPhone(stored))
        assertFalse(ContactIdentifier.isLegacyRawPhone("123"))
        assertEquals("+919876543210", ContactIdentifier.phoneFromStored(stored))
        assertEquals("+919876543210", ContactIdentifier.phoneFromStored("9876543210"))
        assertNull(ContactIdentifier.phoneFromStored("ada@example.com"))
    }

    @Test
    fun splitDialCode_uses_longest_prefix() {
        assertEquals("+91" to "9876543210", ContactIdentifier.splitDialCode("+919876543210"))
        assertEquals("+44" to "7700900123", ContactIdentifier.splitDialCode("+447700900123"))
        assertEquals("+1" to "5551234567", ContactIdentifier.splitDialCode("+15551234567"))
        assertEquals("+971" to "501234567", ContactIdentifier.splitDialCode("+971501234567"))
        assertNull(ContactIdentifier.splitDialCode("+99912345678"))
        assertTrue(DialCodes.codesLongestFirst().first() == "+971")
    }

    @Test
    fun displayContact_hides_placeholder_domain() {
        assertEquals(
            "+91 98765 43210",
            ContactIdentifier.displayContact("+919876543210@mobile.splitease.com"),
        )
        assertEquals("+91 98765 43210", ContactIdentifier.displayContact("9876543210"))
        assertEquals("Ada@Example.com", ContactIdentifier.displayContact("Ada@Example.com"))
    }

    @Test
    fun confirm_length_counts_dial_code_and_digits() {
        assertTrue(ContactIdentifier.isConfirmablePhoneLength("+91", "70172 22030"))
        assertFalse(ContactIdentifier.isConfirmablePhoneLength("+91", "123"))
        assertTrue(ContactIdentifier.matchesPhoneQuery("+919876543210@mobile.splitease.com", "98765 43210"))
        assertFalse(ContactIdentifier.matchesPhoneQuery("+919876543210@mobile.splitease.com", "sam"))
    }
}
