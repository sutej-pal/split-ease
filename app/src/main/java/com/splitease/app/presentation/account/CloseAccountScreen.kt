package com.splitease.app.presentation.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun CloseAccountScreen(
    onBack: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    viewModel: CloseAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CloseAccountContent(
        uiState = uiState,
        onBack = onBack,
        onOpenDeleteAccount = onOpenDeleteAccount,
        onDeactivate = viewModel::deactivate,
        onClearError = viewModel::clearError,
    )
}

@Composable
private fun CloseAccountContent(
    uiState: CloseAccountUiState,
    onBack: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    onDeactivate: () -> Unit,
    onClearError: () -> Unit,
) {
    var showDeactivateConfirm by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = uiState.isDeactivating) { }

    LaunchedEffect(uiState.isDeactivating, uiState.errorMessage) {
        if (showDeactivateConfirm && !uiState.isDeactivating && uiState.errorMessage != null) {
            showDeactivateConfirm = false
        }
    }

    SeScreen(
        title = stringResource(R.string.account_close_title),
        onBack = {
            if (!uiState.isDeactivating) onBack()
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
                text = stringResource(R.string.account_close_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )

            Spacer(modifier = Modifier.height(24.dp))
            SeSectionHeader(text = stringResource(R.string.account_deactivate_section))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SplitEaseColors.Surface)
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_deactivate_body_1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.account_deactivate_body_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeOutlinedButton(
                    text = stringResource(R.string.account_deactivate_action),
                    onClick = {
                        onClearError()
                        showDeactivateConfirm = true
                    },
                    enabled = !uiState.isDeactivating,
                    contentColor = SplitEaseColors.YouOwe,
                )
                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(text = uiState.errorMessage)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SeSectionHeader(text = stringResource(R.string.account_delete_permanent_section))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SplitEaseColors.Surface)
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_delete_permanent_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeOutlinedButton(
                    text = stringResource(R.string.account_delete_title),
                    onClick = onOpenDeleteAccount,
                    enabled = !uiState.isDeactivating,
                    contentColor = SplitEaseColors.YouOwe,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }

    if (showDeactivateConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.account_deactivate_action),
            body = stringResource(R.string.account_deactivate_confirm_body),
            confirmLabel = stringResource(R.string.account_deactivate_action),
            onDismissRequest = {
                if (!uiState.isDeactivating) showDeactivateConfirm = false
            },
            onConfirm = onDeactivate,
            icon = Icons.Filled.Delete,
            tone = SeConfirmTone.Danger,
            confirmBusy = uiState.isDeactivating,
            dismissOnBackPress = !uiState.isDeactivating,
            dismissOnClickOutside = !uiState.isDeactivating,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CloseAccountScreenPreview() {
    SePreview {
        CloseAccountContent(
            uiState = CloseAccountUiState(),
            onBack = {},
            onOpenDeleteAccount = {},
            onDeactivate = {},
            onClearError = {},
        )
    }
}
