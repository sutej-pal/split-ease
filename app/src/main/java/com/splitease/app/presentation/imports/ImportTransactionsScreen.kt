package com.splitease.app.presentation.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun ImportTransactionsScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ImportTransactionsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Could not read file.")
            }.onSuccess { text ->
                viewModel.loadCsv(text)
            }.onFailure {
                viewModel.loadCsv("")
            }
        }

    SeScreen(
        title = stringResource(R.string.import_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.import_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SeOutlinedButton(
                    text = stringResource(R.string.action_pick_csv),
                    onClick = { picker.launch(arrayOf("text/*", "text/csv", "application/csv")) },
                    enabled = !ui.isImporting,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.import_preview))
                if (ui.preview.isEmpty()) {
                    SeEmptyState(message = stringResource(R.string.import_preview_empty))
                } else {
                    ui.preview.forEach { row ->
                        SeListRow(
                            title = row.description,
                            subtitle =
                                "${row.currencyCode} ${row.amount.toPlainString()}" +
                                    (row.categoryName?.let { " · $it" } ?: ""),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SePrimaryButton(
                        text =
                            if (ui.isImporting) {
                                stringResource(R.string.import_in_progress)
                            } else {
                                stringResource(R.string.action_confirm_import)
                            },
                        onClick = { viewModel.confirmImport(onDone) },
                        enabled = !ui.isImporting,
                    )
                }
                ui.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
                ui.successMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(it)
                }
            }
        },
    )
}
