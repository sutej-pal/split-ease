package com.splitease.app.presentation.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

data class FriendsUiState(
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
)

/** Invite-derived pending state for friend list rows. */
data class FriendInviteFlags(
    val pendingFriendRowIds: Set<String> = emptySet(),
    val acceptedFriendRowIds: Set<String> = emptySet(),
)

@HiltViewModel
class FriendsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        friendRepository: FriendRepository,
        inviteRepository: InviteRepository,
        private val socialInteractor: SocialInteractor,
        private val balanceInteractor: BalanceInteractor,
        appSettingsRepository: AppSettingsRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        // Eagerly: AddFriendScreen only collects uiState, so WhileSubscribed left userId
        // stuck at null and addFriend/tick/Next silently no-oped.
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { session ->
                    (session as? AuthSession.SignedIn)?.user?.userId
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val friends: StateFlow<List<Friend>> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else friendRepository.observeFriends(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        @OptIn(ExperimentalCoroutinesApi::class)
        val inviteFlags: StateFlow<FriendInviteFlags> =
            userId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(FriendInviteFlags())
                    } else {
                        inviteRepository.observeSentInvites(id).map { invites ->
                            FriendInviteFlags(
                                pendingFriendRowIds =
                                    invites
                                        .filter {
                                            it.status == InviteStatus.PENDING &&
                                                !it.friendRowId.isNullOrBlank()
                                        }.mapNotNull { it.friendRowId }
                                        .toSet(),
                                acceptedFriendRowIds =
                                    invites
                                        .filter {
                                            it.status == InviteStatus.ACCEPTED &&
                                                !it.friendRowId.isNullOrBlank()
                                        }.mapNotNull { it.friendRowId }
                                        .toSet(),
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendInviteFlags())

        @OptIn(ExperimentalCoroutinesApi::class)
        val overallBalances: StateFlow<OverallBalancesUi?> =
            userId
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else balanceInteractor.observeOverallBalances(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val currencyCode: StateFlow<String> =
            appSettingsRepository
                .observeCurrencyCode()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrencies.DEFAULT)

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

        /**
         * Copies the pending invite deep link for [friendRowId] to the clipboard.
         *
         * @param friendRowId Local friendship row id.
         */
        fun copyInviteLink(friendRowId: String) {
            viewModelScope.launch {
                val link =
                    runCatching { socialInteractor.pendingInviteClipboardLink(friendRowId) }
                        .getOrNull()
                if (link.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            errorMessage = appContext.getString(R.string.msg_invite_link_unavailable),
                            infoMessage = null,
                        )
                    }
                    return@launch
                }
                val clipboard = appContext.getSystemService<ClipboardManager>()
                clipboard?.setPrimaryClip(ClipData.newPlainText("SplitEase invite", link))
                _uiState.update {
                    it.copy(
                        infoMessage = appContext.getString(R.string.invite_link_copied),
                        errorMessage = null,
                    )
                }
            }
        }

        /**
         * Re-opens the system share sheet for an existing pending invite.
         *
         * @param friendRowId Local friendship row id.
         */
        fun shareInviteAgain(friendRowId: String) {
            viewModelScope.launch {
                val text =
                    runCatching { socialInteractor.pendingInviteShareText(friendRowId) }
                        .getOrNull()
                if (text.isNullOrBlank()) {
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
                        pendingShareText = text,
                        errorMessage = null,
                        infoMessage = null,
                    )
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                val id = requireUserId() ?: return@launch
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                runCatching {
                    socialInteractor.refreshFriends(id)
                    socialInteractor.refreshSentInvites(id)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                err.message
                                    ?: appContext.getString(R.string.msg_could_not_refresh_friends),
                        )
                    }
                }
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        fun addFriend(
            name: String,
            contact: String,
            groupId: String? = null,
            onLinked: () -> Unit,
        ) {
            viewModelScope.launch {
                val id = requireUserId()
                if (id == null) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = appContext.getString(R.string.msg_not_signed_in),
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
                val result =
                    socialInteractor.addFriendByContact(
                        ownerUserId = id,
                        contact = contact,
                        displayName = name.trim().ifBlank { null },
                        groupId = groupId?.takeIf { it.isNotBlank() },
                    )
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
                                else -> appContext.getString(R.string.msg_friend_added)
                            },
                        pendingShareText = outcome?.inviteShareText,
                    )
                }
                if (outcome != null && !outcome.isInvitePending) {
                    onLinked()
                }
            }
        }

        /** Waits past [AuthSession.Loading] so actions work even before StateFlow warms up. */
        private suspend fun requireUserId(): String? {
            userId.value?.let { return it }
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }
    }
