package com.splitease.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeHeroBalancePair
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInlineLoader
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextButton
import java.math.BigDecimal

/** How the groups list on Home is filtered. */
private enum class GroupsHomeFilter {
    ALL,
    OUTSTANDING,
    YOU_OWE,
    OWED_TO_YOU,
}

@Composable
fun GroupsHomeScreen(
    onOpenGroup: (String) -> Unit,
    onOpenNonGroup: () -> Unit,
    onCreateGroup: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: GroupsHomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var listFilter by remember { mutableStateOf(GroupsHomeFilter.OUTSTANDING) }
    var showSettledGroups by remember { mutableStateOf(false) }
    var photoTargetGroupId by remember { mutableStateOf<String?>(null) }
    val changePhotoCd = stringResource(R.string.cd_change_group_photo)
    val groupPhotoPicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.group_photo_source_title),
            sourceBody = stringResource(R.string.group_photo_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.GroupPhoto,
        ) { uri ->
            val groupId = photoTargetGroupId ?: return@rememberImagePicker
            photoTargetGroupId = null
            viewModel.updateGroupPhoto(groupId, uri)
        }

    if (ui.isLoading) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                SeInlineLoader(text = stringResource(R.string.groups_fetching))
            }
        }
        return
    }

    val balances = ui.balances
    val groupRows =
        remember(ui.allGroups, balances) {
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
        }
    val settled =
        remember(groupRows) { groupRows.filter { it.myNetByCurrency.isEmpty() } }
    val filteredGroups =
        remember(groupRows, listFilter) { groupRows.filter { it.matches(listFilter) } }
    val outstandingWithSettledHidden =
        listFilter == GroupsHomeFilter.OUTSTANDING && filteredGroups.isNotEmpty()
    // If outstanding filter matches nothing, fall back to all groups (same as before).
    val visibleGroups =
        when {
            listFilter == GroupsHomeFilter.OUTSTANDING && filteredGroups.isEmpty() -> groupRows
            outstandingWithSettledHidden && showSettledGroups -> filteredGroups + settled
            else -> filteredGroups
        }
    val hiddenSettledCount =
        if (outstandingWithSettledHidden && !showSettledGroups) settled.size else 0
    val canHideSettled =
        outstandingWithSettledHidden && showSettledGroups && settled.isNotEmpty()
    val nonGroupNet = balances?.nonGroupMyNetByCurrency.orEmpty()
    val showNonGroup =
        balances != null &&
            when (listFilter) {
                GroupsHomeFilter.ALL -> balances.hasNonGroupActivity
                GroupsHomeFilter.OUTSTANDING ->
                    nonGroupNet.matches(GroupsHomeFilter.OUTSTANDING) ||
                        balances.nonGroupDebts.isNotEmpty()
                GroupsHomeFilter.YOU_OWE,
                GroupsHomeFilter.OWED_TO_YOU,
                ->
                    balances.hasNonGroupActivity && nonGroupNet.matches(listFilter)
            }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SePageHeader(
                title = stringResource(R.string.groups_title),
                actions = {
                    SeSoftIconButton(
                        onClick = onOpenSearch,
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.cd_search),
                    )
                    SeSoftIconButton(
                        onClick = onCreateGroup,
                        imageVector = Icons.Filled.GroupAdd,
                        contentDescription = stringResource(R.string.action_create_group),
                    )
                },
            )
        },
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                    item {
                        SeHeroBalancePair(
                            iOwe = balances?.totalIOweByCurrency.orEmpty(),
                            owedToMe = balances?.totalOwedToMeByCurrency.orEmpty(),
                            currencyCode = ui.currencyCode,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                        GroupsFilterMenu(
                            selectedFilter = listFilter,
                            onFilterSelected = {
                                listFilter = it
                                showSettledGroups = false
                            },
                        )
                    }

                    if (ui.allGroups.isEmpty() && !showNonGroup) {
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
                            photoUrl = group?.photoUrl,
                            icon = groupTypeIcon(group?.groupType),
                            iconTint = groupTypeColor(group?.groupType),
                            currencyFallback = ui.currencyCode,
                            onClick = { onOpenGroup(row.groupId) },
                            onIconClick = {
                                photoTargetGroupId = row.groupId
                                groupPhotoPicker.launch()
                            },
                            iconContentDescription = changePhotoCd,
                        )
                    }

                    if (showNonGroup) {
                        item {
                            NonGroupListItem(
                                myNet = balances.nonGroupMyNetByCurrency,
                                debts = balances.nonGroupDebts,
                                currencyFallback = ui.currencyCode,
                                onClick = onOpenNonGroup,
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
                                text =
                                    pluralStringResource(
                                        R.plurals.groups_show_settled,
                                        hiddenSettledCount,
                                        hiddenSettledCount,
                                    ),
                                onClick = { showSettledGroups = true },
                            )
                        }
                    } else if (canHideSettled) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            SeTextButton(
                                text = stringResource(R.string.groups_hide_settled),
                                onClick = { showSettledGroups = false },
                            )
                        }
                    }
                }
            }
    }
}

