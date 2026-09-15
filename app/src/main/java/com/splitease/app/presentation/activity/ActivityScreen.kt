package com.splitease.app.presentation.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.sync.SyncState
import com.splitease.app.presentation.navigation.bottomBarContentWindowInsets
import com.splitease.app.presentation.navigation.bottomBarScrollPadding
import com.splitease.app.presentation.navigation.paddingAboveBottomBar
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeLineSkeleton
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeShimmerProvider
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.seDetailHorizontal
import kotlinx.coroutines.launch
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isScrollingUp = listState.isScrollingUp()
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollBackward || isScrollingUp
        }
    }
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4
        }
    }

    LaunchedEffect(listFilter, query) {
        if (listState.firstVisibleItemIndex > 0) {
            ActivityPerfLog.scroll("filter-query-change", "animating scroll to top")
            listState.animateScrollToItem(0)
        }
    }

    DisposableEffect(listState) {
        ActivityPerfLog.scroll("list-state", "initialized")
        onDispose {}
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    viewModel.markFeedSeen()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.markFeedSeen()
        }
    }

    val emptyMessage =
        if (!feed.hasAnyItems && !feed.isFiltered) {
            stringResource(R.string.activity_empty)
        } else {
            stringResource(R.string.activity_empty_filtered)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = bottomBarContentWindowInsets(),
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.paddingAboveBottomBar(),
            ) {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    SeFab(
                        onClick = {
                            coroutineScope.launch {
                                ActivityPerfLog.scroll("scroll-to-top", "animating scroll to top")
                                listState.animateScrollToItem(0)
                            }
                        },
                        contentDescription = stringResource(R.string.cd_scroll_to_top),
                        icon = Icons.Filled.KeyboardArrowUp,
                    )
                }
                SeExtendedFab(
                    text = stringResource(R.string.action_add_expense),
                    onClick = onAddExpense,
                    icon = Icons.Filled.Receipt,
                    expanded = isFabExpanded,
                )
            }
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        SePullRefreshBox(
            isRefreshing = feed.syncState == SyncState.IN_PROGRESS,
            onRefresh = viewModel::refreshFeed,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                    ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
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
                Crossfade(
                    targetState =
                        when (feed.syncState) {
                            SyncState.IN_PROGRESS -> 0
                            SyncState.FAILED -> 1
                            SyncState.IDLE,
                            SyncState.COMPLETE,
                            -> 2
                        },
                    label = "activity-feed",
                    modifier = Modifier.fillMaxSize(),
                ) { phase ->
                    when (phase) {
                        0 -> ActivityListSkeleton()
                        1 ->
                            ActivitySyncError(
                                onRetry = viewModel::retryInitialHydrate,
                                modifier =
                                    Modifier
                                        .seDetailHorizontal()
                                        .padding(top = 16.dp),
                            )
                        else ->
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                    PaddingValues(bottom = bottomBarScrollPadding(includeFab = true)),
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
                                    feed.entries.forEach { entry ->
                                        when (entry) {
                                            is ActivityListEntry.DayHeader -> {
                                                stickyHeader(
                                                    key = entry.stableKey(),
                                                    contentType = "header",
                                                ) {
                                                    ActivityDayHeader(day = entry.day)
                                                }
                                            }
                                            is ActivityListEntry.Row -> {
                                                item(
                                                    key = entry.stableKey(),
                                                    contentType = "row",
                                                ) {
                                                    val expenseId = entry.item.relatedExpenseId
                                                    val onClick = remember(expenseId, onOpenExpense) {
                                                        expenseId?.let { id ->
                                                            {
                                                                ActivityPerfLog.interaction("row-click", "expenseId=$id")
                                                                onOpenExpense(id)
                                                            }
                                                        }
                                                    }
                                                    ActivityRow(
                                                        item = entry.item,
                                                        onClick = onClick,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityListSkeleton(modifier: Modifier = Modifier) {
    val loadingCd = stringResource(R.string.activity_loading)
    SeShimmerProvider {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .semantics { contentDescription = loadingCd }
                    .padding(bottom = bottomBarScrollPadding(includeFab = true)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SeLayout.detailHorizontal)
                        .padding(top = 16.dp, bottom = 8.dp),
            ) {
                SeLineSkeleton(widthFraction = 0.28f)
            }
            repeat(7) { ActivityRowSkeleton() }
        }
    }
}

@Composable
private fun ActivityRowSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SeLayout.detailHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SeLayout.iconTileSize)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SplitEaseColors.SurfaceMuted),
            )
            Spacer(modifier = Modifier.width(SeLayout.iconTileGap))
            Column(modifier = Modifier.weight(1f)) {
                SeLineSkeleton(widthFraction = 0.86f)
                Spacer(modifier = Modifier.height(10.dp))
                SeLineSkeleton(widthFraction = 0.42f)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(48.dp)) {
                SeLineSkeleton(widthFraction = 1f)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = activityDividerStart),
            color = SplitEaseColors.Outline,
        )
    }
}

