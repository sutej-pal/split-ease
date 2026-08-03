package com.splitease.app.presentation.friends

import com.splitease.app.data.contacts.DeviceContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draft friend invite shown on the Review screen before sending.
 *
 * @property id Stable row id (device contact id or generated UUID).
 * @property contactId Device contacts provider id when sourced from the address book.
 * @property displayName Name shown on Review / used for the invite.
 * @property contactValue Default phone, or an edited phone/email.
 */
data class ReviewFriendEntry(
    val id: String,
    val contactId: String? = null,
    val displayName: String,
    val contactValue: String,
) {
    val isEmail: Boolean
        get() = contactValue.contains("@")
}

/**
 * Holds selected contacts between Find people → Review → optional Edit contact.
 */
@Singleton
class PendingFriendReviewStore
    @Inject
    constructor() {
        private val _entries = MutableStateFlow<List<ReviewFriendEntry>>(emptyList())
        val entries: StateFlow<List<ReviewFriendEntry>> = _entries.asStateFlow()

        private val _groupId = MutableStateFlow<String?>(null)
        val groupId: StateFlow<String?> = _groupId.asStateFlow()

        /**
         * Replaces the review list with defaults from device contacts (primary phone, else email).
         */
        fun replaceFromDeviceContacts(
            contacts: List<DeviceContact>,
            groupId: String?,
        ) {
            _groupId.value = groupId?.takeIf { it.isNotBlank() }
            _entries.value =
                contacts.map { contact ->
                    val defaultContact =
                        contact.phoneNumber?.trim().orEmpty()
                            .ifBlank { contact.email?.trim().orEmpty() }
                    ReviewFriendEntry(
                        id = contact.id,
                        contactId = contact.id,
                        displayName =
                            contact.displayName.trim().ifBlank { defaultContact },
                        contactValue = defaultContact,
                    )
                }
        }

        /**
         * Adds or replaces a manually entered / edited draft.
         */
        fun upsert(entry: ReviewFriendEntry) {
            _entries.update { current ->
                val without = current.filterNot { it.id == entry.id }
                without + entry
            }
        }

        fun update(
            id: String,
            displayName: String,
            contactValue: String,
        ) {
            _entries.update { current ->
                current.map { entry ->
                    if (entry.id != id) {
                        entry
                    } else {
                        entry.copy(
                            displayName = displayName.trim(),
                            contactValue = contactValue.trim(),
                        )
                    }
                }
            }
        }

        fun remove(id: String) {
            _entries.update { current -> current.filterNot { it.id == id } }
        }

        fun newManualEntryId(): String = UUID.randomUUID().toString()

        fun setGroupId(groupId: String?) {
            _groupId.value = groupId?.takeIf { it.isNotBlank() }
        }

        fun clear() {
            _entries.value = emptyList()
            _groupId.value = null
        }
    }
