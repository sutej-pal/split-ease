package com.splitease.app.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.ErrorMessages
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloseAccountUiState(
    val isDeactivating: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CloseAccountViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authRepository: AuthRepository,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(CloseAccountUiState())
        val uiState: StateFlow<CloseAccountUiState> = _uiState.asStateFlow()

        fun clearError() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        fun deactivate() {
            if (_uiState.value.isDeactivating) return
            viewModelScope.launch {
                _uiState.update { it.copy(isDeactivating = true, errorMessage = null) }
                try {
                    val result = authRepository.deactivateOwnAccount()
                    val err = result.exceptionOrNull()
                    if (err != null) {
                        val msg =
                            if (ErrorMessages.isNetworkError(err)) {
                                appContext.getString(R.string.account_deactivate_error_offline)
                            } else {
                                ErrorMessages.message(appContext, TAG, err)
                            }
                        _uiState.update {
                            it.copy(errorMessage = msg)
                        }
                    }
                } finally {
                    _uiState.update {
                        it.copy(isDeactivating = false)
                    }
                }
            }
        }

        private companion object {
            const val TAG = "CloseAccountViewModel"
        }
    }
