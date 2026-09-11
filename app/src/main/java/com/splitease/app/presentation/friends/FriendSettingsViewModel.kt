package com.splitease.app.presentation.friends

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject

data class FriendSettingsUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
    val removed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FriendSettingsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val friendRepository: FriendRepository,
        private val groupRepository: GroupRepository,
        userRepository: UserRepository,
        private val socialInteractor: SocialInteractor,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val friendUserId: String =
            savedStateHandle.get<String>("friendUserId").orEmpty()

        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val friend: StateFlow<Friend?> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(null)
                    } else {
                        friendRepository.observeFriends(me).map { list ->
                            list.firstOrNull { it.friendUserId == friendUserId }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val photoUrl: StateFlow<String?> =
            userRepository
                .observeUsers()
                .map { users -> users.firstOrNull { it.id == friendUserId }?.photoUrl }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val sharedGroups: StateFlow<List<Group>> =
            userId
                .flatMapLatest { me ->
                    if (me == null || friendUserId.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            groupRepository.observeGroupsForUser(me),
                            groupRepository.observeGroupsForUser(friendUserId),
                        ) { mine, theirs ->
                            val theirIds = theirs.map { it.id }.toSet()
                            mine.filter { it.id in theirIds }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _uiState = MutableStateFlow(FriendSettingsUiState())
        val uiState: StateFlow<FriendSettingsUiState> = _uiState.asStateFlow()

        fun consumeShareText() {
            _uiState.update { it.copy(pendingShareText = null) }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        fun resendInvite() {
            viewModelScope.launch {
                val rowId = friend.value?.id ?: return@launch
                val outcome =
                    runCatching { socialInteractor.deliverPendingInvite(rowId) }.getOrNull()
                if (outcome == null) {
                    _uiState.update {
                        it.copy(
                            errorMessage = appContext.getString(R.string.msg_invite_link_unavailable),
                            infoMessage = null,
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        pendingShareText = outcome.inviteShareText,
                        errorMessage = null,
                        infoMessage =
                            if (outcome.inviteEmailSent) {
                                appContext.getString(
                                    R.string.msg_invite_email_resent,
                                    outcome.friend.emailSnapshot,
                                )
                            } else {
                                appContext.getString(R.string.msg_invite_resent)
                            },
                    )
                }
            }
        }

        fun removeFriend(onRemoved: () -> Unit) {
            viewModelScope.launch {
                val me = requireUserId() ?: return@launch
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
                val result = socialInteractor.removeFriend(me, friendUserId)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = ErrorMessages.messageOrNull(appContext, TAG, result.exceptionOrNull()),
                        removed = result.isSuccess,
                        infoMessage =
                            if (result.isSuccess) {
                                appContext.getString(R.string.msg_friend_removed)
                            } else {
                                null
                            },
                    )
                }
                if (result.isSuccess) onRemoved()
            }
        }

        fun blockFriend(onDone: () -> Unit) {
            // Local-only: block behaves like remove for now.
            removeFriend(onDone)
        }

        fun reportFriend() {
            _uiState.update {
                it.copy(
                    infoMessage = appContext.getString(R.string.msg_report_submitted),
                    errorMessage = null,
                )
            }
        }

        fun firstName(): String {
            val raw =
                friend.value
                    ?.displayNameSnapshot
                    ?.removeSuffix(" (invited)")
                    ?.trim()
                    .orEmpty()
            return raw.substringBefore(" ").ifBlank { raw.ifBlank { "Friend" } }
        }

        fun isPendingInvite(): Boolean =
            friend.value?.displayNameSnapshot?.contains("(invited)", ignoreCase = true) == true

        private suspend fun requireUserId(): String? {
            userId.value?.let { return it }
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }

        private companion object {
            const val TAG = "FriendSettingsViewModel"
        }
    }
