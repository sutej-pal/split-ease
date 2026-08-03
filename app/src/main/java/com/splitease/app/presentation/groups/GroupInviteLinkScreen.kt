package com.splitease.app.presentation.groups

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeScreen

/**
 * Manage a group's shareable invite link: copy, share, or regenerate.
 *
 * @param onBack Navigate back to group settings.
 * @param viewModel Invite-link ViewModel (Hilt).
 */
@Composable
fun GroupInviteLinkScreen(
    onBack: () -> Unit,
    viewModel: GroupInviteLinkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val html = InviteLinks.htmlForShareText(text)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, inviteSubject)
                putExtra(Intent.EXTRA_TEXT, text)
                if (html != null) {
                    putExtra(Intent.EXTRA_HTML_TEXT, html)
                }
            }
        context.startActivity(Intent.createChooser(intent, shareInvite))
        viewModel.consumeShareText()
    }

    LaunchedEffect(uiState.infoMessage) {
        val message = uiState.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        viewModel.clearMessages()
    }

    SeScreen(
        title = stringResource(R.string.invite_link_screen_title),
        onBack = onBack,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SplitEaseColors.Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.invite_link_trust_body,
                            uiState.groupName.ifBlank { stringResource(R.string.this_group_label) },
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(24.dp))

                when {
                    uiState.isLoading || uiState.isChanging -> {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = SplitEaseColors.Primary,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text =
                                    if (uiState.isChanging) {
                                        stringResource(R.string.invite_link_changing)
                                    } else {
                                        stringResource(R.string.invite_link_loading)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SplitEaseColors.NavyMuted,
                            )
                        }
                    }
                    uiState.inviteUrl != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SplitEaseColors.Primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = uiState.inviteUrl.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = SplitEaseColors.Navy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)

                InviteLinkActionRow(
                    icon = Icons.Filled.ContentCopy,
                    title = stringResource(R.string.action_copy_link),
                    enabled = !uiState.isLoading && !uiState.isChanging && uiState.inviteUrl != null,
                    onClick = viewModel::copyLink,
                )
                InviteLinkActionRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.action_share_link),
                    enabled = !uiState.isLoading && !uiState.isChanging && uiState.shareText != null,
                    onClick = viewModel::shareLink,
                )
                InviteLinkActionRow(
                    icon = Icons.Filled.RemoveCircleOutline,
                    title = stringResource(R.string.action_change_link),
                    enabled = !uiState.isLoading && !uiState.isChanging,
                    onClick = viewModel::changeLink,
                    showDivider = false,
                )

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeErrorText(it)
                }
            }
        },
    )
}

@Composable
private fun InviteLinkActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (enabled) {
                        SplitEaseColors.Navy
                    } else {
                        SplitEaseColors.NavyMuted.copy(alpha = 0.5f)
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (enabled) {
                        SplitEaseColors.Navy
                    } else {
                        SplitEaseColors.NavyMuted.copy(alpha = 0.5f)
                    },
            )
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}
