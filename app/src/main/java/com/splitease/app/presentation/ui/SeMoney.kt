package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import java.math.BigDecimal

enum class SeMoneyTone {
    NEUTRAL,
    YOU_OWE,
    OWED_TO_YOU,
    SETTLED,
}

@Composable
fun SeMoneyText(
    amount: BigDecimal,
    currencyCode: String,
    tone: SeMoneyTone,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    val color =
        when (tone) {
            SeMoneyTone.YOU_OWE -> SplitEaseColors.YouOwe
            SeMoneyTone.OWED_TO_YOU -> SplitEaseColors.OwedToYou
            SeMoneyTone.SETTLED -> SplitEaseColors.Settled
            SeMoneyTone.NEUTRAL -> MaterialTheme.colorScheme.onBackground
        }
    val money = MoneyFormat.format(amount, currencyCode)
    val text =
        when {
            tone == SeMoneyTone.SETTLED && prefix != null -> prefix
            prefix != null -> "$prefix $money"
            else -> money
        }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun SeOverallSummary(
    prefix: String,
    amount: BigDecimal?,
    currencyCode: String,
    tone: SeMoneyTone,
    modifier: Modifier = Modifier,
) {
    if (amount == null) {
        Text(
            text = prefix,
            modifier = modifier,
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Settled,
        )
        return
    }
    val accent =
        when (tone) {
            SeMoneyTone.YOU_OWE -> SplitEaseColors.YouOwe
            SeMoneyTone.OWED_TO_YOU -> SplitEaseColors.OwedToYou
            else -> MaterialTheme.colorScheme.onBackground
        }
    Text(
        text =
            buildAnnotatedString {
                append(prefix)
                append(" ")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(MoneyFormat.format(amount, currencyCode))
                }
            },
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Preview(name = "Money", showBackground = true)
@Composable
private fun SeMoneyPreview() {
    SePreview {
        Column {
            SeOverallSummary(
                prefix = "Overall, you owe",
                amount = BigDecimal("1642.21"),
                currencyCode = "INR",
                tone = SeMoneyTone.YOU_OWE,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeMoneyText(BigDecimal("420.00"), "INR", SeMoneyTone.YOU_OWE, prefix = "you owe")
            SeMoneyText(BigDecimal("100.00"), "INR", SeMoneyTone.OWED_TO_YOU, prefix = "you are owed")
            SeMoneyText(BigDecimal.ZERO, "INR", SeMoneyTone.SETTLED, prefix = "settled up")
        }
    }
}
