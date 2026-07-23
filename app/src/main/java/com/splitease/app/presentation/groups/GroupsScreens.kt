package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.balances.GroupBalanceHeader
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.expenses.ledgerEntries
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTypeChip
import java.math.BigDecimal

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
                    // width(0) + weight lets the field shrink; OutlinedTextField's
                    // intrinsic min width otherwise overflows the row.
                    modifier = Modifier.weight(1f).width(0.dp),
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
}

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (expenseId: String) -> Unit,
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
    val membersState by remember(groupId) { viewModel.observeMembers(groupId) }
        .collectAsStateWithLifecycle()
    val members = membersState.orEmpty()
    val membersReady = membersState != null
    val ledger by remember(groupId) { expensesViewModel.observeGroupLedger(groupId) }
        .collectAsStateWithLifecycle()
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

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Flush local writes + pull remote so other members' expense/payment edits show up.
            expensesViewModel.refreshGroupFromCloud(groupId)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                icon = Icons.Filled.Receipt,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
        ) {
            GroupDetailBanner(
                group = group,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
            )

            if (!(isSolo && ledger.isEmpty())) {
                GroupOverallBalanceBlock(
                    balance = groupBalance,
                    currencyFallback = group?.defaultCurrencyCode.orEmpty(),
                )
            }

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
                        if (isSolo && ledger.isEmpty()) {
                            item {
                                GroupSoloEmptyState(
                                    onAddMembers = onOpenSettings,
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
                        } else if (ledger.isEmpty()) {
                            item {
                                SeEmptyState(message = stringResource(R.string.ledger_empty))
                            }
                        } else {
                            ledgerEntries(ledger, onExpenseClick = onOpenExpense)
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

                GroupDetailPane.Balances -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 88.dp),
                    ) {
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
    }
}

@Composable
private fun GroupDetailBanner(
    group: Group?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val type = group?.groupType ?: GroupType.OTHER
    val bannerColor = lerp(type.tint(), SplitEaseColors.Navy, 0.28f)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bannerColor)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerCircleIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
            BannerCircleIconButton(
                onClick = onOpenSettings,
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_group_settings),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = group?.name ?: stringResource(R.string.groups_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BannerCircleIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = SplitEaseColors.Navy,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun GroupOverallBalanceBlock(
    balance: GroupBalanceUi?,
    currencyFallback: String,
) {
    if (balance == null) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val myDebts =
        balance.simplifiedDebts.filter { debt ->
            debt.fromLabel == "You" || debt.toLabel == "You"
        }
    val nets = balance.myNetByCurrency
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SplitEaseColors.Surface)
                .clickable(enabled = myDebts.isNotEmpty()) { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GroupOverallHeadline(
                    nets = nets,
                    currencyFallback = currencyFallback.ifBlank { "INR" },
                )
            }
            if (myDebts.isNotEmpty()) {
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        }
        AnimatedVisibility(visible = expanded && myDebts.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                myDebts.forEach { debt ->
                    GroupOverallDebtLine(debt)
                }
            }
        }
    }
}

@Composable
private fun GroupOverallHeadline(
    nets: Map<String, BigDecimal>,
    currencyFallback: String,
) {
    when {
        nets.isEmpty() || nets.values.all { it.compareTo(BigDecimal.ZERO) == 0 } -> {
            Text(
                text = stringResource(R.string.balances_settled_overall),
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Settled,
                fontWeight = FontWeight.SemiBold,
            )
        }
        else -> {
            val (currency, net) =
                nets.entries.firstOrNull { it.value.compareTo(BigDecimal.ZERO) != 0 }
                    ?: return
            val code = currency.ifBlank { currencyFallback }
            val money = MoneyFormat.format(net.abs(), code)
            val youOwe = net < BigDecimal.ZERO
            val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
            val template =
                if (youOwe) {
                    stringResource(R.string.balances_you_owe_overall, money)
                } else {
                    stringResource(R.string.balances_you_are_owed_overall, money)
                }
            // Highlight the money substring inside the localized sentence.
            Text(
                text =
                    buildAnnotatedString {
                        val start = template.indexOf(money)
                        if (start < 0) {
                            append(template)
                        } else {
                            append(template.substring(0, start))
                            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                                append(money)
                            }
                            append(template.substring(start + money.length))
                        }
                    },
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
            )
        }
    }
}

@Composable
private fun GroupOverallDebtLine(debt: LabeledDebt) {
    val youOwe = debt.fromLabel == "You"
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .background(
                        if (youOwe) SplitEaseColors.YouOwe.copy(alpha = 0.35f)
                        else SplitEaseColors.OwedToYou.copy(alpha = 0.35f),
                        RoundedCornerShape(2.dp),
                    ),
        )
        SeMoneyText(
            amount = debt.amount,
            currencyCode = debt.currencyCode,
            tone = if (youOwe) SeMoneyTone.YOU_OWE else SeMoneyTone.OWED_TO_YOU,
            prefix =
                if (youOwe) {
                    stringResource(R.string.balances_you_owe_person, debt.toLabel)
                } else {
                    stringResource(R.string.balances_person_owes_you, debt.fromLabel)
                },
        )
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
