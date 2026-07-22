package com.splitease.app.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        authRepository: AuthRepository,
        private val groupRepository: GroupRepository,
        friendRepository: FriendRepository,
        private val socialInteractor: SocialInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

        fun refresh() {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching {
                    socialInteractor.refreshGroups(id)
                    socialInteractor.refreshFriends(id)
                    socialInteractor.refreshSentInvites(id)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.message ?: "Could not refresh groups.")
                    }
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun createGroup(
            name: String,
            currency: String,
            memberIds: List<String>,
            onSuccess: (String) -> Unit,
        ) {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.createGroup(id, name, currency, memberIds)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }
                result.getOrNull()?.let { onSuccess(it.id) }
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
            val ownerId = userId.value ?: return
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isSubmitting = true, errorMessage = null, infoMessage = null, pendingShareText = null)
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

        suspend fun getGroup(groupId: String): Group? = groupRepository.getGroupById(groupId)

        fun currentUserId(): String? = userId.value
    }
