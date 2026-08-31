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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinBoardUiState(
    val content: String = "",
    val groupName: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
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

        private var loadedGroupId: String? = null

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
                        isDirty = false,
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

        fun onContentChanged(newContent: String) {
            _uiState.value = _uiState.value.copy(
                content = newContent,
                isDirty = true,
            )
        }

        /**
         * Persists the current content when the user taps Save.
         */
        fun save() {
            val content = _uiState.value.content
            val gid = loadedGroupId ?: return
            val uid = userId.value ?: return
            if (!_uiState.value.isDirty || _uiState.value.isSaving) return
            viewModelScope.launch {
                try {
                    _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
                    val synced = interactor.save(gid, content, uid)
                    val editorName = socialRemote.fetchProfileById(uid)?.displayName
                    _uiState.value = _uiState.value.copy(
                        content = synced,
                        isSaving = false,
                        isDirty = false,
                        lastEditedBy = editorName,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = ErrorMessages.message(appContext, TAG, e),
                    )
                }
            }
        }

        /**
         * Uploads [localPath] when possible and returns a display URL (remote preferred).
         */
        suspend fun ensureImageUploaded(
            groupId: String,
            localPath: String,
        ): String = interactor.uploadLocalImage(groupId, localPath) ?: localPath

        private companion object {
            const val TAG = "PinBoardViewModel"
        }
    }
