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
import com.splitease.app.presentation.ui.SeIconTileWithAvatar
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    val groupedItems =
        remember(visibleItems) {
            visibleItems.groupBy { dayKey(it.sortEpochMs) }
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
                    ActivityFilterButton(
                        selectedFilter = listFilter,
                        onFilterSelected = { listFilter = it },
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
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.activity_search_hint),
                    modifier =
                        Modifier
                            .seDetailHorizontal()
                            .padding(top = 4.dp, bottom = 4.dp),
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    groupedItems.forEach { (day, dayItems) ->
                        item(key = "day-$day") {
                            ActivityDayHeader(day = day)
                        }
                        items(dayItems, key = { it.id }) { item ->
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

private fun dayKey(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun ActivityDayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label =
        when (day) {
            today -> stringResource(R.string.activity_section_today)
            today.minusDays(1) -> stringResource(R.string.activity_section_yesterday)
            else ->
                remember(day) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)
                }
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
    val isExpenseKind =
        item.kind == ActivityKind.EXPENSE ||
            item.kind == ActivityKind.EXPENSE_UPDATED ||
            item.kind == ActivityKind.EXPENSE_DELETED
    val amountTone =
        when (item.balanceTone) {
            ActivityBalanceTone.POSITIVE -> SplitEaseColors.OwedToYou
            ActivityBalanceTone.NEGATIVE -> SplitEaseColors.YouOwe
            null -> SplitEaseColors.Navy
        }
    val timeLabel =
        remember(item.sortEpochMs) {
            DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .format(
                    Instant.ofEpochMilli(item.sortEpochMs).atZone(ZoneId.systemDefault()),
                )
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
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.balanceLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.balanceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = amountTone,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeLabel,
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
            ActivityDayHeader(day = LocalDate.now())
            ActivityRow(
                ActivityUiItem(
                    id = "1",
                    kind = ActivityKind.EXPENSE,
                    title = "Sutej Pal Hotmail added exp3 in Noida room",
                    subtitle = "Aug 5, 2026, 6:12 PM",
                    amountLabel = "",
                    sortEpochMs = System.currentTimeMillis(),
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
                    sortEpochMs = System.currentTimeMillis() - 32 * 60_000L,
                    balanceLabel = "you owe ₹250.00",
                    balanceTone = ActivityBalanceTone.NEGATIVE,
                    actorDisplayName = "You",
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
