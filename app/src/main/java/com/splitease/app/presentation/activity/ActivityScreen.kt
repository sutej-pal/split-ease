package com.splitease.app.presentation.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeIconTileWithAvatar
import com.splitease.app.presentation.ui.SeListRow
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
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
    SeListRow(
        title = item.title,
        subtitle = item.subtitle,
        onClick = onClick,
        leading = {
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
        },
        trailing =
            if (item.amountLabel.isBlank()) {
                null
            } else {
                {
                    Text(
                        text = item.amountLabel,
                        color = SplitEaseColors.Navy,
                    )
                }
            },
    )
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ActivityScreenPreview() {
    SePreview {
        Column(modifier = Modifier.padding(20.dp)) {
            ActivityRow(
                ActivityUiItem(
                    id = "1",
                    kind = ActivityKind.EXPENSE,
                    title = "Dinner",
                    subtitle = "Roommates · Added by You · 22 Jul 2026",
                    amountLabel = "INR 1200.00",
                    sortEpochMs = 0L,
                    actorDisplayName = "You",
                ),
            )
            ActivityRow(
                ActivityUiItem(
                    id = "2",
                    kind = ActivityKind.EXPENSE_UPDATED,
                    title = "Updated: Dinner",
                    subtitle = "Roommates · Updated by You · 22 Jul 2026",
                    amountLabel = "INR 1400.00",
                    sortEpochMs = 0L,
                    actorDisplayName = "You",
                ),
            )
            ActivityRow(
                ActivityUiItem(
                    id = "3",
                    kind = ActivityKind.GROUP_CREATED,
                    title = "You created \"Trip\"",
                    subtitle = "22 Jul 2026",
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
