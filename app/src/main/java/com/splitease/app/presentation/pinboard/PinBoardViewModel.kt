package com.splitease.app.presentation.pinboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.pinboard.PinBoardDto
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
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
    /** Bumped when [content] is replaced from load/refresh (not from typing). */
    val contentRevision: Long = 0,
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
        private var initialLoadDone: Boolean = false
        private var refreshJob: Job? = null

        init {
            @OptIn(FlowPreview::class)
            viewModelScope.launch {
                contentChanges
                    .debounce(AUTOSAVE_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collectLatest { content ->
                        saveContent(content)
                    }
            }
        }

        fun load(groupId: String) {
            if (loadedGroupId == groupId && initialLoadDone && !_uiState.value.isLoading) return
            loadedGroupId = groupId
            initialLoadDone = false
            viewModelScope.launch {
                val groupName = groupRepository.getGroupById(groupId)?.name.orEmpty()
                val cached = interactor.peekLocal(groupId)
                if (cached != null) {
                    applyLoadedDto(cached, groupName = groupName, isLoading = false)
                } else {
                    _uiState.value =
                        PinBoardUiState(
                            groupName = groupName,
                            isLoading = true,
                            errorMessage = null,
                        )
                }
                try {
                    val dto = interactor.load(groupId)
                    applyLoadedDto(dto, groupName = groupName, isLoading = false)
                    initialLoadDone = true
                    startRemoteRefreshLoop()
                } catch (e: Exception) {
                    initialLoadDone = true
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = ErrorMessages.message(appContext, TAG, e),
                        )
                    startRemoteRefreshLoop()
                }
            }
        }

        /** Pulls the cloud board when this screen is shown again and there is no unsaved draft. */
        fun refreshFromRemote() {
            val gid = loadedGroupId ?: return
            if (!initialLoadDone) return
            val state = _uiState.value
            if (state.isLoading || state.saveState == SaveState.PENDING || state.saveState == SaveState.SAVING) {
                return
            }
            viewModelScope.launch {
                runCatching {
                    val dto = interactor.load(gid)
                    applyLoadedDto(dto, groupName = state.groupName, isLoading = false)
                }.onFailure { error ->
                    Log.w(TAG, "Background pin board refresh failed for $gid", error)
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

        /** Persists the current content immediately (bypassing debounce). */
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
                interactor.saveLocal(gid, content, uid)
                interactor.sync(gid)

                val editorName = socialRemote.fetchProfileById(uid)?.displayName
                val latest = _uiState.value.content
                val stillDirty = latest != content
                _uiState.value =
                    _uiState.value.copy(
                        saveState = if (stillDirty) SaveState.PENDING else SaveState.SAVED,
                        lastEditedBy = editorName,
                    )
                if (stillDirty) {
                    saveContent(latest)
                }
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        saveState = SaveState.ERROR,
                        errorMessage = ErrorMessages.message(appContext, TAG, e),
                    )
            }
        }

        private suspend fun applyLoadedDto(
            dto: PinBoardDto,
            groupName: String,
            isLoading: Boolean,
        ) {
            val prev = _uiState.value
            if (prev.saveState == SaveState.PENDING || prev.saveState == SaveState.SAVING) {
                _uiState.value =
                    prev.copy(
                        groupName = groupName,
                        isLoading = isLoading,
                    )
                return
            }
            val editorName =
                dto.updatedBy?.let { uid ->
                    socialRemote.fetchProfileById(uid)?.displayName
                }
            val contentChanged = dto.content != prev.content
            _uiState.value =
                prev.copy(
                    content = dto.content,
                    groupName = groupName,
                    isLoading = isLoading,
                    lastEditedBy = editorName,
                    lastEditedAt = dto.updatedAt,
                    errorMessage = null,
                    contentRevision = if (contentChanged) prev.contentRevision + 1 else prev.contentRevision,
                    saveState =
                        when {
                            !contentChanged -> prev.saveState
                            prev.saveState == SaveState.SAVED -> SaveState.SAVED
                            else -> SaveState.IDLE
                        },
                )
        }

        private fun startRemoteRefreshLoop() {
            refreshJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    while (isActive) {
                        delay(REMOTE_REFRESH_MS)
                        refreshFromRemote()
                    }
                }
        }

        public override fun onCleared() {
            refreshJob?.cancel()
            viewModelScope.launch(NonCancellable) {
                val gid = loadedGroupId ?: return@launch
                val uid = userId.value ?: return@launch
                if (_uiState.value.saveState == SaveState.PENDING || _uiState.value.saveState == SaveState.ERROR) {
                    interactor.saveLocal(gid, _uiState.value.content, uid)
                    interactor.sync(gid)
                }
            }
            super.onCleared()
        }

        private companion object {
            const val TAG = "PinBoardViewModel"
            const val AUTOSAVE_DEBOUNCE_MS = 2_000L
            const val REMOTE_REFRESH_MS = 15_000L
        }
    }
