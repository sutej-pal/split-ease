package com.splitease.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SeOverallSummary
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeTopBar
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsHomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onAddExpenseForGroup: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: GroupsHomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showSettled by remember { mutableStateOf(false) }
    var showExpensePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val balances = ui.balances
    val groupRows =
        ui.allGroups.map { group ->
            balances?.groupBalances?.firstOrNull { it.groupId == group.id }
                ?: GroupBalanceUi(
                    groupId = group.id,
                    groupName = group.name,
                    myNetByCurrency = emptyMap(),
                    memberNetsByCurrency = emptyMap(),
                    simplifiedDebts = emptyList(),
                )
        }
    val unsettled = groupRows.filter { it.myNetByCurrency.isNotEmpty() }
    val settled = groupRows.filter { it.myNetByCurrency.isEmpty() }
    val visibleGroups = if (showSettled) groupRows else unsettled.ifEmpty { groupRows }
    val hiddenSettledCount = if (showSettled) 0 else settled.size
    val hasNonGroup =
        balances != null &&
            (balances.nonGroupMyNetByCurrency.isNotEmpty() || balances.nonGroupDebts.isNotEmpty())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = "",
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                    IconButton(onClick = onCreateGroup) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = stringResource(R.string.action_create_group))
                    }
                },
            )
        },
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = {
                    val groups = ui.allGroups
                    when {
                        groups.size == 1 -> onAddExpenseForGroup(groups.first().id)
                        else -> showExpensePicker = true
                    }
                },
                icon = Icons.Filled.Receipt,
            )
        },
    ) { padding ->
        SePullRefreshBox(
            isRefreshing = ui.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            ) {
                item {
                    OverallSummaryRow(
                        iOwe = balances?.totalIOweByCurrency.orEmpty(),
                        owedToMe = balances?.totalOwedToMeByCurrency.orEmpty(),
                        currencyCode = ui.currencyCode,
                        onFilterClick = onOpenSettings,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (ui.allGroups.isEmpty() && !hasNonGroup) {
                    item {
                        SeEmptyState(
                            message = stringResource(R.string.groups_empty_home),
                            actionLabel = stringResource(R.string.action_create_group),
                            onAction = onCreateGroup,
                        )
                    }
                }

                items(visibleGroups, key = { it.groupId }) { row ->
                    val group = ui.allGroups.firstOrNull { it.id == row.groupId }
                    GroupBalanceListItem(
                        row = row,
                        icon = groupTypeIcon(group?.groupType),
                        iconTint = groupTypeColor(group?.groupType),
                        currencyFallback = ui.currencyCode,
                        onClick = { onOpenGroup(row.groupId) },
                    )
                }

                if (hasNonGroup && balances != null) {
                    item {
                        NonGroupListItem(
                            myNet = balances.nonGroupMyNetByCurrency,
                            debts = balances.nonGroupDebts,
                            currencyFallback = ui.currencyCode,
                        )
                    }
                }

                if (hiddenSettledCount > 0) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.groups_hiding_settled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SeOutlinedButton(
                            text = stringResource(R.string.groups_show_settled, hiddenSettledCount),
                            onClick = { showSettled = true },
                        )
                    }
                }
            }
        }
    }

    if (showExpensePicker) {
        ModalBottomSheet(
            onDismissRequest = { showExpensePicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = stringResource(R.string.pick_group_for_expense),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (ui.allGroups.isEmpty()) {
                Text(
                    text = stringResource(R.string.groups_empty_home),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SeOutlinedButton(
                    text = stringResource(R.string.action_create_group),
                    onClick = {
                        showExpensePicker = false
                        onCreateGroup()
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                ui.allGroups.forEach { group: Group ->
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExpensePicker = false
                                    onAddExpenseForGroup(group.id)
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverallSummaryRow(
    iOwe: Map<String, BigDecimal>,
    owedToMe: Map<String, BigDecimal>,
    currencyCode: String,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val summaryModifier = Modifier.weight(1f)
        when {
            iOwe.isNotEmpty() -> {
                val (code, amount) = iOwe.entries.first()
                SeOverallSummary(
                    prefix = stringResource(R.string.overall_you_owe_prefix),
                    amount = amount,
                    currencyCode = code.ifBlank { currencyCode },
                    tone = SeMoneyTone.YOU_OWE,
                    modifier = summaryModifier,
                )
            }
            owedToMe.isNotEmpty() -> {
                val (code, amount) = owedToMe.entries.first()
                SeOverallSummary(
                    prefix = stringResource(R.string.overall_owed_to_you_prefix),
                    amount = amount,
                    currencyCode = code.ifBlank { currencyCode },
                    tone = SeMoneyTone.OWED_TO_YOU,
                    modifier = summaryModifier,
                )
            }
            else -> {
                SeOverallSummary(
                    prefix = stringResource(R.string.overall_settled),
                    amount = null,
                    currencyCode = currencyCode,
                    tone = SeMoneyTone.SETTLED,
                    modifier = summaryModifier,
                )
            }
        }
        IconButton(onClick = onFilterClick) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupBalanceListItem(
    row: GroupBalanceUi,
    icon: ImageVector,
    iconTint: Color,
    currencyFallback: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SeIconTile(icon = icon, tint = iconTint)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            MyNetStatus(row.myNetByCurrency, currencyFallback)
            row.simplifiedDebts
                .filter { it.fromLabel == "You" || it.toLabel == "You" }
                .take(3)
                .forEach { debt ->
                    DebtLine(debt)
                }
        }
    }
}

@Composable
private fun NonGroupListItem(
    myNet: Map<String, BigDecimal>,
    debts: List<LabeledDebt>,
    currencyFallback: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SeIconTile(icon = Icons.AutoMirrored.Filled.List, tint = SplitEaseColors.IconOther)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.non_group_expenses),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            MyNetStatus(myNet, currencyFallback)
            debts.take(3).forEach { DebtLine(it) }
        }
    }
}

@Composable
private fun MyNetStatus(
    myNet: Map<String, BigDecimal>,
    currencyFallback: String,
) {
    if (myNet.isEmpty()) {
        SeMoneyText(
            amount = BigDecimal.ZERO,
            currencyCode = currencyFallback,
            tone = SeMoneyTone.SETTLED,
            prefix = stringResource(R.string.balances_settled_up).lowercase(),
        )
        return
    }
    myNet.toSortedMap().forEach { (currency, net) ->
        val code = currency.ifBlank { currencyFallback }
        when {
            net < BigDecimal.ZERO ->
                SeMoneyText(net.abs(), code, SeMoneyTone.YOU_OWE, prefix = "you owe")
            net > BigDecimal.ZERO ->
                SeMoneyText(net, code, SeMoneyTone.OWED_TO_YOU, prefix = "you are owed")
        }
    }
}

@Composable
private fun DebtLine(debt: LabeledDebt) {
    val youOwe = debt.fromLabel == "You"
    SeMoneyText(
        amount = debt.amount,
        currencyCode = debt.currencyCode,
        tone = if (youOwe) SeMoneyTone.YOU_OWE else SeMoneyTone.OWED_TO_YOU,
        prefix = if (youOwe) "You owe ${debt.toLabel}" else "${debt.fromLabel} owes you",
    )
}

private fun groupTypeIcon(type: GroupType?): ImageVector =
    when (type) {
        GroupType.FRIENDS -> Icons.Filled.Group
        GroupType.HOME -> Icons.Filled.Home
        GroupType.OTHER, null -> Icons.AutoMirrored.Filled.List
    }

private fun groupTypeColor(type: GroupType?): Color =
    when (type) {
        GroupType.FRIENDS -> SplitEaseColors.IconFriends
        GroupType.HOME -> SplitEaseColors.IconHome
        GroupType.OTHER, null -> SplitEaseColors.IconOther
    }

@Preview(name = "Groups home", showBackground = true, heightDp = 640)
@Composable
private fun GroupsHomeScreenPreview() {
    SePreview {
        Column {
            SeOverallSummary(
                prefix = "Overall, you owe",
                amount = BigDecimal("1642.21"),
                currencyCode = "INR",
                tone = SeMoneyTone.YOU_OWE,
            )
            Spacer(modifier = Modifier.height(16.dp))
            GroupBalanceListItem(
                row =
                    GroupBalanceUi(
                        groupId = "1",
                        groupName = "Home",
                        myNetByCurrency = mapOf("INR" to BigDecimal("-420.00")),
                        memberNetsByCurrency = emptyMap(),
                        simplifiedDebts = emptyList(),
                    ),
                icon = Icons.Filled.Home,
                iconTint = SplitEaseColors.IconHome,
                currencyFallback = "INR",
                onClick = {},
            )
        }
    }
}