private val activityDividerStart = SeLayout.detailHorizontal + SeLayout.afterIconTile

@Composable
private fun ActivitySyncError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SeErrorText(text = stringResource(R.string.activity_sync_failed))
        Spacer(modifier = Modifier.height(12.dp))
        SeOutlinedButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
        )
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
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SplitEaseColors.Navy,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = SeLayout.detailHorizontal)
                .padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ActivityRow(
    item: ActivityUiItem,
    onClick: (() -> Unit)? = null,
) {
    val icon =
        remember(item.kind) {
            when (item.kind) {
                ActivityKind.EXPENSE -> Icons.Filled.Receipt
                ActivityKind.EXPENSE_UPDATED -> Icons.Filled.Edit
                ActivityKind.EXPENSE_DELETED -> Icons.Filled.Delete
                ActivityKind.PAYMENT -> Icons.Filled.Payments
                ActivityKind.GROUP_CREATED -> Icons.Filled.Group
            }
        }
    val tint =
        when (item.kind) {
            ActivityKind.EXPENSE -> SplitEaseColors.Primary
            ActivityKind.EXPENSE_UPDATED -> SplitEaseColors.Primary
            ActivityKind.EXPENSE_DELETED -> SplitEaseColors.YouOwe
            ActivityKind.PAYMENT -> SplitEaseColors.OwedToYou
            ActivityKind.GROUP_CREATED -> SplitEaseColors.IconFriends
        }
    val showsBalanceSlot =
        remember(item.kind) {
            item.kind == ActivityKind.EXPENSE ||
                item.kind == ActivityKind.EXPENSE_UPDATED ||
                item.kind == ActivityKind.EXPENSE_DELETED ||
                item.kind == ActivityKind.PAYMENT
        }
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
            SeIconTile(icon = icon, tint = tint, size = SeLayout.iconTile)
            Spacer(modifier = Modifier.width(SeLayout.iconTileGap))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isSeen) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SplitEaseColors.Primary),
                        )
                    }
                    ActivityRowTitle(item = item, modifier = Modifier.weight(1f, fill = false))
                }
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = SplitEaseColors.NavyMuted,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = activityDividerStart),
            color = SplitEaseColors.Outline,
        )
    }
}

@Composable
private fun ActivityRowTitle(
    item: ActivityUiItem,
    modifier: Modifier = Modifier,
) {
    Text(
        text = item.annotatedTitle,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
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
                    annotatedTitle = AnnotatedString("Sutej Pal Hotmail added exp3 in Noida room"),
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
                    annotatedTitle = AnnotatedString("You added exp2 in Noida room"),
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
                    annotatedTitle = AnnotatedString("You created \"Trip\""),
                ),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ActivitySkeletonPreview() {
    SePreview {
        ActivityListSkeleton()
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
