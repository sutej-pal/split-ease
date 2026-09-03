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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTypeChip
import com.splitease.app.presentation.ui.seEntityHeaderStyle
import java.math.BigDecimal

private data class SelectedGroupMember(
    val userId: String,
    val displayName: String,
    val email: String?,
    val pending: Boolean,
    val photoUrl: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit,
    onAddPeople: () -> Unit,
    onInviteViaLink: () -> Unit,
    onViewMemberSettings: (friendUserId: String) -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val userDisplayNames by viewModel.userDisplayNames.collectAsStateWithLifecycle()
    val userPhotoUrls by viewModel.userPhotoUrls.collectAsStateWithLifecycle()
    val membersState by remember(groupId) { viewModel.observeMembers(groupId) }
        .collectAsStateWithLifecycle()
    val members = membersState.orEmpty()
    val simplifyDebts by remember(groupId) { viewModel.observeSimplifyDebts(groupId) }
        .collectAsStateWithLifecycle()
    val groupMuted by remember(groupId) { viewModel.observeGroupNotificationsMuted(groupId) }
        .collectAsStateWithLifecycle()
    val group by remember(groupId) { viewModel.observeGroup(groupId) }
        .collectAsStateWithLifecycle()
    val groupBalance by remember(groupId) { balancesViewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showSimplifyInfo by rememberSaveable { mutableStateOf(false) }
    var showDefaultSplitInfo by rememberSaveable { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<SelectedGroupMember?>(null) }
    var memberPendingRemove by remember { mutableStateOf<SelectedGroupMember?>(null) }
    val memberSheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val me = viewModel.currentUserId()
    val isOwner = group?.createdByUserId == me
    val currencyFallback = group?.defaultCurrencyCode.orEmpty()
    val photoPicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.group_photo_source_title),
            sourceBody = stringResource(R.string.group_photo_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.GroupPhoto,
        ) { uri ->
            viewModel.updateGroupPhoto(groupId, uri)
        }

    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)

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

    LaunchedEffect(uiState.pendingFileShare) {
        val share = uiState.pendingFileShare ?: return@LaunchedEffect
        val launched = CsvFileShare.share(context, share)
        if (!launched) {
            viewModel.onFileShareLaunchFailed()
        }
        viewModel.consumeFileShare()
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
                    onChangePhoto = { photoPicker.launch() },
                )
                SettingsActionRow(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(R.string.action_edit_group_photo),
                    onClick = { photoPicker.launch() },
                )
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(8.dp))

                SeSectionHeader(text = stringResource(R.string.group_settings_members_section))
                SettingsActionRow(
                    icon = Icons.Filled.PersonAdd,
                    title = stringResource(R.string.action_add_people_to_group),
                    onClick = onAddPeople,
                )
                SettingsActionRow(
                    icon = Icons.Filled.Link,
                    title = stringResource(R.string.action_invite_via_link),
                    onClick = onInviteViaLink,
                )
                members.forEach { member ->
                    val friend = friends.firstOrNull { it.friendUserId == member.userId }
                    val isYou = member.userId == me
                    val pending =
                        friend?.displayNameSnapshot?.contains("(invited)", ignoreCase = true) == true
                    val rawName =
                        friend?.displayNameSnapshot
                            ?.removeSuffix(" (invited)")
                            ?.trim()
                            .orEmpty()
                            .ifBlank {
                                userDisplayNames[member.userId]
                                    ?: member.userId.take(8)
                            }
                    val title =
                        if (isYou) {
                            stringResource(
                                R.string.member_you_label,
                                friend?.displayNameSnapshot
                                    ?.removeSuffix(" (invited)")
                                    ?.trim()
                                    ?.ifBlank { null }
                                    ?: userDisplayNames[member.userId]
                                    ?: stringResource(R.string.you_label),
                            )
                        } else if (pending) {
                            friend?.displayNameSnapshot
                                ?: userDisplayNames[member.userId]
                                ?: member.userId.take(8)
                        } else {
                            rawName
                        }
                    val memberNets = memberNets(groupBalance, member.userId)
                    SeListRow(
                        title = title,
                        subtitle =
                            when {
                                pending ->
                                    listOfNotNull(
                                        friend?.emailSnapshot,
                                        stringResource(R.string.invite_pending_label),
                                    ).joinToString(" · ")
                                else -> friend?.emailSnapshot
                            },
                        leading = {
                            SeAvatarBadge(
                                name =
                                    if (isYou) {
                                        stringResource(R.string.you_label)
                                    } else {
                                        rawName.ifBlank { title }
                                    },
                                photoUrl = userPhotoUrls[member.userId],
                                size = 40.dp,
                                borderWidth = 0.dp,
                            )
                        },
                        trailing =
                            when {
                                pending -> {
                                    {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    viewModel.copyInviteLinkForMember(member.userId)
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Filled.ContentCopy,
                                                    contentDescription =
                                                        stringResource(R.string.cd_copy_invite_link),
                                                    tint = SplitEaseColors.Primary,
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.shareInviteAgainForMember(member.userId)
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Filled.Share,
                                                    contentDescription =
                                                        stringResource(R.string.cd_share_invite_again),
                                                    tint = SplitEaseColors.Primary,
                                                )
                                            }
                                        }
                                    }
                                }
                                !isYou -> {
                                    {
                                        MemberNetStatus(
                                            netByCurrency = memberNets,
                                            currencyFallback = currencyFallback,
                                        )
                                    }
                                }
                                else -> null
                            },
                        onClick =
                            if (!isYou) {
                                {
                                    selectedMember =
                                        SelectedGroupMember(
                                            userId = member.userId,
                                            displayName = rawName.ifBlank { title },
                                            email = friend?.emailSnapshot,
                                            pending = pending,
                                            photoUrl = userPhotoUrls[member.userId],
                                        )
                                }
                            } else {
                                null
                            },
                        showDivider = true,
                    )
                }

                SettingsActionRow(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.group_settings_export_csv),
                    subtitle =
                        stringResource(
                            if (uiState.isExporting) {
                                R.string.group_settings_export_csv_working
                            } else {
                                R.string.group_settings_export_csv_subtitle
                            },
                        ),
                    enabled = !uiState.isExporting,
                    showProgress = uiState.isExporting,
                    onClick = { viewModel.exportGroupCsv(groupId) },
                )

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
                SeTextButton(
                    text = stringResource(R.string.action_learn_more),
                    onClick = { showSimplifyInfo = true },
                    emphasized = true,
                    modifier = Modifier.padding(start = 40.dp),
                )
                HorizontalDivider(color = SplitEaseColors.Outline)

                SettingsToggleRow(
                    icon = Icons.Filled.NotificationsOff,
                    title = stringResource(R.string.group_settings_mute_title),
                    checked = groupMuted,
                    onCheckedChange = { viewModel.setGroupNotificationsMuted(groupId, it) },
                )
                Text(
                    text = stringResource(R.string.group_settings_mute_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier = Modifier.padding(start = 54.dp, end = 8.dp, bottom = 12.dp),
                )
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
        SeConfirmDialog(
            title = stringResource(R.string.action_leave_group),
            body = stringResource(R.string.group_leave_confirm),
            confirmLabel = stringResource(R.string.action_leave_group),
            onDismissRequest = { showLeaveConfirm = false },
            onConfirm = {
                showLeaveConfirm = false
                viewModel.leaveGroup(groupId, onLeftOrDeleted)
            },
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            tone = SeConfirmTone.Danger,
        )
    }

    if (showDeleteConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.action_delete_group),
            body = stringResource(R.string.group_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete_group),
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteGroup(groupId, onLeftOrDeleted)
            },
            icon = Icons.Filled.Delete,
            tone = SeConfirmTone.Danger,
        )
    }

    if (showSimplifyInfo) {
        SeConfirmDialog(
            title = stringResource(R.string.group_settings_simplify_title),
            body = stringResource(R.string.group_settings_simplify_learn_more),
            onDismissRequest = { showSimplifyInfo = false },
            icon = Icons.Filled.AccountTree,
            tone = SeConfirmTone.Primary,
        )
    }

    if (showDefaultSplitInfo) {
        SeConfirmDialog(
            title = stringResource(R.string.group_settings_default_split),
            body = stringResource(R.string.group_settings_default_split_pro_body),
            onDismissRequest = { showDefaultSplitInfo = false },
            icon = Icons.Filled.AccountTree,
            tone = SeConfirmTone.Primary,
        )
    }

    val sheetMember = selectedMember
    if (sheetMember != null) {
        val nets = memberNets(groupBalance, sheetMember.userId)
        val canRemove = nets.isEmpty()
        ModalBottomSheet(
            onDismissRequest = { selectedMember = null },
            sheetState = memberSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            GroupMemberActionsSheet(
                member = sheetMember,
                netByCurrency = nets,
                currencyFallback = currencyFallback,
                canRemove = canRemove,
                onViewSettings = {
                    val userId = sheetMember.userId
                    selectedMember = null
                    onViewMemberSettings(userId)
                },
                onRemove = {
                    if (canRemove) {
                        memberPendingRemove = sheetMember
                        selectedMember = null
                    }
                },
            )
        }
    }

    memberPendingRemove?.let { pending ->
        SeConfirmDialog(
            title = stringResource(R.string.action_remove_from_group),
            body = stringResource(R.string.member_remove_confirm, pending.displayName),
            confirmLabel = stringResource(R.string.action_remove),
            onDismissRequest = { memberPendingRemove = null },
            onConfirm = {
                val userId = pending.userId
                memberPendingRemove = null
                viewModel.removeGroupMember(groupId, userId)
            },
            icon = Icons.Filled.Person,
            tone = SeConfirmTone.Danger,
        )
    }
}

