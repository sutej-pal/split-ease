package com.splitease.app.presentation.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.account.AccountDeletionBlockingGroup
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeLineSkeleton
import com.splitease.app.presentation.ui.SeShimmerHost
import com.splitease.app.presentation.ui.seShimmer
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onOpenNonGroupExpenses: () -> Unit,
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
        onOpenGroup = onOpenGroup,
        onOpenNonGroupExpenses = onOpenNonGroupExpenses,
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
    onOpenGroup: (groupId: String) -> Unit,
    onOpenNonGroupExpenses: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit,
) {
    val confirmPhrase = stringResource(R.string.account_delete_confirm_phrase)
    var showTypedConfirm by rememberSaveable { mutableStateOf(false) }
    val blocked = blockingGroups.isNotEmpty()
    val isCheckingBalances = isLoadingBalances || isRefreshingBalances
    val showBlockingSection = isCheckingBalances || blocked
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

            if (isRefreshingBalances && !isDeleting) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SplitEaseColors.PrimarySoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SplitEaseColors.Primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.account_delete_refreshing),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = SplitEaseColors.Primary,
                    )
                }
            }

            if (showBlockingSection) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = SplitEaseColors.YouOwe,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.account_delete_blocked_section).uppercase(),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                            ),
                        color = SplitEaseColors.YouOwe,
                    )
                }
                Text(
                    text = stringResource(R.string.account_delete_blocked_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SeShimmerHost(enabled = isCheckingBalances) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SplitEaseColors.Surface),
                    ) {
                        if (isCheckingBalances) {
                            val count = 3
                            repeat(count) { index ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .seShimmer(),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    SeLineSkeleton(
                                        modifier = Modifier.weight(1f),
                                        widthFraction = 0.6f,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (index < count - 1) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = SplitEaseColors.Outline,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        } else {
                            blockingGroups.forEachIndexed { index, group ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .alpha(if (busy) 0.5f else 1f)
                                            .clickable(
                                                enabled = !busy,
                                                onClick = {
                                                    if (group.groupId.isNotBlank()) {
                                                        onOpenGroup(group.groupId)
                                                    } else {
                                                        onOpenNonGroupExpenses()
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SeIconTile(
                                        icon = if (group.groupId.isNotBlank()) Icons.Filled.Group else Icons.Filled.Receipt,
                                        tint = SplitEaseColors.YouOwe,
                                        size = 32,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = group.groupName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = SplitEaseColors.Navy,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = SplitEaseColors.NavyMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (index < blockingGroups.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = SplitEaseColors.Outline,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
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
                leadingIcon =
                    when {
                        isDeleting -> null
                        !canRequestDelete -> Icons.Filled.Lock
                        else -> Icons.Filled.Delete
                    },
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

@Preview(showBackground = true)
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
            onOpenGroup = {},
            onOpenNonGroupExpenses = {},
            onDelete = {},
            onClearError = {},
        )
    }
}
