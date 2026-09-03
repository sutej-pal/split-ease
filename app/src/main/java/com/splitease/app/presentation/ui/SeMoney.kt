package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.ErrorContainerPlaceholder
import com.splitease.app.presentation.theme.PositiveContainerPlaceholder
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
    val color = tone.color()
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

/** Trailing amount on a ledger row — the visual punch of the list. */
@Composable
fun SeLedgerAmount(
    amount: BigDecimal,
    currencyCode: String,
    tone: SeMoneyTone,
    modifier: Modifier = Modifier,
) {
    Text(
        text = MoneyFormat.format(amount, currencyCode),
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = tone.color(),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
fun SeMoneyTone.color() =
    when (this) {
        SeMoneyTone.YOU_OWE -> SplitEaseColors.YouOwe
        SeMoneyTone.OWED_TO_YOU -> SplitEaseColors.OwedToYou
        SeMoneyTone.SETTLED -> SplitEaseColors.Settled
        SeMoneyTone.NEUTRAL -> MaterialTheme.colorScheme.onBackground
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
    Text(
        text = "$prefix ${MoneyFormat.format(amount, currencyCode)}",
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = tone.color(),
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Two side-by-side balance tiles: you-owe and owed-to-you, with large amounts.
 */
@Composable
fun SeHeroBalancePair(
    iOwe: Map<String, BigDecimal>,
    owedToMe: Map<String, BigDecimal>,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val oweEntry = iOwe.entries.firstOrNull()
    val owedEntry = owedToMe.entries.firstOrNull()
    val bothEmpty = oweEntry == null && owedEntry == null
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeHeroTile(
            label = stringResource(R.string.balances_you_owe_plain),
            amount = oweEntry?.value,
            currencyCode = oweEntry?.key?.ifBlank { currencyCode } ?: currencyCode,
            tone = SeMoneyTone.YOU_OWE,
            modifier = Modifier.weight(1f),
            settled = bothEmpty,
        )
        SeHeroTile(
            label = stringResource(R.string.balances_you_are_owed_plain),
            amount = owedEntry?.value,
            currencyCode = owedEntry?.key?.ifBlank { currencyCode } ?: currencyCode,
            tone = SeMoneyTone.OWED_TO_YOU,
            modifier = Modifier.weight(1f),
            settled = bothEmpty,
        )
    }
}

/**
 * Shimmer stand-in for [SeHeroBalancePair] while the first-login full sync runs.
 * Matches the live tiles' size so the list does not jump when totals appear.
 */
@Composable
fun SeHeroBalancePairSkeleton(modifier: Modifier = Modifier) {
    val loadingCd = stringResource(R.string.groups_balances_loading)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = loadingCd },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeHeroTileSkeleton(modifier = Modifier.weight(1f))
        SeHeroTileSkeleton(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SeHeroTileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SplitEaseColors.SurfaceMuted)
                .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.NavyMuted),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier =
                    Modifier
                        .width(72.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .seShimmer(),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .seShimmer(),
        )
    }
}

@Composable
fun SeLineSkeleton(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.55f,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(widthFraction)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .seShimmer(),
    )
}

@Composable
private fun SeHeroTile(
    label: String,
    amount: BigDecimal?,
    currencyCode: String,
    tone: SeMoneyTone,
    modifier: Modifier = Modifier,
    settled: Boolean = false,
) {
    val fill =
        when {
            settled || amount == null || amount.compareTo(BigDecimal.ZERO) == 0 ->
                SplitEaseColors.SurfaceMuted
            tone == SeMoneyTone.YOU_OWE -> ErrorContainerPlaceholder
            else -> PositiveContainerPlaceholder
        }
    val pip =
        when {
            settled || amount == null || amount.compareTo(BigDecimal.ZERO) == 0 ->
                SplitEaseColors.NavyMuted
            else -> tone.color()
        }
    val value =
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            MoneyFormat.format(BigDecimal.ZERO, currencyCode)
        } else {
            MoneyFormat.format(amount, currencyCode)
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(fill)
                .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(pip),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SplitEaseColors.NavyMuted,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color =
                if (settled || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                    SplitEaseColors.Navy
                } else {
                    tone.color()
                },
            maxLines = 1,
        )
    }
}

@Preview(name = "Money", showBackground = true)
@Composable
private fun SeMoneyPreview() {
    SePreview {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SeHeroBalancePair(
                iOwe = mapOf("INR" to BigDecimal("1642.21")),
                owedToMe = mapOf("INR" to BigDecimal("80.00")),
                currencyCode = "INR",
            )
            SeHeroBalancePairSkeleton()
            SeLedgerAmount(BigDecimal("420.00"), "INR", SeMoneyTone.YOU_OWE)
            SeMoneyText(BigDecimal("420.00"), "INR", SeMoneyTone.YOU_OWE, prefix = "you owe")
        }
    }
}

/**
 * Banner shown on balance screens when mixed currencies are detected.
 */
@Composable
fun SeMixedCurrencyBanner(
    targetCurrency: String,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = SplitEaseColors.PrimarySoft,
                contentColor = SplitEaseColors.PrimaryDark,
            ),
        onClick = onConvert,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CurrencyExchange,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mixed_currency_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.mixed_currency_banner_body, targetCurrency),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
