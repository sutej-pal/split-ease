package com.splitease.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeActionChipRow
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeHeroBalancePair
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInlineLoader
import com.splitease.app.presentation.ui.SeLedgerRow
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.seDetailHorizontal
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
            if (!ui.isLoading) {
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
            }
        },
        floatingActionButton = {
            if (!ui.isLoading) {
                SeExtendedFab(
                    text = stringResource(R.string.action_add_expense),
                    onClick = onAddExpense,
                    icon = Icons.Filled.Receipt,
                )
            }
        },
    ) { padding ->
        if (ui.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                SeInlineLoader(text = stringResource(R.string.groups_fetching))
            }
        } else {
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
                    // No horizontal contentPadding — row click/ripple is full-bleed; content
                    // keeps [SeLayout.detailHorizontal] inset inside each item.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item {
                        SeHeroBalancePair(
                            iOwe = balances?.totalIOweByCurrency.orEmpty(),
                            owedToMe = balances?.totalOwedToMeByCurrency.orEmpty(),
                            currencyCode = ui.currencyCode,
                            modifier =
                                Modifier
                                    .seDetailHorizontal()
                                    .padding(top = 4.dp, bottom = 4.dp),
                        )
                        SeActionChipRow {
                            GroupsHomeFilter.entries.forEach { option ->
                                SeActionChip(
                                    label = stringResource(option.chipLabelRes),
                                    selected = listFilter == option,
                                    onClick = {
                                        listFilter = option
                                        showSettledGroups = false
                                    },
                                )
                            }
                        }
                    }

                    if (ui.allGroups.isEmpty() && !showNonGroup) {
                        item {
                            SeEmptyState(
                                message = stringResource(R.string.groups_empty_home),
                                icon = Icons.Filled.Group,
                                actionLabel = stringResource(R.string.action_create_group),
                                onAction = onCreateGroup,
                                modifier = Modifier.seDetailHorizontal(),
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
                            Column(modifier = Modifier.seDetailHorizontal()) {
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
                        }
                    } else if (canHideSettled) {
                        item {
                            Column(modifier = Modifier.seDetailHorizontal()) {
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
    }
}

private val GroupsHomeFilter.chipLabelRes: Int
    get() =
        when (this) {
            GroupsHomeFilter.ALL -> R.string.filter_all
            GroupsHomeFilter.OUTSTANDING -> R.string.filter_outstanding
            GroupsHomeFilter.YOU_OWE -> R.string.filter_you_owe
            GroupsHomeFilter.OWED_TO_YOU -> R.string.filter_owed_to_you
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
    val net = row.myNetByCurrency.primaryLedger(currencyFallback)
    val debtSubtitle =
        row.simplifiedDebts
            .filter { it.fromLabel == "You" || it.toLabel == "You" }
            .firstOrNull()
            ?.let { debt ->
                val youOwe = debt.fromLabel == "You"
                if (youOwe) {
                    "You owe ${debt.toLabel}"
                } else {
                    "${debt.fromLabel} owes you"
                }
            }
    SeLedgerRow(
        title = row.groupName,
        subtitle = debtSubtitle ?: net.caption,
        amount = net.amountLabel,
        amountTone = net.tone,
        onClick = onClick,
        leading = {
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
        },
    )
}

@Composable
private fun NonGroupListItem(
    myNet: Map<String, BigDecimal>,
    debts: List<LabeledDebt>,
    currencyFallback: String,
    onClick: () -> Unit,
) {
    val net = myNet.primaryLedger(currencyFallback)
    val debtSubtitle =
        debts.firstOrNull()?.let { debt ->
            val youOwe = debt.fromLabel == "You"
            if (youOwe) "You owe ${debt.toLabel}" else "${debt.fromLabel} owes you"
        }
    SeLedgerRow(
        title = stringResource(R.string.non_group_expenses),
        subtitle = debtSubtitle ?: net.caption,
        amount = net.amountLabel,
        amountTone = net.tone,
        onClick = onClick,
        leading = {
            SeIconTile(icon = Icons.AutoMirrored.Filled.List, tint = SplitEaseColors.IconOther)
        },
    )
}

private data class LedgerNet(
    val amountLabel: String?,
    val tone: SeMoneyTone,
    val caption: String,
)

private fun Map<String, BigDecimal>.primaryLedger(fallback: String): LedgerNet {
    val settledCaption = "settled up"
    val entry =
        entries.firstOrNull { it.value.compareTo(BigDecimal.ZERO) != 0 }
            ?: return LedgerNet(amountLabel = null, tone = SeMoneyTone.SETTLED, caption = settledCaption)
    val code = entry.key.ifBlank { fallback }
    return if (entry.value < BigDecimal.ZERO) {
        LedgerNet(
            amountLabel = MoneyFormat.format(entry.value.abs(), code),
            tone = SeMoneyTone.YOU_OWE,
            caption = "you owe",
        )
    } else {
        LedgerNet(
            amountLabel = MoneyFormat.format(entry.value, code),
            tone = SeMoneyTone.OWED_TO_YOU,
            caption = "you are owed",
        )
    }
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
