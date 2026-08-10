package com.splitease.app.presentation.imports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.imports.TransactionImportInteractor
import com.splitease.app.domain.imports.CsvTransactionParser
import com.splitease.app.domain.imports.ImportedTransaction
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val preview: List<ImportedTransaction> = emptyList(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ImportTransactionsViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val importInteractor: TransactionImportInteractor,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val userId =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _ui = MutableStateFlow(ImportUiState())
        val ui: StateFlow<ImportUiState> = _ui.asStateFlow()

        private var pendingCsv: String? = null

        fun loadCsv(text: String) {
            viewModelScope.launch {
                _ui.update { it.copy(errorMessage = null, successMessage = null) }
                if (text.isBlank()) {
                    pendingCsv = null
                    _ui.update {
                        it.copy(preview = emptyList(), errorMessage = appContext.getString(R.string.msg_could_not_read_file))
                    }
                    return@launch
                }
                val currency = appSettingsRepository.getCurrencyCode()
                runCatching {
                    CsvTransactionParser.parse(text, currency)
                }.onSuccess { rows ->
                    pendingCsv = text
                    _ui.update { it.copy(preview = rows) }
                }.onFailure { err ->
                    pendingCsv = null
                    _ui.update {
                        it.copy(
                            preview = emptyList(),
                            errorMessage = err.message ?: appContext.getString(R.string.msg_could_not_parse_csv),
                        )
                    }
                }
            }
        }

        fun confirmImport(onDone: () -> Unit) {
            val csv = pendingCsv ?: return
            val me = userId.value ?: return
            viewModelScope.launch {
                _ui.update { it.copy(isImporting = true, errorMessage = null, successMessage = null) }
                val currency = appSettingsRepository.getCurrencyCode()
                val result =
                    runCatching {
                        importInteractor.importCsv(csv, me, currency)
                    }.getOrElse { err ->
                        _ui.update {
                            it.copy(
                                isImporting = false,
                                errorMessage = err.message ?: appContext.getString(R.string.msg_import_failed),
                            )
                        }
                        return@launch
                    }
                _ui.update {
                    it.copy(
                        isImporting = false,
                        successMessage =
                            if (result.failures.isEmpty()) {
                                appContext.getString(R.string.msg_imported_count, result.imported)
                            } else {
                                appContext.resources.getQuantityString(
                                    R.plurals.msg_synced_partial,
                                    result.failures.size,
                                    appContext.getString(R.string.msg_imported_count, result.imported),
                                    result.failures.size,
                                )
                            },
                        errorMessage = result.failures.firstOrNull(),
                        preview = if (result.failures.isEmpty()) emptyList() else it.preview,
                    )
                }
                if (result.imported > 0 && result.failures.isEmpty()) onDone()
            }
        }
    }
