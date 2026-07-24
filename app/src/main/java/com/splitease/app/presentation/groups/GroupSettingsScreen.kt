package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTypeChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit,
    onAddPeople: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val membersState by remember(groupId) { viewModel.observeMembers(groupId) }
        .collectAsStateWithLifecycle()
    val members = membersState.orEmpty()
    val simplifyDebts by remember(groupId) { viewModel.observeSimplifyDebts(groupId) }
        .collectAsStateWithLifecycle()
    val group by remember(groupId) { viewModel.observeGroup(groupId) }
        .collectAsStateWithLifecycle()
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showSimplifyInfo by rememberSaveable { mutableStateOf(false) }
    var showDefaultSplitInfo by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val me = viewModel.currentUserId()
    val isOwner = group?.createdByUserId == me

    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, inviteSubject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(intent, shareInvite))
        viewModel.consumeShareText()
    }

    SeScreen(
        title = stringResource(R.string.group_settings_title),
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
                GroupSettingsHeader(
                    group = group,
                    onEdit = { showEdit = true },
                )
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(8.dp))

                SeSectionHeader(text = stringResource(R.string.group_settings_members_section))
                SettingsActionRow(
                    icon = Icons.Filled.PersonAdd,
                    title = stringResource(R.string.action_add_people_to_group),
                    onClick = onAddPeople,
                )
                val groupShareText = stringResource(R.string.group_share_placeholder, group?.name.orEmpty())
                SettingsActionRow(
                    icon = Icons.Filled.Link,
                    title = stringResource(R.string.action_invite_via_link),
                    onClick = {
                        viewModel.shareGroupLink(groupShareText)
                    },
                )
                members.forEach { member ->
                    val friend = friends.firstOrNull { it.friendUserId == member.userId }
                    val isYou = member.userId == me
                    val title =
                        if (isYou) {
                            stringResource(
                                R.string.member_you_label,
                                friend?.displayNameSnapshot ?: stringResource(R.string.you_label),
                            )
                        } else {
                            friend?.displayNameSnapshot ?: member.userId.take(8)
                        }
                    SeListRow(
                        title = title,
                        subtitle = friend?.emailSnapshot,
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Person,
                                tint = if (isYou) SplitEaseColors.OwedToYou else SplitEaseColors.IconOther,
                                size = 40,
                            )
                        },
                        showDivider = true,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SeSectionHeader(text = stringResource(R.string.group_settings_advanced_section))

                SettingsToggleRow(
                    icon = Icons.Filled.AccountTree,
                    title = stringResource(R.string.group_settings_simplify_title),
                    checked = simplifyDebts,
                    onCheckedChange = { viewModel.setSimplifyDebts(groupId, it) },
                )
                Text(
                    text = stringResource(R.string.group_settings_simplify_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier = Modifier.padding(start = 54.dp, end = 8.dp, bottom = 4.dp),
                )
                TextButton(
                    onClick = { showSimplifyInfo = true },
                    modifier = Modifier.padding(start = 40.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_learn_more),
                        color = SplitEaseColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(color = SplitEaseColors.Outline)

                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.group_settings_default_split),
                    subtitle = stringResource(R.string.group_settings_default_split_subtitle),
                    trailingBadge = stringResource(R.string.badge_pro),
                    onClick = { showDefaultSplitInfo = true },
                )
                Text(
                    text = stringResource(R.string.group_settings_default_split_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier = Modifier.padding(start = 54.dp, end = 8.dp, bottom = 12.dp),
                )
                HorizontalDivider(color = SplitEaseColors.Outline)

                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = stringResource(R.string.action_leave_group),
                    titleColor = SplitEaseColors.YouOwe,
                    iconTint = SplitEaseColors.YouOwe,
                    onClick = { showLeaveConfirm = true },
                )
                if (isOwner) {
                    SettingsActionRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.action_delete_group),
                        titleColor = SplitEaseColors.YouOwe,
                        iconTint = SplitEaseColors.YouOwe,
                        onClick = { showDeleteConfirm = true },
                        showDivider = false,
                    )
                }

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

    if (showEdit && group != null) {
        EditGroupDialog(
            group = group!!,
            isSubmitting = uiState.isSubmitting,
            onDismiss = { showEdit = false },
            onSave = { name, type ->
                val updated = group!!.copy(name = name, groupType = type)
                viewModel.updateGroup(updated)
                showEdit = false
            },
        )
    }

    if (showLeaveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.action_leave_group),
            body = stringResource(R.string.group_leave_confirm),
            confirmLabel = stringResource(R.string.action_leave_group),
            destructive = true,
            onDismiss = { showLeaveConfirm = false },
            onConfirm = {
                showLeaveConfirm = false
                viewModel.leaveGroup(groupId, onLeftOrDeleted)
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.action_delete_group),
            body = stringResource(R.string.group_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete_group),
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteGroup(groupId, onLeftOrDeleted)
            },
        )
    }

    if (showSimplifyInfo) {
        AlertDialog(
            onDismissRequest = { showSimplifyInfo = false },
            title = { Text(stringResource(R.string.group_settings_simplify_title)) },
            text = { Text(stringResource(R.string.group_settings_simplify_learn_more)) },
            confirmButton = {
                TextButton(onClick = { showSimplifyInfo = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    if (showDefaultSplitInfo) {
        AlertDialog(
            onDismissRequest = { showDefaultSplitInfo = false },
            title = { Text(stringResource(R.string.group_settings_default_split)) },
            text = { Text(stringResource(R.string.group_settings_default_split_pro_body)) },
            confirmButton = {
                TextButton(onClick = { showDefaultSplitInfo = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
}

@Composable
private fun GroupSettingsHeader(
    group: Group?,
    onEdit: () -> Unit,
) {
    val type = group?.groupType ?: GroupType.OTHER
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeIconTile(icon = type.settingsIcon(), tint = type.settingsTint(), size = 64)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
            )
            Text(
                text = stringResource(type.settingsLabelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = SplitEaseColors.Navy,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleColor: Color = SplitEaseColors.Navy,
    iconTint: Color = SplitEaseColors.NavyMuted,
    trailingBadge: String? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
            }
            if (trailingBadge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7C4DFF))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = trailingBadge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SplitEaseColors.NavyMuted, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SplitEaseColors.Primary,
                ),
        )
    }
}

@Composable
private fun EditGroupDialog(
    group: Group,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, type: GroupType) -> Unit,
) {
    var name by rememberSaveable(group.id) { mutableStateOf(group.name) }
    var type by rememberSaveable(group.id) { mutableStateOf(group.groupType.name) }
    val selected = runCatching { GroupType.valueOf(type) }.getOrDefault(GroupType.OTHER)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit)) },
        text = {
            Column {
                SeTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.label_group_name),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SeSectionHeader(text = stringResource(R.string.label_group_type))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeTypeChip(
                        label = stringResource(R.string.group_type_friends),
                        icon = Icons.Filled.Group,
                        selected = selected == GroupType.FRIENDS,
                        onClick = { type = GroupType.FRIENDS.name },
                        modifier = Modifier.weight(1f),
                    )
                    SeTypeChip(
                        label = stringResource(R.string.group_type_home),
                        icon = Icons.Filled.Home,
                        selected = selected == GroupType.HOME,
                        onClick = { type = GroupType.HOME.name },
                        modifier = Modifier.weight(1f),
                    )
                    SeTypeChip(
                        label = stringResource(R.string.group_type_other),
                        icon = Icons.AutoMirrored.Filled.List,
                        selected = selected == GroupType.OTHER,
                        onClick = { type = GroupType.OTHER.name },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) onSave(trimmed, selected)
                },
                enabled = !isSubmitting && name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) SplitEaseColors.YouOwe else SplitEaseColors.Primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

private fun GroupType.settingsIcon(): ImageVector =
    when (this) {
        GroupType.FRIENDS -> Icons.Filled.Group
        GroupType.HOME -> Icons.Filled.Home
        GroupType.OTHER -> Icons.AutoMirrored.Filled.List
    }

private fun GroupType.settingsTint(): Color =
    when (this) {
        GroupType.FRIENDS -> SplitEaseColors.IconFriends
        GroupType.HOME -> SplitEaseColors.IconHome
        GroupType.OTHER -> SplitEaseColors.IconOther
    }

private fun GroupType.settingsLabelRes(): Int =
    when (this) {
        GroupType.FRIENDS -> R.string.group_type_friends
        GroupType.HOME -> R.string.group_type_home
        GroupType.OTHER -> R.string.group_type_other
    }
