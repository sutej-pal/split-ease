package com.splitease.app.presentation.friends

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.DialCodes
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.contacts.DeviceContactsDataSource
import com.splitease.app.data.social.ContactIdentifier
import com.splitease.app.data.social.ContactKind
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ContactMethodKind {
    EXISTING_PHONE,
    EXISTING_EMAIL,
    NEW_PHONE,
    NEW_EMAIL,
}

data class ContactMethodOption(
    val id: String,
    val kind: ContactMethodKind,
    val value: String = "",
)

/** Device address-book picker vs the single phone-or-email field. */
enum class EditContactMode {
    SINGLE_FIELD,
    DEVICE_CONTACT,
}

/**
 * Country + national number for the confirm-phone dialog. Lives in the ViewModel so rotation keeps it.
 *
 * @property dialCode Chosen calling code, including `+`.
 * @property flag Emoji flag for [dialCode].
 * @property number National number as shown in the dialog (spaces kept).
 */
data class PhoneConfirmState(
    val dialCode: String,
    val flag: String,
    val number: String,
)

data class EditContactUiState(
    val mode: EditContactMode = EditContactMode.SINGLE_FIELD,
    val name: String = "",
    val contactInput: String = "",
    val options: List<ContactMethodOption> = emptyList(),
    val selectedOptionId: String = "",
    val newPhone: String = "",
    val newEmail: String = "",
    val friendUserId: String? = null,
    val groupId: String? = null,
    val entryId: String? = null,
    val confirmOnly: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val contactError: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
    val invitePhone: String? = null,
    val showSmsPrompt: Boolean = false,
    val phoneConfirm: PhoneConfirmState? = null,
)