private val GroupsHomeFilter.labelRes: Int
    get() =
        when (this) {
            GroupsHomeFilter.ALL -> R.string.groups_filter_all
            GroupsHomeFilter.OUTSTANDING -> R.string.groups_filter_outstanding
            GroupsHomeFilter.YOU_OWE -> R.string.groups_filter_you_owe
            GroupsHomeFilter.OWED_TO_YOU -> R.string.groups_filter_owed_to_you
        }

@Composable
private fun GroupsFilterMenu(
    selectedFilter: GroupsHomeFilter,
    onFilterSelected: (GroupsHomeFilter) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_filter_groups),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                GroupsHomeFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(option.labelRes)) },
                        onClick = {
                            onFilterSelected(option)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            RadioButton(
                                selected = selectedFilter == option,
                                onClick = {
                                    onFilterSelected(option)
                                    menuExpanded = false
                                },
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = SplitEaseColors.Primary,
                                    ),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun GroupBalanceUi.matches(filter: GroupsHomeFilter): Boolean =
    myNetByCurrency.matches(filter)

private fun Map<String, BigDecimal>.matches(filter: GroupsHomeFilter): Boolean {
    val nets = values
    return when (filter) {
        GroupsHomeFilter.ALL -> true
        GroupsHomeFilter.OUTSTANDING -> nets.any { it.compareTo(BigDecimal.ZERO) != 0 }
        GroupsHomeFilter.YOU_OWE -> nets.any { it < BigDecimal.ZERO }
        GroupsHomeFilter.OWED_TO_YOU -> nets.any { it > BigDecimal.ZERO }
    }
}

@Composable
private fun GroupBalanceListItem(
    row: GroupBalanceUi,
    photoUrl: String?,
    icon: ImageVector,
    iconTint: Color,
    currencyFallback: String,
    onClick: () -> Unit,
    onIconClick: () -> Unit,
    iconContentDescription: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SeGroupIconTile(
            photoUrl = photoUrl,
            fallbackIcon = icon,
            fallbackTint = iconTint,
            modifier =
                Modifier
                    .semantics { contentDescription = iconContentDescription }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIconClick,
                    ),
        )
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
            SeHeroBalancePair(
                iOwe = mapOf("INR" to BigDecimal("1642.21")),
                owedToMe = emptyMap(),
                currencyCode = "INR",
                modifier = Modifier.padding(16.dp),
            )
            GroupBalanceListItem(
                row =
                    GroupBalanceUi(
                        groupId = "1",
                        groupName = "Home",
                        myNetByCurrency = mapOf("INR" to BigDecimal("-420.00")),
                        memberNetsByCurrency = emptyMap(),
                        simplifiedDebts = emptyList(),
                    ),
                photoUrl = null,
                icon = Icons.Filled.Home,
                iconTint = SplitEaseColors.IconHome,
                currencyFallback = "INR",
                onClick = {},
                onIconClick = {},
                iconContentDescription = "Change group photo",
            )
        }
    }
}
