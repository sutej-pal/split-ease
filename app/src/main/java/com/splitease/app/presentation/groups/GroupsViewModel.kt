package com.splitease.app.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
)

@HiltViewModel
class GroupsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val groupRepository: GroupRepository,
        friendRepository: FriendRepository,
        private val socialInteractor: SocialInteractor,
        private val syncInteractor: SyncInteractor,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        /** Eager so Create Group (which only collects [uiState]) still has a signed-in user id. */
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val groups: StateFlow<List<Group>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else groupRepository.observeGroupsForUser(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        val friends: StateFlow<List<Friend>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else friendRepository.observeFriends(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _uiState = MutableStateFlow(GroupsUiState())
        val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        fun consumeShareText() {
            _uiState.update { it.copy(pendingShareText = null) }
        }

        /** Queues a system share sheet with [shareText]. */
        fun shareGroupLink(shareText: String) {
            _uiState.update { it.copy(pendingShareText = shareText) }
        }

        fun refresh() {
            viewModelScope.launch {
                val id = requireUserId() ?: return@launch
                _uiState.update { it.copy(isRefreshing = true) }
                // Soft-fail: missing Supabase tables / offline must not block local create UI.
                runCatching { syncInteractor.syncForUser(id) }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun createGroup(
            name: String,
            groupType: GroupType,
            memberIds: List<String> = emptyList(),
            onSuccess: (String) -> Unit,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val id = requireUserId()
                if (id == null) {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = "Not signed in.")
                    }
                    return@launch
                }
                val currency = appSettingsRepository.getCurrencyCode()
                val result =
                    socialInteractor.createGroup(
                        creatorUserId = id,
                        name = name,
                        currencyCode = currency,
                        groupType = groupType,
                        memberFriendUserIds = memberIds,
                    )
                val created = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage =
                            if (created == null) {
                                userFacingError(result.exceptionOrNull())
                            } else {
                                null
                            },
                    )
                }
                if (created != null) {
                    onSuccess(created.id)
                }
            }
        }

        fun updateGroup(group: Group) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.updateGroup(group)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) "Group updated." else null,
                    )
                }
            }
        }

        fun observeMembers(groupId: String): StateFlow<List<GroupMember>> =
            groupRepository
                .observeMembers(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun observeSimplifyDebts(groupId: String): StateFlow<Boolean> =
            appSettingsRepository
                .observeSimplifyGroupDebts(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        fun setSimplifyDebts(groupId: String, enabled: Boolean) {
            viewModelScope.launch {
                appSettingsRepository.setSimplifyGroupDebts(groupId, enabled)
            }
        }

        fun addMember(groupId: String, userId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.addMemberToGroup(groupId, userId)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage = if (result.isSuccess) "Member added." else null,
                    )
                }
            }
        }

        fun inviteMemberByEmail(groupId: String, email: String) {
            viewModelScope.launch {
                val ownerId = requireUserId()
                if (ownerId == null) {
                    _uiState.update { it.copy(errorMessage = "Not signed in.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        infoMessage = null,
                        pendingShareText = null,
                    )
                }
                val result = socialInteractor.inviteToGroupByEmail(ownerId, groupId, email)
                val outcome = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage =
                            when {
                                outcome == null -> null
                                outcome.isInvitePending -> "Invite ready — share the link so they can join."
                                else -> "Member added."
                            },
                        pendingShareText = outcome?.inviteShareText,
                    )
                }
            }
        }

        fun leaveGroup(groupId: String, onLeft: () -> Unit) {
            viewModelScope.launch {
                val id = requireUserId()
                if (id == null) {
                    _uiState.update { it.copy(errorMessage = "Not signed in.") }
                    return@launch
                }
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.leaveGroup(groupId, id)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
                if (result.isSuccess) onLeft()
            }
        }

        fun deleteGroup(groupId: String, onDeleted: () -> Unit) {
            viewModelScope.launch {
                val id = requireUserId()
                if (id == null) {
                    _uiState.update { it.copy(errorMessage = "Not signed in.") }
                    return@launch
                }
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.deleteGroup(groupId, id)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
                if (result.isSuccess) onDeleted()
            }
        }

        suspend fun getGroup(groupId: String): Group? = groupRepository.getGroupById(groupId)

        fun currentUserId(): String? = userId.value

        private suspend fun requireUserId(): String? {
            userId.value?.let { return it }
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }

        private fun userFacingError(error: Throwable?): String {
            val raw = error?.message.orEmpty()
            return when {
                raw.contains("group_members", ignoreCase = true) ||
                    raw.contains("schema cache", ignoreCase = true) ->
                    "Cloud tables are not set up yet. Group was not saved to the cloud — " +
                        "run docs/sql/phase-3-schema.sql in the Supabase SQL Editor."
                raw.contains("Authorization", ignoreCase = true) ||
                    raw.contains("Bearer ", ignoreCase = true) ||
                    raw.contains("apikey", ignoreCase = true) ->
                    "Could not reach the cloud. Check your connection and Supabase setup."
                raw.isNotBlank() && raw.length <= 160 -> raw
                else -> "Something went wrong. Try again."
            }
        }
    }
