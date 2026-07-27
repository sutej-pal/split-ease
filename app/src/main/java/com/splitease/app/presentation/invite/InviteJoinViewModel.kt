package com.splitease.app.presentation.invite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.InvitePreview
import com.splitease.app.domain.model.pendingOpenTarget
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the invite deep-link landing / join flow.
 *
 * @property token Pending invite token from the deep link.
 * @property isLoading True while preview is loading.
 * @property preview Loaded invite preview, when available.
 * @property errorMessage User-visible error, if any.
 */
data class InviteJoinUiState(
    val token: String? = null,
    val isLoading: Boolean = false,
    val preview: InvitePreview? = null,
    val errorMessage: String? = null,
)

/**
 * Loads invite landing preview and persists the token for post-OTP accept.
 */
@HiltViewModel
class InviteJoinViewModel
    @Inject
    constructor(
        private val socialInteractor: SocialInteractor,
        private val appSettingsRepository: AppSettingsRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(InviteJoinUiState())

        /** Landing / join UI state. */
        val uiState: StateFlow<InviteJoinUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val stored = appSettingsRepository.getPendingInviteToken()
                if (!stored.isNullOrBlank()) {
                    loadPreview(stored)
                }
            }
        }

        /**
         * Stores [token] and loads the public invite preview.
         *
         * @param token Opaque invite token from the deep link.
         */
        fun onInviteToken(token: String) {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                appSettingsRepository.setPendingInviteToken(trimmed)
                loadPreview(trimmed)
            }
        }

        /** Clears a transient error message. */
        fun clearError() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        /**
         * Dismisses the invite flow without joining.
         */
        fun dismissInvite() {
            viewModelScope.launch {
                appSettingsRepository.setPendingInviteToken(null)
                appSettingsRepository.setPendingInviteOpenTarget(null)
                _uiState.value = InviteJoinUiState()
            }
        }

        private suspend fun loadPreview(token: String) {
            _uiState.update {
                it.copy(
                    token = token,
                    isLoading = true,
                    errorMessage = null,
                )
            }
            val preview =
                runCatching { socialInteractor.loadInvitePreview(token) }
                    .getOrElse { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage =
                                    error.localizedMessage
                                        ?: appContext.getString(R.string.invite_preview_failed),
                            )
                        }
                        return
                    }
            if (preview == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        preview = null,
                        errorMessage = appContext.getString(R.string.invite_not_found),
                    )
                }
            } else {
                appSettingsRepository.setPendingInviteOpenTarget(preview.pendingOpenTarget())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        preview = preview,
                        errorMessage = null,
                    )
                }
            }
        }
    }
