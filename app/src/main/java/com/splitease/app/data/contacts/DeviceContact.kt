package com.splitease.app.data.contacts

/**
 * A row from the device address book.
 *
 * @property id Stable contacts provider id.
 * @property displayName Contact name (maybe blank).
 * @property phoneNumbers All phone numbers for this contact.
 * @property emails All email addresses for this contact.
 */
data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
) {
    /** Primary phone, if any. */
    val phoneNumber: String?
        get() = phoneNumbers.firstOrNull()

    /** Primary email, if any. */
    val email: String?
        get() = emails.firstOrNull()

    /** Best single line for search matching. */
    fun searchable(): String =
        listOf(displayName)
            .plus(phoneNumbers)
            .plus(emails)
            .joinToString(" ")
            .lowercase()
}
