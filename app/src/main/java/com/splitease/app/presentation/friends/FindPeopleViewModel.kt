package com.splitease.app.presentation.friends

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.contacts.DeviceContact
import com.splitease.app.data.contacts.DeviceContactsDataSource
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FindPeopleUiState(
    val query: String = "",
    val contacts: List<DeviceContact> = emptyList(),
    val contactsPermissionGranted: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
    val memberUserIds: Set<String> = emptySet(),
)

/**
 * Find people: search friends + device contacts, optional group membership.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FindPeopleViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        friendRepository: FriendRepository,
        private val groupRepository: GroupRepository,
        private val socialInteractor: SocialInteractor,
        private val deviceContactsDataSource: DeviceContactsDataSource,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val friends: StateFlow<List<Friend>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else friendRepository.observeFriends(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _uiState = MutableStateFlow(FindPeopleUiState())
        val uiState: StateFlow<FindPeopleUiState> = _uiState.asStateFlow()

        private val groupIdFlow = MutableStateFlow<String?>(null)

        init {
            viewModelScope.launch {
                combine(groupIdFlow, userId) { groupId, _ -> groupId }
                    .flatMapLatest { groupId ->
                        if (groupId.isNullOrBlank()) {
                            flowOf(emptySet())
                        } else {
                            groupRepository.observeMembers(groupId).map { members ->
                                members.map { it.userId }.toSet()
                            }
                        }
                    }.collect { ids ->
                        _uiState.update { it.copy(memberUserIds = ids) }
                    }
            }
            refreshPermissionAndContacts()
        }

        fun setGroupId(groupId: String?) {
            groupIdFlow.value = groupId?.takeIf { it.isNotBlank() }
        }

        fun setQuery(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        fun consumeShareText() {
            _uiState.update { it.copy(pendingShareText = null) }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        fun refreshPermissionAndContacts() {
            val granted =
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED
            _uiState.update {
                it.copy(contactsPermissionGranted = granted, isLoadingContacts = granted)
            }
            if (!granted) {
                _uiState.update { it.copy(contacts = emptyList(), isLoadingContacts = false) }
                return
            }
            viewModelScope.launch {
                val loaded =
                    withContext(Dispatchers.IO) {
                        runCatching { deviceContactsDataSource.loadContacts() }
                            .getOrDefault(emptyList())
                    }
                _uiState.update {
                    it.copy(contacts = loaded, isLoadingContacts = false, contactsPermissionGranted = true)
                }
            }
        }

        fun onContactsPermissionResult(granted: Boolean) {
            _uiState.update { it.copy(contactsPermissionGranted = granted) }
            if (granted) refreshPermissionAndContacts()
        }

        fun filteredFriends(all: List<Friend>): List<Friend> {
            val q = _uiState.value.query.trim().lowercase()
            if (q.isEmpty()) return all
            return all.filter {
                it.displayNameSnapshot.lowercase().contains(q) ||
                    it.emailSnapshot.lowercase().contains(q)
            }
        }

        fun filteredContacts(): List<DeviceContact> {
            val q = _uiState.value.query.trim().lowercase()
            val all = _uiState.value.contacts
            if (q.isEmpty()) return all
            return all.filter { it.searchable().contains(q) }
        }

        fun addFriendToGroup(friendUserId: String, onDone: () -> Unit) {
            val groupId = groupIdFlow.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.addMemberToGroup(groupId, friendUserId)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
                if (result.isSuccess) onDone()
            }
        }

        fun addContact(
            contact: DeviceContact,
            onNeedManualAdd: (name: String, contactValue: String) -> Unit,
            onDone: () -> Unit,
        ) {
            val email = contact.email?.trim().orEmpty()
            val phone = contact.phoneNumber?.trim().orEmpty()
            val name = contact.displayName
            when {
                email.isNotEmpty() ->
                    submitContact(name = name, contactValue = email, onDone = onDone)
                phone.isNotEmpty() -> onNeedManualAdd(name, phone)
                else -> onNeedManualAdd(name, "")
            }
        }

        fun submitContact(
            name: String,
            contactValue: String,
            onDone: () -> Unit,
        ) {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
                val result =
                    socialInteractor.addFriendByContact(
                        ownerUserId = id,
                        contact = contactValue,
                        displayName = name.trim().ifBlank { null },
                        groupId = groupIdFlow.value,
                    )
                val outcome = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        pendingShareText = outcome?.inviteShareText,
                        infoMessage =
                            when {
                                outcome == null -> null
                                outcome.isInvitePending ->
                                    appContext.getString(R.string.msg_invite_ready)
                                else -> appContext.getString(R.string.msg_friend_added)
                            },
                    )
                }
                if (outcome != null && !outcome.isInvitePending) {
                    onDone()
                }
            }
        }
    }
