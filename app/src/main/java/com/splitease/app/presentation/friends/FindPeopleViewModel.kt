package com.splitease.app.presentation.friends

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.contacts.DeviceContact
import com.splitease.app.data.contacts.DeviceContactsDataSource
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val selectedContactIds: Set<String> = emptySet(),
    val contactsPermissionGranted: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
    /** All local group member user ids (including LOCAL_ONLY placeholders). */
    val memberUserIds: Set<String> = emptySet(),
    /** Members confirmed SYNCED to Supabase. */
    val syncedMemberUserIds: Set<String> = emptySet(),
    /** Pending per-person GROUP invites for the current group. */
    val pendingGroupInviteUserIds: Set<String> = emptySet(),
)

/**
 * Find people: search friends + device contacts, optional group membership.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FindPeopleViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        friendRepository: FriendRepository,
        userRepository: UserRepository,
        private val groupRepository: GroupRepository,
        private val inviteRepository: InviteRepository,
        private val socialInteractor: SocialInteractor,
        private val deviceContactsDataSource: DeviceContactsDataSource,
        private val reviewStore: PendingFriendReviewStore,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val friends: StateFlow<List<Friend>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else friendRepository.observeFriends(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** userId → profile photo URL when the friend has set one. */
        val userPhotoUrls: StateFlow<Map<String, String?>> =
            userRepository
                .observeUsers()
                .map { users -> users.associate { it.id to it.photoUrl } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        private val _uiState = MutableStateFlow(FindPeopleUiState())
        val uiState: StateFlow<FindPeopleUiState> = _uiState.asStateFlow()

        private val groupIdFlow = MutableStateFlow<String?>(null)

        init {
            viewModelScope.launch {
                combine(groupIdFlow, userId, friends) { groupId, me, friendList ->
                    Triple(groupId, me, friendList)
                }
                    .flatMapLatest { (groupId, me, friendList) ->
                        if (groupId.isNullOrBlank()) {
                            flowOf(Triple(emptySet<String>(), emptySet(), emptySet()))
                        } else {
                            combine(
                                groupRepository.observeMembers(groupId),
                                if (me == null) {
                                    flowOf(emptyList())
                                } else {
                                    inviteRepository.observeSentInvites(me)
                                },
                            ) { members, invites ->
                                val memberUserIds = members.map { it.userId }.toSet()
                                val syncedMemberUserIds =
                                    members
                                        .filter { it.syncStatus == SyncStatus.SYNCED }
                                        .map { it.userId }
                                        .toSet()
                                val pendingFriendRowIds =
                                    invites
                                        .filter { invite ->
                                            invite.status == InviteStatus.PENDING &&
                                                invite.kind == InviteKind.GROUP &&
                                                invite.groupId == groupId &&
                                                invite.friendRowId != null
                                        }
                                        .mapNotNull { it.friendRowId }
                                        .toSet()
                                val pendingGroupInviteUserIds =
                                    friendList
                                        .filter { it.id in pendingFriendRowIds }
                                        .map { it.friendUserId }
                                        .toSet()
                                Triple(memberUserIds, syncedMemberUserIds, pendingGroupInviteUserIds)
                            }
                        }
                    }.collect { (memberUserIds, syncedMemberUserIds, pendingGroupInviteUserIds) ->
                        _uiState.update {
                            it.copy(
                                memberUserIds = memberUserIds,
                                syncedMemberUserIds = syncedMemberUserIds,
                                pendingGroupInviteUserIds = pendingGroupInviteUserIds,
                            )
                        }
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

        fun toggleContactSelection(contactId: String) {
            _uiState.update { state ->
                val next =
                    if (contactId in state.selectedContactIds) {
                        state.selectedContactIds - contactId
                    } else {
                        state.selectedContactIds + contactId
                    }
                state.copy(selectedContactIds = next)
            }
        }

        /**
         * Seeds the Review screen with selected contacts (email when available, else phone).
         *
         * @return true when at least one contact was queued for review.
         */
        fun prepareReview(): Boolean {
            val selectedIds = _uiState.value.selectedContactIds
            if (selectedIds.isEmpty()) return false
            val selected =
                _uiState.value.contacts.filter { it.id in selectedIds }
            if (selected.isEmpty()) return false
            reviewStore.replaceFromDeviceContacts(selected, groupIdFlow.value)
            return true
        }

        /** Clears prior review drafts before a manual Add-people entry. */
        fun prepareManualAdd() {
            reviewStore.clear()
            reviewStore.setGroupId(groupIdFlow.value)
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
            val q = _uiState.value.query
                .trim()
                .lowercase()
            if (q.isEmpty()) return all
            return all.filter {
                it.displayNameSnapshot.lowercase().contains(q) ||
                    it.emailSnapshot.lowercase().contains(q)
            }
        }

        fun filteredContacts(): List<DeviceContact> {
            val q = _uiState.value.query
                .trim()
                .lowercase()
            val all = _uiState.value.contacts
            if (q.isEmpty()) return all
            return all.filter { it.searchable().contains(q) }
        }

        fun addFriendToGroup(friendUserId: String, onDone: () -> Unit = {}) {
            val groupId = groupIdFlow.value ?: return
            viewModelScope.launch {
                val ownerId =
                    userId.value
                        ?: (
                            authRepository.observeSession().first { it !is AuthSession.Loading }
                            as? AuthSession.SignedIn
                        )?.user
                            ?.userId
                if (ownerId == null) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
                }
                val result =
                    socialInteractor.addExistingFriendToGroup(
                        ownerUserId = ownerId,
                        groupId = groupId,
                        friendUserId = friendUserId,
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
                                outcome.inviteEmailSent ->
                                    appContext.getString(
                                        R.string.msg_invite_email_sent,
                                        outcome.friend.emailSnapshot,
                                    )
                                outcome.isInvitePending ->
                                    appContext.getString(R.string.msg_invite_ready)
                                else -> appContext.getString(R.string.msg_member_added)
                            },
                    )
                }
                if (outcome != null && !outcome.isInvitePending) {
                    onDone()
                }
            }
        }

        fun submitContact(
            name: String,
            contactValue: String,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val id =
                    userId.value
                        ?: (
                            authRepository.observeSession().first { it !is AuthSession.Loading }
                            as? AuthSession.SignedIn
                        )?.user
                            ?.userId
                if (id == null) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                    }
                    return@launch
                }
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
                                outcome.inviteEmailSent ->
                                    appContext.getString(
                                        R.string.msg_invite_email_sent,
                                        outcome.friend.emailSnapshot,
                                    )
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
