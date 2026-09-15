package com.splitease.app.presentation.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeInlineLoader
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import java.text.DateFormat
import java.util.Date

@Composable
fun CurrencyConversionScreen(
    onBack: () -> Unit,
    onConverted: () -> Unit,
    viewModel: CurrencyConversionViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.success) {
        if (ui.success) onConverted()
    }

    SeScreen(
        title = stringResource(R.string.title_currency_conversion),
        onBack = onBack,
    ) { padding ->
        if (ui.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding.values), contentAlignment = Alignment.Center) {
                SeInlineLoader()
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    item {
                        ConversionWarning()
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.conversion_expenses_to_convert, ui.expensesToConvert.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = SplitEaseColors.Navy,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(ui.expensesToConvert, key = { it.id }) { item ->
                        ConvertibleExpenseRow(item, ui.targetCurrency)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                ) {
                    if (ui.error != null) {
                        Text(
                            text = ui.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    SePrimaryButton(
                        text = stringResource(R.string.action_convert_all),
                        onClick = viewModel::convert,
                        isLoading = ui.isSubmitting,
                        enabled = ui.expensesToConvert.isNotEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionWarning() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = SplitEaseColors.YouOwe,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.conversion_warning_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.YouOwe,
            )
            Text(
                text = stringResource(R.string.conversion_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
        }
    }
}

@Composable
private fun ConvertibleExpenseRow(
    item: ConvertibleExpenseUi,
    targetCurrency: String,
) {
    val dateLabel =
        remember(item.dateEpochMs) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateEpochMs))
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = SplitEaseColors.NavyMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MoneyFormat.format(item.originalAmount, item.originalCurrency),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.Navy,
            )
            Text(
                text = " → ",
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
            Text(
                text = MoneyFormat.format(item.convertedAmount, targetCurrency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.Primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(at ${item.rate})",
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
            )
        }
    }
}
