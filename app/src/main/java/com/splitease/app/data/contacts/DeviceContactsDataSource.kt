package com.splitease.app.data.contacts

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads device contacts (name / phones / emails) for the Find people UI.
 *
 * Requires [android.Manifest.permission.READ_CONTACTS] at runtime.
 */
@Singleton
class DeviceContactsDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Loads contacts that have at least a phone or email.
         *
         * @return Sorted list by display name.
         */
        fun loadContacts(): List<DeviceContact> {
            val phones = loadPhonesByContactId()
            val emails = loadEmailsByContactId()
            val names = loadNamesByContactId()
            val ids = (phones.keys + emails.keys).toSet()
            return ids
                .map { id ->
                DeviceContact(
                    id = id,
                    displayName = names[id].orEmpty().ifBlank {
                        emails[id]?.firstOrNull() ?: phones[id]?.firstOrNull().orEmpty()
                    },
                    phoneNumbers = phones[id].orEmpty(),
                    emails = emails[id].orEmpty(),
                )
            }.sortedBy { it.displayName.lowercase() }
        }

        /**
         * Loads a single contact by provider id.
         *
         * @param contactId Contacts provider id.
         * @return Matching contact, or null.
         */
        fun loadContactById(contactId: String): DeviceContact? =
            loadContacts().firstOrNull { it.id == contactId }

        private fun loadNamesByContactId(): Map<String, String> {
            val out = mutableMapOf<String, String>()
            context.contentResolver
                .query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    if (name.isNotEmpty()) out[id] = name
                }
            }
            return out
        }

        private fun loadPhonesByContactId(): Map<String, List<String>> {
            val out = mutableMapOf<String, MutableList<String>>()
            context.contentResolver
                .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val list = out.getOrPut(id) { mutableListOf() }
                    if (list.none { it.equals(number, ignoreCase = true) }) {
                        list.add(number)
                    }
                }
            }
            return out
        }

        private fun loadEmailsByContactId(): Map<String, List<String>> {
            val out = mutableMapOf<String, MutableList<String>>()
            context.contentResolver
                .query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addrIdx =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val email = cursor.getString(addrIdx)?.trim().orEmpty()
                    if (email.isEmpty()) continue
                    val list = out.getOrPut(id) { mutableListOf() }
                    if (list.none { it.equals(email, ignoreCase = true) }) {
                        list.add(email)
                    }
                }
            }
            return out
        }
    }
