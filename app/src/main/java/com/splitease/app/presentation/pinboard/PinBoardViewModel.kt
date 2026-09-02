package com.splitease.app.presentation.pinboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SaveState {
    IDLE,
    PENDING,
    SAVING,
    SAVED,
    ERROR,
}

data class PinBoardUiState(
    val content: String = "",
    val groupName: String = "",
    val isLoading: Boolean = true,
    val saveState: SaveState = SaveState.IDLE,
    val lastEditedBy: String? = null,
    val lastEditedAt: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class PinBoardViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val interactor: PinBoardInteractor,
        private val socialRemote: SocialRemoteDataSource,
        private val groupRepository: GroupRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _uiState = MutableStateFlow(PinBoardUiState())
        val uiState: StateFlow<PinBoardUiState> = _uiState.asStateFlow()

        private val contentChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)
        private var loadedGroupId: String? = null

        init {
            @OptIn(FlowPreview::class)
            viewModelScope.launch {
                contentChanges
                    .debounce(600)
                    .distinctUntilChanged()
                    .collectLatest { content ->
                        saveContent(content)
                    }
            }
        }

        fun load(groupId: String) {
            if (loadedGroupId == groupId && !_uiState.value.isLoading) return
            loadedGroupId = groupId
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                try {
                    val groupName = groupRepository.getGroupById(groupId)?.name.orEmpty()
                    val dto = interactor.load(groupId)
                    val editorName = dto.updatedBy?.let { uid ->
                        socialRemote.fetchProfileById(uid)?.displayName
                    }
                    _uiState.value = PinBoardUiState(
                        content = dto.content,
                        groupName = groupName,
                        isLoading = false,
                        saveState = SaveState.IDLE,
                        lastEditedBy = editorName,
                        lastEditedAt = dto.updatedAt,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = ErrorMessages.message(appContext, TAG, e),
                    )
                }
            }
        }

        fun onContentChanged(
            newContent: String,
            immediate: Boolean = false,
        ) {
            _uiState.value =
                _uiState.value.copy(
                    content = newContent,
                    saveState = SaveState.PENDING,
                )
            if (immediate) {
                viewModelScope.launch { saveContent(newContent) }
            } else {
                contentChanges.tryEmit(newContent)
            }
        }

        /**
         * Persists the current content immediately (bypassing debounce).
         */
        fun saveImmediately() {
            viewModelScope.launch {
                saveContent(_uiState.value.content)
            }
        }

        private suspend fun saveContent(content: String) {
            val gid = loadedGroupId ?: return
            val uid = userId.value ?: return
            if (_uiState.value.saveState == SaveState.SAVING) return
            try {
                _uiState.value = _uiState.value.copy(saveState = SaveState.SAVING, errorMessage = null)
                // Save locally first
                interactor.saveLocal(gid, content, uid)
                // Enqueue sync (Interactor.sync handles both local file paths and remote upsert)
                interactor.sync(gid)

                val editorName = socialRemote.fetchProfileById(uid)?.displayName
                _uiState.value = _uiState.value.copy(
                    saveState = SaveState.SAVED,
                    lastEditedBy = editorName,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saveState = SaveState.ERROR,
                    errorMessage = ErrorMessages.message(appContext, TAG, e),
                )
            }
        }

        override fun onCleared() {
            viewModelScope.launch(NonCancellable) {
                val gid = loadedGroupId ?: return@launch
                val uid = userId.value ?: return@launch
                if (_uiState.value.saveState == SaveState.PENDING) {
                    interactor.saveLocal(gid, _uiState.value.content, uid)
                    interactor.sync(gid)
                }
            }
            super.onCleared()
        }

        private companion object {
            const val TAG = "PinBoardViewModel"
        }
    }
