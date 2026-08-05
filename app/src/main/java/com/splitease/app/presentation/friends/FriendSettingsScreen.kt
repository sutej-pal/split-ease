package com.splitease.app.presentation.friends

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.presentation.theme.AmberLight
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

private val PendingInviteCardBg = Color(0xFFFFF3E0)

@Composable
fun FriendSettingsScreen(
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    onEditContact: () -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    viewModel: FriendSettingsViewModel = hiltViewModel(),
) {
    val friend by viewModel.friend.collectAsStateWithLifecycle()
    val sharedGroups by viewModel.sharedGroups.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    var showBlockConfirm by rememberSaveable { mutableStateOf(false) }
    var showReportConfirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)
    val displayName =
        friend
            ?.displayNameSnapshot
            ?.removeSuffix(" (invited)")
            ?.trim()
            .orEmpty()
    val firstName = viewModel.firstName()

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

    SeScreen(
        title = stringResource(R.string.friend_settings_title),
        onBack = onBack,
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
                FriendSettingsHeader(friend = friend)

                if (viewModel.isPendingInvite() && friend != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PendingInviteCard(
                        firstName = firstName,
                        contact = friend!!.emailSnapshot,
                        onEditContact = onEditContact,
                        onResendInvite = viewModel::resendInvite,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.friend_settings_shared_groups))
                if (sharedGroups.isEmpty()) {
                    Text(
                        text =
                            stringResource(
                                R.string.friend_settings_no_shared_groups,
                                firstName,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    sharedGroups.forEach { group ->
                        SharedGroupRow(group = group, onClick = { onOpenGroup(group.id) })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SeSectionHeader(text = stringResource(R.string.friend_settings_manage))

                ManageActionRow(
                    icon = Icons.Filled.PersonRemove,
                    title = stringResource(R.string.action_remove_from_friends),
                    subtitle = stringResource(R.string.friend_settings_remove_subtitle),
                    titleColor = SplitEaseColors.YouOwe,
                    iconTint = SplitEaseColors.YouOwe,
                    onClick = { showRemoveConfirm = true },
                )
                ManageActionRow(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.action_block_user),
                    subtitle = stringResource(R.string.friend_settings_block_subtitle),
                    onClick = { showBlockConfirm = true },
                )
                ManageActionRow(
                    icon = Icons.Filled.Report,
                    title = stringResource(R.string.action_report_user),
                    subtitle = stringResource(R.string.friend_settings_report_subtitle),
                    onClick = { showReportConfirm = true },
                    showDivider = false,
                )

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
                uiState.infoMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(it)
                }
            }
        },
    )

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = {
                Text(
                    stringResource(
                        R.string.friend_remove_confirm_title,
                        displayName.ifBlank { firstName },
                    ),
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.friend_remove_confirm_body))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.friend_remove_confirm_body_2))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        viewModel.removeFriend(onRemoved)
                    },
                    enabled = !uiState.isSubmitting,
                ) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        color = SplitEaseColors.Primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = SplitEaseColors.Primary,
                    )
                }
            },
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(stringResource(R.string.action_block_user)) },
            text = { Text(stringResource(R.string.friend_block_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        viewModel.blockFriend(onRemoved)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_block_user),
                        color = SplitEaseColors.YouOwe,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReportConfirm) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text(stringResource(R.string.action_report_user)) },
            text = { Text(stringResource(R.string.friend_report_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReportConfirm = false
                        viewModel.reportFriend()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_report_user),
                        color = SplitEaseColors.Primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FriendSettingsHeader(friend: Friend?) {
    val name = friend
        ?.displayNameSnapshot
        ?.removeSuffix(" (invited)")
        ?.trim()
        .orEmpty()
    val contact = friend?.emailSnapshot.orEmpty()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeIconTile(
            icon = Icons.Filled.Email,
            tint = SplitEaseColors.NavyMuted,
            size = 64,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { stringResource(R.string.friends_title) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
            )
            if (contact.isNotBlank()) {
                Text(
                    text = contact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
    }
}

@Composable
private fun PendingInviteCard(
    firstName: String,
    contact: String,
    onEditContact: () -> Unit,
    onResendInvite: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PendingInviteCardBg)
                .padding(16.dp),
    ) {
        Text(
            text =
                stringResource(
                    R.string.friend_invite_pending_card,
                    firstName,
                    contact,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.Navy,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PendingInviteAction(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.action_edit_contact_info),
            onClick = onEditContact,
        )
        Spacer(modifier = Modifier.height(4.dp))
        PendingInviteAction(
            icon = Icons.AutoMirrored.Filled.Send,
            label = stringResource(R.string.action_resend_invite),
            onClick = onResendInvite,
        )
    }
}

@Composable
private fun PendingInviteAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AmberLight, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
    }
}

@Composable
private fun SharedGroupRow(
    group: Group,
    onClick: () -> Unit,
) {
    SeListRow(
        title = group.name,
        onClick = onClick,
        leading = {
            SeIconTile(
                icon = Icons.Filled.Group,
                tint = SplitEaseColors.IconFriends,
                size = 40,
            )
        },
    )
}

@Composable
private fun ManageActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color? = null,
    iconTint: Color? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: SplitEaseColors.NavyMuted,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor ?: SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}
