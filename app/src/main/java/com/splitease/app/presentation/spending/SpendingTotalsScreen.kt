package com.splitease.app.presentation.spending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import com.splitease.app.presentation.theme.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.spending.CategorySpending
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.math.BigDecimal

@Composable
fun SpendingTotalsScreen(
    onBack: () -> Unit,
    viewModel: SpendingTotalsViewModel = hiltViewModel(),
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val rows by viewModel.totals.collectAsStateWithLifecycle()

    SpendingTotalsScreenContent(
        period = period,
        rows = rows,
        onBack = onBack,
        onSetPeriod = viewModel::setPeriod,
    )
}

@Composable
fun SpendingTotalsScreenContent(
    period: SpendingPeriod,
    rows: List<CategorySpending>,
    onBack: () -> Unit,
    onSetPeriod: (SpendingPeriod) -> Unit,
) {
    SeScreen(
        title = stringResource(R.string.spending_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .seDetailHorizontal()
                        .verticalScroll(rememberScrollState()),
            ) {
                SeSectionHeader(text = stringResource(R.string.spending_period))
                Row {
                    SpendingPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { onSetPeriod(option) },
                            label = {
                                SeBodyMedium(
                                    when (option) {
                                        SpendingPeriod.THIS_MONTH ->
                                            stringResource(R.string.spending_this_month)
                                        SpendingPeriod.LAST_30_DAYS ->
                                            stringResource(R.string.spending_last_30)
                                        SpendingPeriod.ALL_TIME ->
                                            stringResource(R.string.spending_all_time)
                                    },
                                )
                            },
                            modifier = Modifier.heightIn(min = 40.dp).padding(end = 8.dp),
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SplitEaseColors.Primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SeSectionHeader(text = stringResource(R.string.spending_by_category))
                if (rows.isEmpty()) {
                    SeEmptyState(message = stringResource(R.string.spending_empty))
                } else {
                    SpendingCategoryChart(rows = rows)
                    Spacer(modifier = Modifier.height(12.dp))
                    rows.forEach { row ->
                        SeListRow(
                            title = row.categoryName,
                            subtitle =
                                pluralStringResource(
                                    R.plurals.spending_expense_count,
                                    row.expenseCount,
                                    row.expenseCount,
                                ),
                            trailing = {
                                SeMoneyText(
                                    amount = row.total,
                                    currencyCode = row.currencyCode,
                                    tone = SeMoneyTone.NEUTRAL,
                                )
                            },
                        )
                    }
                }
            }
        },
    )
}

@Preview(name = "Spending Totals", showBackground = true, heightDp = 720)
@Composable
private fun SpendingTotalsScreenPreview() {
    SePreview {
        SpendingTotalsScreenContent(
            period = SpendingPeriod.THIS_MONTH,
            rows =
                listOf(
                    CategorySpending(
                        categoryId = "1",
                        categoryName = "Groceries",
                        currencyCode = "USD",
                        total = BigDecimal("142.50"),
                        expenseCount = 4,
                    ),
                    CategorySpending(
                        categoryId = "2",
                        categoryName = "Dining Out",
                        currencyCode = "USD",
                        total = BigDecimal("89.00"),
                        expenseCount = 3,
                    ),
                    CategorySpending(
                        categoryId = "3",
                        categoryName = "Entertainment",
                        currencyCode = "USD",
                        total = BigDecimal("45.20"),
                        expenseCount = 2,
                    ),
                ),
            onBack = {},
            onSetPeriod = {},
        )
    }
}

