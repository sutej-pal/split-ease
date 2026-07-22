package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.balances.GroupBalanceHeader
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTypeChip

@Composable
fun GroupsListScreen(
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.groups_title),
        onBack = onBack,
        floatingActionButton = {
            SeFab(
                onClick = onCreateGroup,
                contentDescription = stringResource(R.string.action_create_group),
                icon = Icons.Filled.Add,
            )
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding.values)) {
                uiState.errorMessage?.let {
                    SeErrorText(it, modifier = Modifier.padding(16.dp))
                }
                if (groups.isEmpty()) {
                    SeEmptyState(
                        message = stringResource(R.string.groups_empty),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        actionLabel = stringResource(R.string.action_create_group),
                        onAction = onCreateGroup,
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        items(groups, key = { it.id }) { group ->
                            SeListRow(
                                title = group.name,
                                leading = {
                                    SeIconTile(
                                        icon = group.groupType.icon(),
                                        tint = group.groupType.tint(),
                                        size = 48,
                                    )
                                },
                                onClick = { onOpenGroup(group.id) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var groupType by rememberSaveable { mutableStateOf(GroupType.OTHER.name) }
    val selectedType = runCatching { GroupType.valueOf(groupType) }.getOrDefault(GroupType.OTHER)
    val canDone = name.isNotBlank() && !uiState.isSubmitting

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.create_group_title),
                onClose = onBack,
                centered = true,
                actions = {
                    SeTextButton(
                        text = stringResource(R.string.action_done),
                        onClick = {
                            viewModel.createGroup(
                                name = name,
                                groupType = selectedType,
                                onSuccess = onCreated,
                            )
                        },
                        enabled = canDone,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 1.dp,
                                color = SplitEaseColors.OutlineStrong,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .background(SplitEaseColors.Surface)
                            .clickable { /* photo later */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = stringResource(R.string.cd_group_photo),
                        tint = SplitEaseColors.Primary,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                SeTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.label_group_name),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSubmitting,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            SeSectionHeader(text = stringResource(R.string.label_group_type))
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GroupType.entries.forEach { type ->
                    SeTypeChip(
                        label = stringResource(type.labelRes()),
                        icon = type.icon(),
                        selected = selectedType == type,
                        onClick = { groupType = type.name },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                SeErrorText(it)
            }
        }
    }
}

private fun GroupType.icon() =
    when (this) {
        GroupType.FRIENDS -> Icons.Filled.Group
        GroupType.HOME -> Icons.Filled.Home
        GroupType.OTHER -> Icons.AutoMirrored.Filled.List
    }

private fun GroupType.tint() =
    when (this) {
        GroupType.FRIENDS -> SplitEaseColors.IconFriends
        GroupType.HOME -> SplitEaseColors.IconHome
        GroupType.OTHER -> SplitEaseColors.IconOther
    }

private fun GroupType.labelRes() =
    when (this) {
        GroupType.FRIENDS -> R.string.group_type_friends
        GroupType.HOME -> R.string.group_type_home
        GroupType.OTHER -> R.string.group_type_other
    }

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
    expensesViewModel: com.splitease.app.presentation.expenses.ExpensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val members by viewModel.observeMembers(groupId).collectAsStateWithLifecycle()
    val expenses by remember(groupId) { expensesViewModel.observeGroupExpenses(groupId) }
        .collectAsStateWithLifecycle()
    var group by remember { mutableStateOf<Group?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(groupId) {
        val loaded = viewModel.getGroup(groupId)
        group = loaded
        if (loaded != null) {
            name = loaded.name
        }
        expensesViewModel.refreshGroupExpenses(groupId)
    }

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.invite_email_subject))
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share_invite)))
        viewModel.consumeShareText()
    }

    SeScreen(
        title = group?.name ?: stringResource(R.string.groups_title),
        onBack = onBack,
        actions = {
            SeTextButton(
                text =
                    if (editing) {
                        stringResource(R.string.action_done)
                    } else {
                        stringResource(R.string.action_edit)
                    },
                onClick = { editing = !editing },
            )
        },
        floatingActionButton = {
            SeFab(
                onClick = onAddExpense,
                contentDescription = stringResource(R.string.action_add_expense),
                icon = Icons.Filled.Add,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (editing && group != null) {
                    SeTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.label_group_name),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SePrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            val current = group ?: return@SePrimaryButton
                            viewModel.updateGroup(current.copy(name = name.trim()))
                            editing = false
                            group = current.copy(name = name.trim())
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                SeSectionHeader(text = stringResource(R.string.balances_title))
                GroupBalanceHeader(groupId = groupId)
                Spacer(modifier = Modifier.height(16.dp))

                SeSectionHeader(text = stringResource(R.string.label_members))
                members.forEach { member ->
                    MemberRow(member = member, friends = friends)
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.invite_by_email))
                var inviteEmail by rememberSaveable { mutableStateOf("") }
                SeTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = stringResource(R.string.label_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SePrimaryButton(
                    text = stringResource(R.string.action_send_invite),
                    onClick = {
                        viewModel.inviteMemberByEmail(groupId, inviteEmail)
                        inviteEmail = ""
                    },
                    enabled = !uiState.isSubmitting && inviteEmail.isNotBlank(),
                )

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.add_member_from_friends))
                val memberIds = members.map { it.userId }.toSet()
                friends
                    .filter {
                        it.friendUserId !in memberIds &&
                            !it.displayNameSnapshot.contains("(invited)", ignoreCase = true)
                    }.forEach { friend ->
                        SeTextButton(
                            text = "+ ${friend.displayNameSnapshot}",
                            onClick = { viewModel.addMember(groupId, friend.friendUserId) },
                            enabled = !uiState.isSubmitting,
                        )
                    }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.expenses_title))
                com.splitease.app.presentation.expenses.ExpenseListSection(
                    expenses = expenses,
                    emptyText = stringResource(R.string.expenses_empty),
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
}

@Composable
private fun MemberRow(member: GroupMember, friends: List<Friend>) {
    val label =
        friends.firstOrNull { it.friendUserId == member.userId }?.displayNameSnapshot
            ?: member.userId.take(8)
    SeListRow(
        title = label,
        subtitle = member.role.name,
        leading = { SeIconTile(Icons.Filled.Group, SplitEaseColors.IconOther, size = 40) },
        showDivider = true,
    )
}
