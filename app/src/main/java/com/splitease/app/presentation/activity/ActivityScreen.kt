package com.splitease.app.presentation.activity

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ActivityScreen(
    onOpenExpense: (expenseId: String) -> Unit = {},
    onAddExpense: () -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val showSearch = searchVisible || query.isNotBlank()
    val listState = rememberLazyListState()

    val emptyMessage =
        if (!feed.hasAnyItems && !feed.isFiltered) {
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
                                viewModel.setSearchQuery("")
                            } else {
                                searchVisible = true
                            }
                        },
                        imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.cd_search),
                    )
                    ActivityFilterButton(
                        selectedFilter = listFilter,
                        onFilterSelected = { viewModel.setListFilter(it) },
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
            if (showSearch) {
                SeTextField(
                    value = query,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = stringResource(R.string.activity_search_hint),
                    modifier =
                        Modifier
                            .seDetailHorizontal()
                            .padding(top = 4.dp, bottom = 4.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (feed.entries.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        SeEmptyState(
                            message = emptyMessage,
                            icon = Icons.Filled.Receipt,
                            modifier = Modifier.seDetailHorizontal(),
                        )
                    }
                } else {
                    items(
                        items = feed.entries,
                        key = { it.stableKey() },
                        contentType = { entry ->
                            when (entry) {
                                is ActivityListEntry.DayHeader -> "header"
                                is ActivityListEntry.Row -> "row"
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is ActivityListEntry.DayHeader ->
                                ActivityDayHeader(day = entry.day)
                            is ActivityListEntry.Row ->
                                ActivityRow(
                                    item = entry.item,
                                    onClick =
                                        entry.item.relatedExpenseId?.let { expenseId ->
                                            { onOpenExpense(expenseId) }
                                        },
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFilterButton(
    selectedFilter: ActivityListFilter,
    onFilterSelected: (ActivityListFilter) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        SeSoftIconButton(
            onClick = { menuExpanded = true },
            imageVector = Icons.Filled.Tune,
            contentDescription = stringResource(R.string.cd_filter_activity),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            ActivityListFilter.entries.forEach { option ->
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

private val ActivityListFilter.labelRes: Int
    get() =
        when (this) {
            ActivityListFilter.ALL -> R.string.activity_filter_all
            ActivityListFilter.EXPENSE -> R.string.activity_filter_expense
            ActivityListFilter.SETTLEMENTS -> R.string.activity_filter_settlements
            ActivityListFilter.GROUPS -> R.string.activity_filter_groups
        }

@Composable
private fun ActivityDayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val formattedDay =
        remember(day) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)
        }
    val label =
        when (day) {
            today -> stringResource(R.string.activity_section_today)
            today.minusDays(1) -> stringResource(R.string.activity_section_yesterday)
            else -> formattedDay
        }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SeLayout.detailHorizontal)
                .padding(top = 16.dp, bottom = 4.dp),
    )
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
    val showsBalanceSlot =
        item.kind == ActivityKind.EXPENSE ||
            item.kind == ActivityKind.EXPENSE_UPDATED ||
            item.kind == ActivityKind.EXPENSE_DELETED ||
            item.kind == ActivityKind.PAYMENT
    val amountTone =
        when (item.balanceTone) {
            ActivityBalanceTone.POSITIVE -> SplitEaseColors.OwedToYou
            ActivityBalanceTone.NEGATIVE -> SplitEaseColors.YouOwe
            null -> SplitEaseColors.Navy
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = SeLayout.detailHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SeIconTile(icon = icon, tint = tint, size = 44)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                ActivityRowTitle(item = item)
                if (showsBalanceSlot) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.balanceLabel.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (item.balanceLabel.isNullOrBlank()) {
                                Color.Transparent
                            } else {
                                amountTone
                            },
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = SeLayout.detailHorizontal + 58.dp),
            color = SplitEaseColors.Outline,
        )
    }
}

@Composable
private fun ActivityRowTitle(item: ActivityUiItem) {
    val expenseTitle = item.expenseTitle
    if (expenseTitle.isNullOrBlank()) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val titleText =
            remember(item.title, expenseTitle) {
                activityTitleText(item.title, expenseTitle)
            }
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun activityTitleText(
    title: String,
    expenseTitle: String,
) = buildAnnotatedString {
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
            ActivityDayHeader(day = LocalDate.now())
            ActivityRow(
                ActivityUiItem(
                    id = "1",
                    kind = ActivityKind.EXPENSE,
                    title = "Sutej Pal Hotmail added exp3 in Noida room",
                    subtitle = "Aug 5, 2026, 6:12 PM",
                    amountLabel = "",
                    sortEpochMs = System.currentTimeMillis(),
                    timeLabel = "6:12 PM",
                    balanceLabel = "You get back ₹100.00",
                    balanceTone = ActivityBalanceTone.POSITIVE,
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
                    sortEpochMs = System.currentTimeMillis() - 32 * 60_000L,
                    timeLabel = "5:40 PM",
                    balanceLabel = "you owe ₹250.00",
                    balanceTone = ActivityBalanceTone.NEGATIVE,
                    expenseTitle = "exp2",
                ),
            )
            ActivityDayHeader(day = LocalDate.now().minusDays(1))
            ActivityRow(
                ActivityUiItem(
                    id = "3",
                    kind = ActivityKind.GROUP_CREATED,
                    title = "You created \"Trip\"",
                    subtitle = "Jul 22, 2026, 3:15 PM",
                    amountLabel = "",
                    sortEpochMs = System.currentTimeMillis() - 86_400_000L,
                    timeLabel = "3:15 PM",
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
