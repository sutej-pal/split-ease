package com.splitease.app.presentation.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.account.AccountDeletionBlockingGroup
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    DeleteAccountContent(
        blockingGroups = uiState.blockingGroups,
        isLoadingBalances = uiState.isLoadingBalances,
        isRefreshingBalances = uiState.isRefreshingBalances,
        isDeleting = action.isDeleting,
        errorMessage = action.errorMessage,
        onBack = onBack,
        onDelete = viewModel::deleteAccount,
        onClearError = viewModel::clearError,
    )
}

@Composable
private fun DeleteAccountContent(
    blockingGroups: List<AccountDeletionBlockingGroup>,
    isLoadingBalances: Boolean,
    isRefreshingBalances: Boolean,
    isDeleting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit,
) {
    val confirmPhrase = stringResource(R.string.account_delete_confirm_phrase)
    var showTypedConfirm by rememberSaveable { mutableStateOf(false) }
    val blocked = blockingGroups.isNotEmpty()
    val busy = isDeleting || isRefreshingBalances
    val canRequestDelete = !blocked && !isLoadingBalances && !busy

    BackHandler(enabled = isDeleting) { }
    LaunchedEffect(isDeleting, errorMessage) {
        if (showTypedConfirm && !isDeleting && errorMessage != null) {
            showTypedConfirm = false
        }
    }

    SeScreen(
        title = stringResource(R.string.account_delete_title),
        onBack = {
            if (!isDeleting) onBack()
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.account_delete_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.account_delete_retained),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.account_delete_requires_network),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )

            if (isRefreshingBalances && !isDeleting) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.account_delete_refreshing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }

            if (blocked) {
                Spacer(modifier = Modifier.height(24.dp))
                SeSectionHeader(text = stringResource(R.string.account_delete_blocked_section))
                Text(
                    text = stringResource(R.string.account_delete_blocked_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                blockingGroups.forEach { group ->
                    Text(
                        text = "• ${group.groupName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SePrimaryButton(
                text = stringResource(R.string.account_delete_title),
                onClick = {
                    onClearError()
                    showTypedConfirm = true
                },
                enabled = canRequestDelete,
                isLoading = isDeleting,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SeErrorText(errorMessage)
            }
        }
    }

    if (showTypedConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.account_delete_title),
            body = stringResource(R.string.account_delete_typed_body, confirmPhrase),
            confirmLabel = stringResource(R.string.account_delete_title),
            onDismissRequest = {
                if (!isDeleting) showTypedConfirm = false
            },
            onConfirm = onDelete,
            icon = Icons.Filled.Delete,
            tone = SeConfirmTone.Danger,
            typedConfirmationPhrase = confirmPhrase,
            confirmBusy = isDeleting,
            dismissOnBackPress = !isDeleting,
            dismissOnClickOutside = !isDeleting,
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun DeleteAccountScreenPreview() {
    SePreview {
        DeleteAccountContent(
            blockingGroups =
                listOf(
                    AccountDeletionBlockingGroup(groupId = "g1", groupName = "Roommates"),
                    AccountDeletionBlockingGroup(groupId = "", groupName = "Non-group expenses"),
                ),
            isLoadingBalances = false,
            isRefreshingBalances = false,
            isDeleting = false,
            errorMessage = null,
            onBack = {},
            onDelete = {},
            onClearError = {},
        )
    }
}
