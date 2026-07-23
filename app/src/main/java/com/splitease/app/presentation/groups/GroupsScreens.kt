package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.balances.GroupBalanceHeader
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
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

private enum class GroupDetailPane {
    Expenses,
    Balances,
    Members,
}

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenSpending: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
    expensesViewModel: com.splitease.app.presentation.expenses.ExpensesViewModel = hiltViewModel(),
    balancesViewModel: com.splitease.app.presentation.balances.BalancesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val membersState by remember(groupId) { viewModel.observeMembers(groupId) }
        .collectAsStateWithLifecycle()
    val members = membersState.orEmpty()
    val membersReady = membersState != null
    val expenses by remember(groupId) { expensesViewModel.observeGroupExpenses(groupId) }
        .collectAsStateWithLifecycle()
    val categories by expensesViewModel.categories.collectAsStateWithLifecycle()
    val groupBalance by remember(groupId) { balancesViewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    val group by remember(groupId) { viewModel.observeGroup(groupId) }
        .collectAsStateWithLifecycle()
    var paneName by rememberSaveable { mutableStateOf(GroupDetailPane.Expenses.name) }
    val pane = runCatching { GroupDetailPane.valueOf(paneName) }.getOrDefault(GroupDetailPane.Expenses)
    var settleHint by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val me = expensesViewModel.currentUserId()
    val isSolo = membersReady && members.size <= 1
    val nothingToSettle = stringResource(R.string.group_nothing_to_settle)
    val lifecycleOwner = LocalLifecycleOwner.current
    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshGroupExpenses(groupId)
        }
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

    fun openSettle() {
        val debt =
            groupBalance?.simplifiedDebts?.firstOrNull { d ->
                me != null && (d.fromUserId == me || d.toUserId == me)
            }
        if (debt == null || me == null) {
            settleHint = nothingToSettle
            paneName = GroupDetailPane.Balances.name
            return
        }
        settleHint = null
        val label = if (me == debt.fromUserId) debt.toLabel else debt.fromLabel
        onSettleDebt(
            debt.fromUserId,
            debt.toUserId,
            debt.amount.toPlainString(),
            debt.currencyCode,
            label,
        )
    }

    SeScreen(
        title = group?.name ?: stringResource(R.string.groups_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_group_settings),
                    tint = SplitEaseColors.Navy,
                )
            }
        },
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                icon = Icons.Filled.Receipt,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                GroupDetailHeader(
                    group = group,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeActionChip(
                        label = stringResource(R.string.action_settle_up),
                        onClick = { openSettle() },
                    )
                    SeActionChip(
                        label = stringResource(R.string.group_chip_balances),
                        selected = pane == GroupDetailPane.Balances,
                        onClick = { paneName = GroupDetailPane.Balances.name },
                    )
                    SeActionChip(
                        label = stringResource(R.string.group_chip_totals),
                        onClick = onOpenSpending,
                    )
                    SeActionChip(
                        label = stringResource(R.string.group_chip_members),
                        selected = pane == GroupDetailPane.Members,
                        onClick = { paneName = GroupDetailPane.Members.name },
                    )
                }

                when (pane) {
                    GroupDetailPane.Expenses -> {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            if (isSolo && expenses.isEmpty()) {
                                item {
                                    GroupSoloEmptyState(
                                        onAddMembers = { paneName = GroupDetailPane.Members.name },
                                        onShareLink = {
                                            viewModel.shareGroupLink(
                                                context.getString(
                                                    R.string.group_share_placeholder,
                                                    group?.name.orEmpty(),
                                                ),
                                            )
                                        },
                                    )
                                }
                            } else {
                                item {
                                    GroupBalanceHeader(
                                        groupId = groupId,
                                        balance = groupBalance,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SeSectionHeader(text = stringResource(R.string.expenses_title))
                                }
                                if (expenses.isEmpty()) {
                                    item {
                                        SeEmptyState(message = stringResource(R.string.expenses_empty))
                                    }
                                } else {
                                    items(expenses, key = { it.id }) { expense ->
                                        val categoryLabel =
                                            expense.categoryId
                                                ?.let { categoryNames[it] }
                                                ?.let { " · $it" }
                                                .orEmpty()
                                        SeListRow(
                                            title = expense.description,
                                            subtitle =
                                                "${expense.currencyCode} ${expense.amount.toPlainString()}" +
                                                    " · ${expense.splitType.name.lowercase()}$categoryLabel",
                                        )
                                    }
                                }
                            }
                            uiState.errorMessage?.let { msg ->
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SeErrorText(msg)
                                }
                            }
                            uiState.infoMessage?.let { msg ->
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SeInfoText(msg)
                                }
                            }
                            item { Spacer(modifier = Modifier.height(88.dp)) }
                        }
                    }

                    else -> {
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 88.dp),
                        ) {
                            when (pane) {
                                GroupDetailPane.Balances -> {
                                    SeSectionHeader(text = stringResource(R.string.balances_title))
                                    GroupBalanceHeader(
                                        groupId = groupId,
                                        balance = groupBalance,
                                    )
                                    val myDebts =
                                        groupBalance?.simplifiedDebts?.filter { debt ->
                                            me != null && (debt.fromUserId == me || debt.toUserId == me)
                                        }.orEmpty()
                                    if (myDebts.isEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        SeInfoText(settleHint ?: nothingToSettle)
                                    } else {
                                        myDebts.forEach { debt ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SeOutlinedButton(
                                                text =
                                                    stringResource(
                                                        R.string.balances_debt_line,
                                                        debt.fromLabel,
                                                        debt.toLabel,
                                                        "${debt.currencyCode} ${debt.amount.toPlainString()}",
                                                    ) + " · " + stringResource(R.string.action_settle_up),
                                                onClick = {
                                                    val label =
                                                        if (me == debt.fromUserId) {
                                                            debt.toLabel
                                                        } else {
                                                            debt.fromLabel
                                                        }
                                                    onSettleDebt(
                                                        debt.fromUserId,
                                                        debt.toUserId,
                                                        debt.amount.toPlainString(),
                                                        debt.currencyCode,
                                                        label,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                GroupDetailPane.Members -> {
                                    GroupMembersSection(
                                        groupId = groupId,
                                        members = members,
                                        friends = friends,
                                        uiState = uiState,
                                        viewModel = viewModel,
                                    )
                                }

                                GroupDetailPane.Expenses -> Unit
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
                    }
                }
            }
        },
    )
}

@Composable
private fun GroupDetailHeader(
    group: Group?,
) {
    val type = group?.groupType ?: GroupType.OTHER
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeIconTile(icon = type.icon(), tint = type.tint(), size = 56)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(type.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
    }
}

@Composable
private fun GroupSoloEmptyState(
    onAddMembers: () -> Unit,
    onShareLink: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_solo_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_add_group_members),
            onClick = onAddMembers,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SeOutlinedButton(
            text = stringResource(R.string.action_share_group_link),
            onClick = onShareLink,
        )
    }
}

@Composable
private fun GroupMembersSection(
    groupId: String,
    members: List<GroupMember>,
    friends: List<Friend>,
    uiState: GroupsUiState,
    viewModel: GroupsViewModel,
) {
    var inviteEmail by rememberSaveable { mutableStateOf("") }

    SeSectionHeader(text = stringResource(R.string.label_members))
    members.forEach { member ->
        MemberRow(member = member, friends = friends)
    }

    Spacer(modifier = Modifier.height(16.dp))
    SeSectionHeader(text = stringResource(R.string.invite_by_email))
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
    val addableFriends =
        friends.filter {
            it.friendUserId !in memberIds &&
                !it.displayNameSnapshot.contains("(invited)", ignoreCase = true)
        }
    if (addableFriends.isEmpty()) {
        SeInfoText(stringResource(R.string.no_friends_yet))
    } else {
        addableFriends.forEach { friend ->
            SeTextButton(
                text = "+ ${friend.displayNameSnapshot}",
                onClick = { viewModel.addMember(groupId, friend.friendUserId) },
                enabled = !uiState.isSubmitting,
            )
        }
    }
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
