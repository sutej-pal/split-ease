package com.splitease.app.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
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

data class FriendsUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
)

@HiltViewModel
class FriendsViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        friendRepository: FriendRepository,
        private val socialInteractor: SocialInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { session ->
                    (session as? AuthSession.SignedIn)?.user?.userId
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val friends: StateFlow<List<Friend>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else friendRepository.observeFriends(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _uiState = MutableStateFlow(FriendsUiState())
        val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

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
                    socialInteractor.refreshFriends(id)
                    socialInteractor.refreshSentInvites(id)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.message ?: "Could not refresh friends.")
                    }
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun addFriend(email: String, onLinked: () -> Unit) {
            val id = userId.value ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
                val result = socialInteractor.addFriendByEmail(id, email)
                val outcome = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message,
                        infoMessage =
                            when {
                                outcome == null -> null
                                outcome.isInvitePending -> "Invite ready — share the link so they can join."
                                else -> "Friend added."
                            },
                        pendingShareText = outcome?.inviteShareText,
                    )
                }
                if (outcome != null && !outcome.isInvitePending) {
                    onLinked()
                }
            }
        }
    }
