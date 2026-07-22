package com.splitease.app.presentation.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun ActivityScreen(
    onOpenSearch: () -> Unit = {},
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
                            ActivityRow(item = item)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ActivityRow(item: ActivityUiItem) {
    val (icon, tint) =
        when (item.kind) {
            ActivityKind.EXPENSE -> Icons.Filled.Receipt to SplitEaseColors.Primary
            ActivityKind.PAYMENT -> Icons.Filled.Payments to SplitEaseColors.OwedToYou
        }
    SeListRow(
        title = item.title,
        subtitle = item.subtitle,
        leading = { SeIconTile(icon = icon, tint = tint, size = 44) },
        trailing = {
            Text(
                text = item.amountLabel,
                color = SplitEaseColors.Navy,
            )
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
                    subtitle = "Roommates · you paid · 22 Jul 2026",
                    amountLabel = "INR 1200.00",
                    sortEpochMs = 0L,
                ),
            )
            ActivityRow(
                ActivityUiItem(
                    id = "2",
                    kind = ActivityKind.PAYMENT,
                    title = "You paid Alex",
                    subtitle = "Roommates · UPI · 20 Jul 2026",
                    amountLabel = "INR 400.00",
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
            message = "No activity yet. Add an expense or record a payment to see it here.",
            modifier = Modifier.padding(20.dp),
        )
    }
}
