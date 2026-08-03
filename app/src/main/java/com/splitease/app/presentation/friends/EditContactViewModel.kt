package com.splitease.app.presentation.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.contacts.DeviceContactsDataSource
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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

data class EditContactUiState(
    val name: String = "",
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
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
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

        fun selectOption(optionId: String) {
            _uiState.update { it.copy(selectedOptionId = optionId) }
        }

        fun setNewPhone(value: String) {
            _uiState.update { it.copy(newPhone = value) }
        }

        fun setNewEmail(value: String) {
            _uiState.update { it.copy(newEmail = value) }
        }

        fun consumeShareText() {
            _uiState.update { it.copy(pendingShareText = null) }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        /**
         * Saves the chosen contact. In [confirmOnly] mode, updates the Review draft and
         * does not send an invite yet.
         */
        fun submit(onLinked: () -> Unit, onConfirmedForReview: () -> Unit) {
            viewModelScope.launch {
                val state = _uiState.value
                val contactValue = resolveSelectedContact(state)
                if (state.name.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_name_required))
                    }
                    return@launch
                }
                if (contactValue.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_contact_required))
                    }
                    return@launch
                }

                if (state.confirmOnly) {
                    applyConfirmOnly(
                        name = state.name.trim(),
                        contactValue = contactValue,
                        entryId = state.entryId,
                        contactId = initialContactId,
                        groupId = state.groupId,
                    )
                    onConfirmedForReview()
                    return@launch
                }

                val ownerId = requireUserId()
                if (ownerId == null) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                    }
                    return@launch
                }

                _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
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
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage =
                            when {
                                outcome == null -> null
                                outcome.isInvitePending ->
                                    appContext.getString(R.string.msg_invite_ready)
                                else ->
                                    if (state.friendUserId != null) {
                                        appContext.getString(R.string.msg_contact_updated)
                                    } else {
                                        appContext.getString(R.string.msg_friend_added)
                                    }
                            },
                        pendingShareText = outcome?.inviteShareText,
                    )
                }
                if (outcome != null && !outcome.isInvitePending) {
                    onLinked()
                }
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
            val contact = entry?.contactValue?.trim().orEmpty().ifBlank { initialContact.trim() }
            val looksLikeEmail = contact.contains("@")
            val options =
                buildOptions(
                    phones = if (!looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
                    emails = if (looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
                )
            val selected = preferredDefaultOptionId(options, contact)
            _uiState.value =
                EditContactUiState(
                    name = entry?.displayName?.ifBlank { initialName }.orEmpty().ifBlank { initialName },
                    options = options,
                    selectedOptionId = selected,
                    newPhone = if (!looksLikeEmail) contact else "",
                    newEmail = if (looksLikeEmail) contact else "",
                    groupId = initialGroupId ?: reviewStore.groupId.value,
                    entryId = entryId,
                    confirmOnly = true,
                    isLoading = false,
                )
        }

        private suspend fun loadFriend(friendUserId: String) {
            val friend = friendRepository.getByFriendUserId(friendUserId)
            val contact = friend?.emailSnapshot.orEmpty()
            val looksLikeEmail = contact.contains("@")
            val options = buildOptions(
                phones = if (!looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
                emails = if (looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
            )
            val selected = preferredDefaultOptionId(options, contact)
            _uiState.value =
                EditContactUiState(
                    name =
                        friend?.displayNameSnapshot
                            ?.removeSuffix(" (invited)")
                            ?.trim()
                            .orEmpty(),
                    options = options,
                    selectedOptionId = selected,
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
                    contact?.phoneNumber?.trim().orEmpty()
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
                    name = contact?.displayName?.ifBlank { initialName }.orEmpty().ifBlank { initialName },
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
            val looksLikeEmail = contact.contains("@")
            val options =
                buildOptions(
                    phones = if (!looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
                    emails = if (looksLikeEmail && contact.isNotBlank()) listOf(contact) else emptyList(),
                )
            val selected =
                when {
                    contact.isBlank() ->
                        options.firstOrNull { it.kind == ContactMethodKind.NEW_PHONE }?.id
                            ?: options.first().id
                    else -> preferredDefaultOptionId(options, contact)
                }
            _uiState.value =
                EditContactUiState(
                    name = initialName,
                    options = options,
                    selectedOptionId = selected,
                    newPhone = if (!looksLikeEmail) contact else "",
                    newEmail = if (looksLikeEmail) contact else "",
                    groupId = initialGroupId,
                    entryId = initialEntryId,
                    confirmOnly = confirmOnly,
                    isLoading = false,
                )
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
         * Prefers the default (first) phone; falls back to a matching contact or first email.
         */
        private fun preferredDefaultOptionId(
            options: List<ContactMethodOption>,
            preferredContact: String,
        ): String {
            if (preferredContact.isNotBlank()) {
                options.firstOrNull {
                    (it.kind == ContactMethodKind.EXISTING_PHONE ||
                        it.kind == ContactMethodKind.EXISTING_EMAIL) &&
                        it.value.equals(preferredContact, ignoreCase = true)
                }?.id?.let { return it }
            }
            options.firstOrNull { it.kind == ContactMethodKind.EXISTING_PHONE }?.id?.let { return it }
            options.firstOrNull { it.kind == ContactMethodKind.EXISTING_EMAIL }?.id?.let { return it }
            return options.first().id
        }

        private fun resolveSelectedContact(state: EditContactUiState): String {
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
    }
