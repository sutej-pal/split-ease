package com.splitease.app.presentation.pinboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinBoardUiState(
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val lastEditedBy: String? = null,
    val lastEditedAt: String? = null,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PinBoardViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val interactor: PinBoardInteractor,
        private val socialRemote: SocialRemoteDataSource,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _uiState = MutableStateFlow(PinBoardUiState())
        val uiState: StateFlow<PinBoardUiState> = _uiState.asStateFlow()

        private var loadedGroupId: String? = null

        private val saveSignal = MutableSharedFlow<String>(extraBufferCapacity = 1)

        init {
            saveSignal
                .debounce(2_000L)
                .onEach { content -> persistQuietly(content) }
                .launchIn(viewModelScope)
        }

        fun load(groupId: String) {
            if (loadedGroupId == groupId && !_uiState.value.isLoading) return
            loadedGroupId = groupId
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                try {
                    val dto = interactor.load(groupId)
                    val editorName = dto.updatedBy?.let { uid ->
                        socialRemote.fetchProfileById(uid)?.displayName
                    }
                    _uiState.value = PinBoardUiState(
                        content = dto.content,
                        isLoading = false,
                        lastEditedBy = editorName,
                        lastEditedAt = dto.updatedAt,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Could not load pin board.",
                    )
                }
            }
        }

        fun onContentChanged(newContent: String) {
            _uiState.value = _uiState.value.copy(content = newContent)
            saveSignal.tryEmit(newContent)
        }

        /**
         * Force-save immediately (called on back navigation).
         */
        fun saveNow() {
            val content = _uiState.value.content
            viewModelScope.launch { persistQuietly(content) }
        }

        private suspend fun persistQuietly(content: String) {
            val gid = loadedGroupId ?: return
            val uid = userId.value ?: return
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                interactor.save(gid, content, uid)
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }
