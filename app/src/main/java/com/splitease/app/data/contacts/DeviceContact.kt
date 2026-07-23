package com.splitease.app.data.contacts

/**
 * A row from the device address book.
 *
 * @property id Stable contacts provider id.
 * @property displayName Contact name (may be blank).
 * @property phoneNumber Primary phone, if any.
 * @property email Primary email, if any.
 */
data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String?,
    val email: String?,
) {
    /** Best single line for search matching. */
    fun searchable(): String =
        listOfNotNull(displayName, email, phoneNumber)
            .joinToString(" ")
            .lowercase()
}
