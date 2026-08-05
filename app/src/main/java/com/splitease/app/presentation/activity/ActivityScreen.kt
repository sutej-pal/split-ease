package com.splitease.app.presentation.activity

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeIconTileWithAvatar
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun ActivityScreen(
    onOpenSearch: () -> Unit = {},
    onOpenExpense: (expenseId: String) -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.nav_activity),
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = SplitEaseColors.Navy,
                )
            }
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                if (items.isEmpty()) {
                    SeEmptyState(
                        message = stringResource(R.string.activity_empty),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
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
        },
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isExpenseKind && !item.actorDisplayName.isNullOrBlank()) {
                SeIconTileWithAvatar(
                    icon = icon,
                    tint = tint,
                    actorName = item.actorDisplayName,
                    actorPhotoUrl = item.actorPhotoUrl,
                    size = 44,
                )
            } else {
                SeIconTile(icon = icon, tint = tint, size = 44)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activityTitleText(item.title, item.expenseTitle),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!item.balanceLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.balanceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            when (item.balanceTone) {
                                ActivityBalanceTone.POSITIVE -> SplitEaseColors.OwedToYou
                                ActivityBalanceTone.NEGATIVE -> SplitEaseColors.YouOwe
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                if (item.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.amountLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.amountLabel,
                    color = SplitEaseColors.Navy,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
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
            modifier = Modifier.padding(20.dp),
        )
    }
}
