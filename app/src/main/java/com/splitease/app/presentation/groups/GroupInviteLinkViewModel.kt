package com.splitease.app.presentation.groups

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the group invite-link management screen.
 *
 * @property isLoading True while resolving or regenerating the link.
 * @property isChanging True while regenerating a new token.
 * @property groupName Display name for trust copy.
 * @property inviteUrl Absolute https invite URL, or null when unavailable.
 * @property shareText Plain share body for the system share sheet.
 * @property errorMessage Last error, if any.
 * @property infoMessage Transient success message (e.g. copied).
 * @property pendingShareText Share text queued for the system share sheet.
 */
data class GroupInviteLinkUiState(
    val isLoading: Boolean = true,
    val isChanging: Boolean = false,
    val groupName: String = "",
    val inviteUrl: String? = null,
    val shareText: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingShareText: String? = null,
)

/**
 * Loads, copies, shares, and regenerates a group's generic invite link.
 */
@HiltViewModel
class GroupInviteLinkViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        private val socialInteractor: SocialInteractor,
    ) : ViewModel() {
        private val groupId: String = checkNotNull(savedStateHandle["groupId"])

        private val _uiState = MutableStateFlow(GroupInviteLinkUiState())
        val uiState: StateFlow<GroupInviteLinkUiState> = _uiState.asStateFlow()

        init {
            loadLink()
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
        }

        fun consumeShareText() {
            _uiState.update { it.copy(pendingShareText = null) }
        }

        /**
         * Ensures a pending group share link exists and updates UI state.
         */
        fun loadLink() {
            viewModelScope.launch {
                val ownerId = currentUserId()
                if (ownerId == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = appContext.getString(R.string.msg_not_signed_in),
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(isLoading = true, errorMessage = null, infoMessage = null)
                }
                val result = socialInteractor.getOrCreateGroupShareLink(ownerId, groupId)
                val link = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        groupName = link?.groupName.orEmpty(),
                        inviteUrl = link?.url,
                        shareText = link?.shareText,
                        errorMessage = friendlyInviteError(result.exceptionOrNull()),
                    )
                }
            }
        }

        /**
         * Copies the https invite URL to the clipboard.
         */
        fun copyLink() {
            val url = _uiState.value.inviteUrl
            if (url.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        errorMessage = appContext.getString(R.string.msg_invite_link_unavailable),
                        infoMessage = null,
                    )
                }
                return
            }
            val clipboard = appContext.getSystemService<ClipboardManager>()
            clipboard?.setPrimaryClip(ClipData.newPlainText("SplitEase invite", url))
            _uiState.update {
                it.copy(
                    infoMessage = appContext.getString(R.string.invite_link_copied),
                    errorMessage = null,
                )
            }
        }

        /**
         * Queues the share body for the system share sheet.
         */
        fun shareLink() {
            val text = _uiState.value.shareText
            if (text.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        errorMessage = appContext.getString(R.string.msg_invite_link_unavailable),
                        infoMessage = null,
                    )
                }
                return
            }
            _uiState.update {
                it.copy(
                    pendingShareText = text,
                    errorMessage = null,
                    infoMessage = null,
                )
            }
        }

        /**
         * Cancels the current generic link and creates a new token.
         */
        fun changeLink() {
            viewModelScope.launch {
                val ownerId = currentUserId()
                if (ownerId == null) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isChanging = true,
                        errorMessage = null,
                        infoMessage = null,
                    )
                }
                val result = socialInteractor.regenerateGroupShareLink(ownerId, groupId)
                val link = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isChanging = false,
                        groupName = link?.groupName ?: it.groupName,
                        inviteUrl = link?.url,
                        shareText = link?.shareText,
                        errorMessage = friendlyInviteError(result.exceptionOrNull()),
                        infoMessage =
                            if (link != null) {
                                appContext.getString(R.string.invite_link_changed)
                            } else {
                                null
                            },
                    )
                }
            }
        }

        private suspend fun currentUserId(): String? {
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }
        private fun friendlyInviteError(error: Throwable?): String? {
            if (error == null) return null
            val raw = error.message.orEmpty()
            val lower = raw.lowercase()
            return when {
                "row-level security" in lower || "42501" in lower ->
                    appContext.getString(R.string.msg_group_sync_rls)
                raw.isNotBlank() && raw.length <= 160 &&
                    "url:" !in lower && "headers:" !in lower ->
                    raw.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank {
                        appContext.getString(R.string.error_generic)
                    }
                else -> appContext.getString(R.string.error_generic)
            }
        }
    }
