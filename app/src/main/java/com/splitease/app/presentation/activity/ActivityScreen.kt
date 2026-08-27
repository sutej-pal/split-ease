package com.splitease.app.presentation.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeActionChipRow
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeIconTileWithAvatar
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.seDetailHorizontal

private enum class ActivityListFilter {
    ALL,
    EXPENSE,
    SETTLEMENTS,
    GROUPS,
}

@Composable
fun ActivityScreen(
    onOpenExpense: (expenseId: String) -> Unit = {},
    onAddExpense: () -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var listFilter by rememberSaveable { mutableStateOf(ActivityListFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val showSearch = searchVisible || query.isNotBlank()
    val visibleItems =
        remember(items, listFilter, query) {
            items.filter { it.matches(listFilter) && it.matchesQuery(query) }
        }
    val emptyMessage =
        if (items.isEmpty() && listFilter == ActivityListFilter.ALL && query.isBlank()) {
            stringResource(R.string.activity_empty)
        } else {
            stringResource(R.string.activity_empty_filtered)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SePageHeader(
                title = stringResource(R.string.nav_activity),
                actions = {
                    SeSoftIconButton(
                        onClick = {
                            if (showSearch) {
                                searchVisible = false
                                query = ""
                            } else {
                                searchVisible = true
                            }
                        },
                        imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.cd_search),
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                SeActionChipRow {
                    ActivityListFilter.entries.forEach { option ->
                        SeActionChip(
                            label = stringResource(option.chipLabelRes),
                            selected = listFilter == option,
                            onClick = { listFilter = option },
                        )
                    }
                }
                if (showSearch) {
                    SeTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.activity_search_hint),
                        modifier =
                            Modifier
                                .seDetailHorizontal()
                                .padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                if (visibleItems.isEmpty()) {
                    SeEmptyState(
                        message = emptyMessage,
                        icon = Icons.Filled.Receipt,
                        modifier = Modifier.seDetailHorizontal(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    ) {
                        items(visibleItems, key = { it.id }) { item ->
                            ActivityRow(
                                item = item,
                                onClick =
                                    item.relatedExpenseId?.let { expenseId ->
                                        { onOpenExpense(expenseId) }
                                    },
                            )
                        }
                    }
                }
            }
    }
}

private val ActivityListFilter.chipLabelRes: Int
    get() =
        when (this) {
            ActivityListFilter.ALL -> R.string.filter_all
            ActivityListFilter.EXPENSE -> R.string.activity_filter_chip_expense
            ActivityListFilter.SETTLEMENTS -> R.string.activity_filter_chip_settlements
            ActivityListFilter.GROUPS -> R.string.activity_filter_chip_groups
        }

private fun ActivityUiItem.matches(filter: ActivityListFilter): Boolean =
    when (filter) {
        ActivityListFilter.ALL -> true
        ActivityListFilter.EXPENSE ->
            kind == ActivityKind.EXPENSE ||
                kind == ActivityKind.EXPENSE_UPDATED ||
                kind == ActivityKind.EXPENSE_DELETED
        ActivityListFilter.SETTLEMENTS -> kind == ActivityKind.PAYMENT
        ActivityListFilter.GROUPS -> kind == ActivityKind.GROUP_CREATED
    }

private fun ActivityUiItem.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        subtitle.contains(needle, ignoreCase = true) ||
        amountLabel.contains(needle, ignoreCase = true) ||
        (balanceLabel?.contains(needle, ignoreCase = true) == true) ||
        (expenseTitle?.contains(needle, ignoreCase = true) == true) ||
        (actorDisplayName?.contains(needle, ignoreCase = true) == true)
}

@Composable
private fun ActivityRow(
    item: ActivityUiItem,
    onClick: (() -> Unit)? = null,
) {
    val (icon, tint) =
        when (item.kind) {
            ActivityKind.EXPENSE -> Icons.Filled.Receipt to SplitEaseColors.Primary
            ActivityKind.EXPENSE_UPDATED -> Icons.Filled.Edit to SplitEaseColors.Primary
            ActivityKind.EXPENSE_DELETED -> Icons.Filled.Delete to SplitEaseColors.YouOwe
            ActivityKind.PAYMENT -> Icons.Filled.Payments to SplitEaseColors.OwedToYou
            ActivityKind.GROUP_CREATED -> Icons.Filled.Group to SplitEaseColors.IconFriends
        }
    val isExpenseKind =
        item.kind == ActivityKind.EXPENSE ||
            item.kind == ActivityKind.EXPENSE_UPDATED ||
            item.kind == ActivityKind.EXPENSE_DELETED
    val railColor =
        when (item.kind) {
            ActivityKind.PAYMENT -> SplitEaseColors.OwedToYou
            ActivityKind.EXPENSE_DELETED -> SplitEaseColors.YouOwe
            ActivityKind.GROUP_CREATED -> SplitEaseColors.IconFriends
            else -> SplitEaseColors.Primary
        }
    val amountTone =
        when (item.balanceTone) {
            ActivityBalanceTone.POSITIVE -> SplitEaseColors.OwedToYou
            ActivityBalanceTone.NEGATIVE -> SplitEaseColors.YouOwe
            null -> SplitEaseColors.Navy
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = SeLayout.detailHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(railColor),
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (isExpenseKind && !item.actorDisplayName.isNullOrBlank()) {
            SeIconTileWithAvatar(
                icon = icon,
                tint = tint,
                actorName = item.actorDisplayName,
                actorPhotoUrl = item.actorPhotoUrl,
            )
        } else {
            SeIconTile(icon = icon, tint = tint, size = 44)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activityTitleText(item.title, item.expenseTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!item.balanceLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.balanceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = amountTone,
                )
            }
            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.amountLabel.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.amountLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountTone,
            )
        }
    }
}

private fun activityTitleText(
    title: String,
    expenseTitle: String?,
) = buildAnnotatedString {
    if (expenseTitle.isNullOrBlank()) {
        append(title)
        return@buildAnnotatedString
    }
    val start = title.indexOf(expenseTitle)
    if (start < 0) {
        append(title)
        return@buildAnnotatedString
    }
    append(title.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(expenseTitle)
    }
    append(title.substring(start + expenseTitle.length))
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ActivityScreenPreview() {
    SePreview {
        Column {
            ActivityRow(
                ActivityUiItem(
                    id = "1",
                    kind = ActivityKind.EXPENSE,
                    title = "Sutej Pal Hotmail added exp3 in Noida room",
                    subtitle = "Aug 5, 2026, 6:12 PM",
                    amountLabel = "",
                    sortEpochMs = 0L,
                    balanceLabel = "You get back ₹100.00",
                    balanceTone = ActivityBalanceTone.POSITIVE,
                    actorDisplayName = "Sutej Pal Hotmail",
                    expenseTitle = "exp3",
                ),
            )
            ActivityRow(
                ActivityUiItem(
                    id = "2",
                    kind = ActivityKind.EXPENSE,
                    title = "You added exp2 in Noida room",
                    subtitle = "Aug 5, 2026, 5:40 PM",
                    amountLabel = "",
                    sortEpochMs = 0L,
                    balanceLabel = "you owe ₹250.00",
                    balanceTone = ActivityBalanceTone.NEGATIVE,
                    actorDisplayName = "You",
                    expenseTitle = "exp2",
                ),
            )
            ActivityRow(
                ActivityUiItem(
                    id = "3",
                    kind = ActivityKind.GROUP_CREATED,
                    title = "You created \"Trip\"",
                    subtitle = "Jul 22, 2026, 3:15 PM",
                    amountLabel = "",
                    sortEpochMs = 0L,
                ),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 240)
@Composable
private fun ActivityEmptyPreview() {
    SePreview {
        SeEmptyState(
            message = "No activity yet. Create a group, add an expense, or record a payment to see it here.",
            modifier = Modifier.padding(SeLayout.detailHorizontal),
        )
    }
}