@HiltViewModel
class EditContactViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val friendRepository: FriendRepository,
        private val socialInteractor: SocialInteractor,
        private val deviceContactsDataSource: DeviceContactsDataSource,
        private val reviewStore: PendingFriendReviewStore,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val initialFriendUserId =
            savedStateHandle.get<String>("friendUserId").orEmpty().ifBlank { null }
        private val initialContactId =
            savedStateHandle.get<String>("contactId").orEmpty().ifBlank { null }
        private val initialGroupId =
            savedStateHandle.get<String>("groupId").orEmpty().ifBlank { null }
        private val initialName = savedStateHandle.get<String>("name").orEmpty()
        private val initialContact = savedStateHandle.get<String>("contact").orEmpty()
        private val initialEntryId =
            savedStateHandle.get<String>("entryId").orEmpty().ifBlank { null }
        private val confirmOnly =
            savedStateHandle.get<String>("confirmOnly").orEmpty() == "true"

        private val _uiState = MutableStateFlow(EditContactUiState())
        val uiState: StateFlow<EditContactUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch { bootstrap() }
        }

        fun setName(value: String) {
            _uiState.update { it.copy(name = value) }
        }

        fun setContactInput(value: String) {
            _uiState.update { it.copy(contactInput = value, contactError = null) }
        }

        fun selectOption(optionId: String) {
            _uiState.update { it.copy(selectedOptionId = optionId, contactError = null) }
        }

        fun setNewPhone(value: String) {
            _uiState.update { it.copy(newPhone = value, contactError = null) }
        }

        fun setNewEmail(value: String) {
            _uiState.update { it.copy(newEmail = value, contactError = null) }
        }

        fun setPhoneConfirmNumber(value: String) {
            _uiState.update { state ->
                val confirm = state.phoneConfirm ?: return@update state
                state.copy(phoneConfirm = confirm.copy(number = value))
            }
        }

        fun setPhoneConfirmDial(dialCode: String, flag: String) {
            _uiState.update { state ->
                val confirm = state.phoneConfirm ?: return@update state
                state.copy(phoneConfirm = confirm.copy(dialCode = dialCode, flag = flag))
            }
        }

        fun dismissPhoneConfirm() {
            _uiState.update { it.copy(phoneConfirm = null) }
        }

        fun consumeShareText() {
            _uiState.update {
                it.copy(
                    pendingShareText = null,
                    invitePhone = null,
                    showSmsPrompt = false,
                )
            }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        /**
         * Saves the chosen contact. In [confirmOnly] mode, updates the Review draft and
         * does not send an invite yet.
         *
         * A phone without a country code opens [PhoneConfirmState] and saves nothing.
         * That includes the device-contact flow (one call, [presentPhoneConfirm]).
         */
        fun submit(onLinked: () -> Unit, onConfirmedForReview: () -> Unit) {
            viewModelScope.launch {
                val state = _uiState.value
                if (state.phoneConfirm != null) return@launch
                if (state.name.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_name_required))
                    }
                    return@launch
                }
                val contactValue = resolveContact(state)
                if (contactValue.isBlank()) {
                    _uiState.update {
                        it.copy(
                            contactError = appContext.getString(R.string.msg_contact_required),
                            errorMessage = null,
                        )
                    }
                    return@launch
                }
                if (ContactIdentifier.classify(contactValue) == ContactKind.INVALID) {
                    _uiState.update {
                        it.copy(
                            contactError = appContext.getString(R.string.msg_contact_invalid),
                            errorMessage = null,
                        )
                    }
                    return@launch
                }
                if (presentPhoneConfirm(contactValue)) return@launch
                saveContact(state, contactValue, onLinked, onConfirmedForReview)
            }
        }

        /**
         * Applies the dialog's dial code and continues the normal save.
         * The field is updated to the confirmed E.164 so a later failure shows what would be stored.
         */
        fun continuePhoneConfirm(onLinked: () -> Unit, onConfirmedForReview: () -> Unit) {
            viewModelScope.launch {
                val state = _uiState.value
                val confirm = state.phoneConfirm ?: return@launch
                if (!ContactIdentifier.isConfirmablePhoneLength(confirm.dialCode, confirm.number)) {
                    return@launch
                }
                val e164 = ContactIdentifier.normalizePhone(confirm.number, confirm.dialCode)
                if (e164 == null) {
                    _uiState.update {
                        it.copy(
                            phoneConfirm = null,
                            contactError = appContext.getString(R.string.msg_contact_invalid),
                        )
                    }
                    return@launch
                }
                val selected = state.options.firstOrNull { it.id == state.selectedOptionId }
                _uiState.update {
                    it.copy(
                        phoneConfirm = null,
                        contactInput =
                            if (it.mode == EditContactMode.SINGLE_FIELD) {
                                e164
                            } else {
                                it.contactInput
                            },
                        newPhone =
                            if (selected?.kind == ContactMethodKind.NEW_PHONE) {
                                e164
                            } else {
                                it.newPhone
                            },
                        options =
                            if (selected?.kind == ContactMethodKind.EXISTING_PHONE) {
                                it.options.map { option ->
                                    if (option.id == it.selectedOptionId) {
                                        option.copy(value = e164)
                                    } else {
                                        option
                                    }
                                }
                            } else {
                                it.options
                            },
                    )
                }
                val updated = _uiState.value
                saveContact(updated, e164, onLinked, onConfirmedForReview)
            }
        }

        /**
         * Returns true when a phone has no country code and the confirm dialog was opened.
         * Remove this call to stop the device-contact flow from confirming a dial code.
         */
        private fun presentPhoneConfirm(contactValue: String): Boolean {
            if (ContactIdentifier.classify(contactValue) != ContactKind.PHONE) return false
            if (ContactIdentifier.hasExplicitCountryCode(contactValue)) return false
            _uiState.update {
                it.copy(
                    phoneConfirm =
                        PhoneConfirmState(
                            dialCode = ContactIdentifier.DEFAULT_DIAL_CODE,
                            flag = DialCodes.flagFor(ContactIdentifier.DEFAULT_DIAL_CODE),
                            number = contactValue.trim(),
                        ),
                    contactError = null,
                    errorMessage = null,
                )
            }
            return true
        }

        private suspend fun saveContact(
            state: EditContactUiState,
            contactValue: String,
            onLinked: () -> Unit,
            onConfirmedForReview: () -> Unit,
        ) {
            if (state.confirmOnly) {
                applyConfirmOnly(
                    name = state.name.trim(),
                    contactValue = contactValue,
                    entryId = state.entryId,
                    contactId = initialContactId,
                    groupId = state.groupId,
                )
                onConfirmedForReview()
                return
            }

            val ownerId = requireUserId()
            if (ownerId == null) {
                _uiState.update {
                    it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                }
                return
            }

            _uiState.update {
                it.copy(isSubmitting = true, errorMessage = null, infoMessage = null, contactError = null)
            }
            val result =
                if (!state.friendUserId.isNullOrBlank()) {
                    socialInteractor.updateFriendContact(
                        ownerUserId = ownerId,
                        friendUserId = state.friendUserId,
                        displayName = state.name,
                        contact = contactValue,
                    )
                } else {
                    socialInteractor.addFriendByContact(
                        ownerUserId = ownerId,
                        contact = contactValue,
                        displayName = state.name.trim(),
                        groupId = state.groupId,
                    )
                }
            val outcome = result.getOrNull()
            val phone = outcome?.invitePhone
            val shareText = outcome?.inviteShareText
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                    infoMessage =
                        when {
                            outcome == null -> null
                            outcome.inviteEmailSent ->
                                appContext.getString(
                                    R.string.msg_invite_email_sent,
                                    ContactIdentifier.displayContact(outcome.friend.emailSnapshot),
                                )
                            outcome.isInvitePending ->
                                appContext.getString(R.string.msg_invite_ready)
                            else ->
                                if (state.friendUserId != null) {
                                    appContext.getString(R.string.msg_contact_updated)
                                } else {
                                    appContext.getString(R.string.msg_friend_added)
                                }
                        },
                    pendingShareText = shareText,
                    invitePhone = phone,
                    showSmsPrompt = !phone.isNullOrBlank() && !shareText.isNullOrBlank(),
                )
            }
            if (outcome != null && !outcome.isInvitePending) {
                onLinked()
            }
        }

        private fun applyConfirmOnly(
            name: String,
            contactValue: String,
            entryId: String?,
            contactId: String?,
            groupId: String?,
        ) {
            if (!groupId.isNullOrBlank()) {
                reviewStore.setGroupId(groupId)
            }
            if (!entryId.isNullOrBlank() && reviewStore.entries.value.any { it.id == entryId }) {
                reviewStore.update(entryId, name, contactValue)
            } else {
                val id = entryId ?: reviewStore.newManualEntryId()
                reviewStore.upsert(
                    ReviewFriendEntry(
                        id = id,
                        contactId = contactId,
                        displayName = name,
                        contactValue = contactValue,
                    ),
                )
            }
        }

        private suspend fun bootstrap() {
            when {
                !initialFriendUserId.isNullOrBlank() -> loadFriend(initialFriendUserId)
                !initialContactId.isNullOrBlank() -> loadDeviceContact(initialContactId)
                !initialEntryId.isNullOrBlank() -> loadFromReviewEntry(initialEntryId)
                else -> loadManualPrefill()
            }
        }

        private fun loadFromReviewEntry(entryId: String) {
            val entry = reviewStore.entries.value.firstOrNull { it.id == entryId }
            val contact =
                entry
                    ?.contactValue
                    ?.trim()
                    .orEmpty()
                    .ifBlank { initialContact.trim() }
            _uiState.value =
                EditContactUiState(
                    mode = EditContactMode.SINGLE_FIELD,
                    name =
                        entry
                            ?.displayName
                            ?.ifBlank { initialName }
                            .orEmpty()
                            .ifBlank { initialName },
                    contactInput = fieldContact(contact, normalizeLegacyPhone = false),
                    groupId = initialGroupId ?: reviewStore.groupId.value,
                    entryId = entryId,
                    confirmOnly = true,
                    isLoading = false,
                )
        }

        private suspend fun loadFriend(friendUserId: String) {
            val friend = friendRepository.getByFriendUserId(friendUserId)
            val contact = friend?.emailSnapshot.orEmpty()
            _uiState.value =
                EditContactUiState(
                    mode = EditContactMode.SINGLE_FIELD,
                    name =
                        friend
                            ?.displayNameSnapshot
                            ?.removeSuffix(" (invited)")
                            ?.trim()
                            .orEmpty(),
                    contactInput = fieldContact(contact, normalizeLegacyPhone = true),
                    friendUserId = friendUserId,
                    groupId = initialGroupId,
                    entryId = initialEntryId,
                    confirmOnly = confirmOnly,
                    isLoading = false,
                )
        }

        private suspend fun loadDeviceContact(contactId: String) {
            val contact =
                withContext(Dispatchers.IO) {
                    deviceContactsDataSource.loadContactById(contactId)
                }
            val preferred =
                initialContact.trim().ifBlank {
                    contact
                        ?.phoneNumber
                        ?.trim()
                        .orEmpty()
                        .ifBlank { contact?.email?.trim().orEmpty() }
                }
            val options =
                buildOptions(
                    phones = contact?.phoneNumbers.orEmpty(),
                    emails = contact?.emails.orEmpty(),
                )
            val selected = preferredDefaultOptionId(options, preferred)
            _uiState.value =
                EditContactUiState(
                    mode = EditContactMode.DEVICE_CONTACT,
                    name =
                        contact
                            ?.displayName
                            ?.ifBlank { initialName }
                            .orEmpty()
                            .ifBlank { initialName },
                    options = options,
                    selectedOptionId = selected,
                    groupId = initialGroupId,
                    entryId = initialEntryId,
                    confirmOnly = confirmOnly,
                    isLoading = false,
                )
        }

        private fun loadManualPrefill() {
            val contact = initialContact.trim()
            _uiState.value =
                EditContactUiState(
                    mode = EditContactMode.SINGLE_FIELD,
                    name = initialName,
                    contactInput = fieldContact(contact, normalizeLegacyPhone = false),
                    groupId = initialGroupId,
                    entryId = initialEntryId,
                    confirmOnly = confirmOnly,
                    isLoading = false,
                )
        }

        /**
         * Placeholder addresses become E.164. Legacy raw phones become E.164 only when
         * loading a saved friend. Review and manual prefill keep the typed string.
         */
        private fun fieldContact(contact: String, normalizeLegacyPhone: Boolean): String {
            val trimmed = contact.trim()
            if (ContactIdentifier.isMobilePlaceholder(trimmed)) {
                return ContactIdentifier.phoneFromStored(trimmed) ?: trimmed
            }
            if (normalizeLegacyPhone && ContactIdentifier.isLegacyRawPhone(trimmed)) {
                return ContactIdentifier.phoneFromStored(trimmed) ?: trimmed
            }
            return trimmed
        }

        private fun buildOptions(
            phones: List<String>,
            emails: List<String>,
        ): List<ContactMethodOption> {
            val options = mutableListOf<ContactMethodOption>()
            phones.forEachIndexed { index, phone ->
                options +=
                    ContactMethodOption(
                        id = "phone_$index",
                        kind = ContactMethodKind.EXISTING_PHONE,
                        value = phone,
                    )
            }
            emails.forEachIndexed { index, email ->
                options +=
                    ContactMethodOption(
                        id = "email_$index",
                        kind = ContactMethodKind.EXISTING_EMAIL,
                        value = email,
                    )
            }
            options +=
                ContactMethodOption(
                    id = "new_phone",
                    kind = ContactMethodKind.NEW_PHONE,
                )
            options +=
                ContactMethodOption(
                    id = "new_email",
                    kind = ContactMethodKind.NEW_EMAIL,
                )
            return options
        }

        /**
         * Prefers a matching contact value, then email (so invite mail can be sent),
         * then the first phone.
         */
        private fun preferredDefaultOptionId(
            options: List<ContactMethodOption>,
            preferredContact: String,
        ): String {
            if (preferredContact.isNotBlank()) {
                options
                    .firstOrNull {
                        (
                            it.kind == ContactMethodKind.EXISTING_PHONE ||
                                it.kind == ContactMethodKind.EXISTING_EMAIL
                        ) &&
                            it.value.equals(preferredContact, ignoreCase = true)
                    }?.id
                    ?.let { return it }
            }
            options.firstOrNull { it.kind == ContactMethodKind.EXISTING_EMAIL }?.id?.let { return it }
            options.firstOrNull { it.kind == ContactMethodKind.EXISTING_PHONE }?.id?.let { return it }
            return options.first().id
        }

        private fun resolveContact(state: EditContactUiState): String {
            if (state.mode == EditContactMode.SINGLE_FIELD) {
                return state.contactInput.trim()
            }
            val selected = state.options.firstOrNull { it.id == state.selectedOptionId }
            return when (selected?.kind) {
                ContactMethodKind.EXISTING_PHONE, ContactMethodKind.EXISTING_EMAIL ->
                    selected.value.trim()
                ContactMethodKind.NEW_PHONE -> state.newPhone.trim()
                ContactMethodKind.NEW_EMAIL -> state.newEmail.trim()
                null -> ""
            }
        }

        private suspend fun requireUserId(): String? {
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }

        private companion object {
            const val TAG = "EditContactViewModel"
        }
    }