@Composable
private fun GroupMemberActionsSheet(
    member: SelectedGroupMember,
    netByCurrency: Map<String, BigDecimal>,
    currencyFallback: String,
    canRemove: Boolean,
    onViewSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeAvatarBadge(
                name = member.displayName,
                photoUrl = member.photoUrl,
                size = 48.dp,
                borderWidth = 0.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                if (!member.email.isNullOrBlank()) {
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
            }
            MemberNetStatus(
                netByCurrency = netByCurrency,
                currencyFallback = currencyFallback,
                settledLowercase = true,
            )
        }
        HorizontalDivider(
            color = SplitEaseColors.Outline,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        MemberSheetActionRow(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.action_view_settings),
            onClick = onViewSettings,
        )
        MemberSheetActionRow(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = stringResource(R.string.action_remove_from_group),
            titleColor = SplitEaseColors.YouOwe,
            iconTint = SplitEaseColors.YouOwe,
            enabled = canRemove,
            onClick = onRemove,
            showDivider = false,
            dimWhenDisabled = false,
        )
        if (!canRemove) {
            Text(
                text = stringResource(R.string.member_remove_blocked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SplitEaseColors.NavyMuted,
                modifier = Modifier.padding(start = 38.dp, end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun MemberSheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    titleColor: Color? = null,
    iconTint: Color? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    dimWhenDisabled: Boolean = true,
) {
    val alpha = if (!enabled && dimWhenDisabled) 0.45f else 1f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = (iconTint ?: SplitEaseColors.NavyMuted).copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = (titleColor ?: SplitEaseColors.Navy).copy(alpha = alpha),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}

@Composable
private fun MemberNetStatus(
    netByCurrency: Map<String, BigDecimal>,
    currencyFallback: String,
    settledLowercase: Boolean = false,
) {
    if (netByCurrency.isEmpty()) {
        val settled = stringResource(R.string.balances_settled_up)
        SeMoneyText(
            amount = BigDecimal.ZERO,
            currencyCode = currencyFallback.ifBlank { "USD" },
            tone = SeMoneyTone.SETTLED,
            prefix = if (settledLowercase) settled.lowercase() else settled,
        )
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        netByCurrency.toSortedMap().forEach { (currency, net) ->
            val code = currency.ifBlank { currencyFallback }
            when {
                net < BigDecimal.ZERO ->
                    SeMoneyText(
                        amount = net.abs(),
                        currencyCode = code,
                        tone = SeMoneyTone.YOU_OWE,
                        prefix = stringResource(R.string.member_balance_owes),
                    )
                net > BigDecimal.ZERO ->
                    SeMoneyText(
                        amount = net,
                        currencyCode = code,
                        tone = SeMoneyTone.OWED_TO_YOU,
                        prefix = stringResource(R.string.member_balance_gets_back),
                    )
            }
        }
    }
}

private fun memberNets(
    balance: GroupBalanceUi?,
    userId: String,
): Map<String, BigDecimal> {
    if (balance == null) return emptyMap()
    return balance.memberNetsByCurrency
        .mapNotNull { (currency, nets) ->
            val net = nets[userId] ?: return@mapNotNull null
            if (net.compareTo(BigDecimal.ZERO) == 0) null else currency to net
        }.toMap()
}

@Composable
private fun GroupSettingsHeader(
    group: Group?,
    onEdit: () -> Unit,
    onChangePhoto: () -> Unit,
) {
    val type = group?.groupType ?: GroupType.OTHER
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .clickable(onClick = onChangePhoto),
        ) {
            SeGroupIconTile(
                photoUrl = group?.photoUrl,
                fallbackIcon = type.settingsIcon(),
                fallbackTint = type.settingsTint(),
                size = 64,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group?.name.orEmpty(),
                style = seEntityHeaderStyle(),
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
    titleColor: Color? = null,
    iconTint: Color? = null,
    trailingBadge: String? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    showDivider: Boolean = true,
) {
    val resolvedTitleColor = titleColor ?: SplitEaseColors.Navy
    val resolvedIconTint = iconTint ?: SplitEaseColors.NavyMuted
    val alpha = if (enabled) 1f else 0.55f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = resolvedIconTint.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = resolvedTitleColor.copy(alpha = alpha),
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
            if (showProgress) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SplitEaseColors.Primary,
                    strokeWidth = 2.dp,
                )
            } else if (trailingBadge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SplitEaseColors.Primary)
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
    var showValidation by rememberSaveable(group.id) { mutableStateOf(false) }
    val selected = runCatching { GroupType.valueOf(type) }.getOrDefault(GroupType.OTHER)
    val nameError = showValidation && name.isBlank()

    SeModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.action_edit),
        icon = Icons.Filled.Edit,
        tone = SeConfirmTone.Primary,
        dismissLabel = stringResource(R.string.action_close),
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = {
            showValidation = true
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) onSave(trimmed, selected)
        },
        confirmEnabled = !isSubmitting,
    ) {
        SeTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.label_group_name),
            isError = nameError,
            supportingText =
                if (nameError) stringResource(R.string.msg_group_name_required) else null,
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

