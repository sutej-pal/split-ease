package com.splitease.app.data.social

import android.util.Patterns
import com.splitease.app.core.DialCodes
import java.util.regex.Pattern

/**
 * How a raw contact string should be stored and shown.
 */
enum class ContactKind {
    EMAIL,
    PHONE,
    INVALID,
}

/**
 * Single classifier for friend contacts.
 *
 * Phones are stored as `<e164>@mobile.splitease.com`. Real emails keep the typed case.
 * Older rows may still hold a raw phone string; those are treated as phones and are not migrated.
 */
object ContactIdentifier {
    const val MOBILE_CONTACT_DOMAIN = "mobile.splitease.com"
    const val DEFAULT_DIAL_CODE = "+91"

    private val E164 = Regex("^\\+[0-9]{8,15}$")

    /**
     * Android's email pattern is null in local unit-test stubs. Fall back to the same shape
     * so classification still rejects addresses without a dotted domain.
     */
    private val EMAIL_FALLBACK: Pattern =
        Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}" +
                "@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
        )

    /**
     * Classifies [raw]. An `@` means email (must match [Patterns.EMAIL_ADDRESS]).
     * Otherwise eight or more digits means phone.
     */
    fun classify(raw: String): ContactKind {
        val trimmed = raw.trim()
        if (trimmed.contains("@")) {
            return if (isEmailAddress(trimmed)) ContactKind.EMAIL else ContactKind.INVALID
        }
        val digits = trimmed.count { it.isDigit() }
        return if (digits >= 8) ContactKind.PHONE else ContactKind.INVALID
    }

    /** True when the trimmed input starts with `+` or the `00` international prefix. */
    fun hasExplicitCountryCode(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("+") || trimmed.startsWith("00")
    }

    /**
     * Builds an E.164 number, or null when the result is not `+` plus 8–15 digits.
     *
     * Explicit `+` is kept. A leading `00` becomes `+`. Otherwise one leading `0` is
     * dropped and [dialCode] is prepended.
     */
    fun normalizePhone(raw: String, dialCode: String = DEFAULT_DIAL_CODE): String? {
        val compact = compactPhone(raw)
        if (compact.isEmpty()) return null
        val withPlus =
            when {
                compact.startsWith("+") -> compact
                compact.startsWith("00") -> "+" + compact.drop(2)
                else -> {
                    val national = if (compact.startsWith("0")) compact.drop(1) else compact
                    canonicalDial(dialCode) + national
                }
            }
        return withPlus.takeIf { E164.matches(it) }
    }

    /**
     * Storage form used by friends, users, and invites.
     *
     * Emails keep trimmed original case. Phones become `<e164>@mobile.splitease.com`.
     * Already-stored mobile placeholders are returned unchanged in shape. Null when invalid.
     */
    fun toStoredContact(raw: String, dialCode: String = DEFAULT_DIAL_CODE): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (isMobilePlaceholder(trimmed)) {
            val phone = phoneFromStored(trimmed) ?: return null
            return "$phone@$MOBILE_CONTACT_DOMAIN"
        }
        return when (classify(trimmed)) {
            ContactKind.EMAIL -> trimmed
            ContactKind.PHONE -> {
                val phone = normalizePhone(trimmed, dialCode) ?: return null
                "$phone@$MOBILE_CONTACT_DOMAIN"
            }
            ContactKind.INVALID -> null
        }
    }

    /** True when [stored] is `<e164>@mobile.splitease.com` with a valid local part. */
    fun isMobilePlaceholder(stored: String): Boolean {
        val trimmed = stored.trim()
        val at = trimmed.lastIndexOf('@')
        if (at <= 0) return false
        val domain = trimmed.substring(at + 1)
        if (!domain.equals(MOBILE_CONTACT_DOMAIN, ignoreCase = true)) return false
        return E164.matches(trimmed.substring(0, at))
    }

    /**
     * E.164 phone from a mobile placeholder or a legacy raw phone string, or null.
     */
    fun phoneFromStored(stored: String): String? {
        val trimmed = stored.trim()
        if (isMobilePlaceholder(trimmed)) {
            return trimmed.substringBefore("@").takeIf { E164.matches(it) }
        }
        if (isLegacyRawPhone(trimmed)) {
            return normalizePhone(trimmed)
        }
        return null
    }

    /**
     * A deliverable email: has `@`, is not a mobile placeholder, and is not a
     * `@splitease.invalid` group-share placeholder.
     */
    fun isRealEmail(stored: String): Boolean {
        val trimmed = stored.trim()
        if (!trimmed.contains("@")) return false
        if (isMobilePlaceholder(trimmed)) return false
        if (trimmed.endsWith("@splitease.invalid", ignoreCase = true)) return false
        return true
    }

    /** Older rows stored the phone with no `@` and at least eight digits. */
    fun isLegacyRawPhone(stored: String): Boolean {
        val trimmed = stored.trim()
        if (trimmed.contains("@")) return false
        return trimmed.count { it.isDigit() } >= 8
    }

    /**
     * Longest dial-code prefix of [e164], plus the remaining national digits.
     * Null when [e164] does not start with a known code.
     */
    fun splitDialCode(e164: String): Pair<String, String>? {
        val trimmed = e164.trim()
        if (!trimmed.startsWith("+")) return null
        val code =
            DialCodes.codesLongestFirst().firstOrNull { trimmed.startsWith(it) }
                ?: return null
        val national = trimmed.removePrefix(code)
        if (national.isEmpty() || national.any { !it.isDigit() }) return null
        return code to national
    }

    /**
     * User-visible contact. Real emails stay as stored. Phones render as
     * `+91 98765 43210` (dial code, then the national number).
     */
    fun displayContact(stored: String): String {
        val trimmed = stored.trim()
        if (trimmed.isEmpty()) return ""
        val phone = phoneFromStored(trimmed) ?: return trimmed
        return formatPhone(phone)
    }

    /** True when [dialCode] plus the digits in [number] total 8–15. */
    fun isConfirmablePhoneLength(dialCode: String, number: String): Boolean {
        val total = dialCode.count { it.isDigit() } + number.count { it.isDigit() }
        return total in 8..15
    }

    /**
     * True when [query] digits appear in the stored phone. Queries with no digits do not match.
     */
    fun matchesPhoneQuery(stored: String, query: String): Boolean {
        val phone = phoneFromStored(stored) ?: return false
        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        return phone.filter { it.isDigit() }.contains(digits)
    }

    private fun formatPhone(e164: String): String {
        val split = splitDialCode(e164) ?: return e164
        val (code, national) = split
        val grouped =
            if (national.length == 10) {
                "${national.take(5)} ${national.drop(5)}"
            } else {
                national
            }
        return "$code $grouped"
    }

    private fun isEmailAddress(value: String): Boolean {
        val pattern = Patterns.EMAIL_ADDRESS ?: EMAIL_FALLBACK
        return pattern.matcher(value).matches()
    }

    private fun compactPhone(raw: String): String =
        buildString {
            for (ch in raw.trim()) {
                when (ch) {
                    ' ', '-', '.', '(', ')' -> Unit
                    else -> append(ch)
                }
            }
        }

    private fun canonicalDial(dialCode: String): String {
        val digits = dialCode.filter { it.isDigit() }
        return "+$digits"
    }
}
