package com.splitease.app.presentation.friends

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewFriendsUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val pendingShareTexts: List<String> = emptyList(),
    val completed: Boolean = false,
)

@HiltViewModel
class ReviewFriendsViewModel
    @Inject
    constructor(
        private val reviewStore: PendingFriendReviewStore,
        private val authRepository: AuthRepository,
        private val socialInteractor: SocialInteractor,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        val entries: StateFlow<List<ReviewFriendEntry>> =
            reviewStore.entries.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

        val groupId: StateFlow<String?> =
            reviewStore.groupId.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

        private val _uiState = MutableStateFlow(ReviewFriendsUiState())
        val uiState: StateFlow<ReviewFriendsUiState> = _uiState.asStateFlow()

        fun remove(entryId: String) {
            reviewStore.remove(entryId)
        }

        fun consumeShareTexts() {
            _uiState.update { it.copy(pendingShareTexts = emptyList()) }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        fun addFriends(onAllDone: () -> Unit) {
            viewModelScope.launch {
                val drafts = reviewStore.entries.value
                if (drafts.isEmpty()) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_review_empty))
                    }
                    return@launch
                }
                val missingContact = drafts.firstOrNull { it.contactValue.isBlank() }
                if (missingContact != null) {
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                appContext.getString(
                                    R.string.msg_review_contact_required,
                                    missingContact.displayName.ifBlank {
                                        appContext.getString(R.string.label_name)
                                    },
                                ),
                        )
                    }
                    return@launch
                }

                val ownerId = requireUserId()
                if (ownerId == null) {
                    _uiState.update {
                        it.copy(errorMessage = appContext.getString(R.string.msg_not_signed_in))
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        pendingShareTexts = emptyList(),
                    )
                }

                val shareTexts = mutableListOf<String>()
                var emailsSent = 0
                var firstError: String? = null
                val groupId = reviewStore.groupId.value

                for (draft in drafts) {
                    val result =
                        socialInteractor.addFriendByContact(
                            ownerUserId = ownerId,
                            contact = draft.contactValue,
                            displayName = draft.displayName.trim().ifBlank { null },
                            groupId = groupId,
                        )
                    val outcome = result.getOrNull()
                    if (outcome == null) {
                        firstError =
                            userFacingError(
                                result.exceptionOrNull()?.message
                                    ?: appContext.getString(R.string.msg_friend_add_failed),
                            )
                        break
                    }
                    // Drop successes immediately so a partial-failure retry does not
                    // re-process them (duplicate invites / emails).
                    reviewStore.remove(draft.id)
                    if (outcome.inviteEmailSent) {
                        emailsSent += 1
                    } else {
                        outcome.inviteShareText?.takeIf { it.isNotBlank() }?.let { shareTexts += it }
                    }
                }

                if (firstError != null) {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = firstError)
                    }
                    return@launch
                }

                reviewStore.clear()
                if (emailsSent > 0) {
                    val info =
                        if (emailsSent == 1) {
                            appContext.getString(R.string.msg_invite_email_sent_one)
                        } else {
                            appContext.getString(R.string.msg_invite_emails_sent, emailsSent)
                        }
                    Toast.makeText(appContext, info, Toast.LENGTH_SHORT).show()
                }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingShareTexts = shareTexts,
                        completed = true,
                    )
                }
                if (shareTexts.isEmpty()) {
                    onAllDone()
                }
            }
        }

        private suspend fun requireUserId(): String? {
            val session = authRepository.observeSession().first { it !is AuthSession.Loading }
            return (session as? AuthSession.SignedIn)?.user?.userId
        }

        private fun userFacingError(raw: String): String =
            when {
                raw.contains("FOREIGN KEY", ignoreCase = true) ||
                    raw.contains("SQLITE_CONSTRAINT_FOREIGNKEY", ignoreCase = true) ->
                    appContext.getString(R.string.msg_local_profile_missing)
                else -> raw
            }
    }
