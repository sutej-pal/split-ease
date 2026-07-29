package com.splitease.app.presentation.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SePreview
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CategoryPastels =
    listOf(
        Color(0xFFFFE0E6),
        Color(0xFFE0F5E9),
        Color(0xFFE8E0F5),
        Color(0xFFFFF3D6),
        Color(0xFFE0F0FF),
        Color(0xFFFFE8D6),
    )

/**
 * Inserts month headers + [LedgerEntryRow] items into a [LazyListScope].
 *
 * @param onExpenseClick Invoked with the raw expense id when an expense row is tapped.
 */
fun LazyListScope.ledgerEntries(
    items: List<LedgerListItem>,
    onExpenseClick: ((expenseId: String) -> Unit)? = null,
) {
    val groups = items.groupByMonth()
    groups.forEach { (monthLabel, monthItems) ->
        item(key = "month-$monthLabel") {
            Text(
                text = monthLabel,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.Navy,
            )
        }
        items(monthItems, key = { it.id }) { entry ->
            LedgerEntryRow(
                item = entry,
                onClick =
                    if (!entry.isPayment && onExpenseClick != null) {
                        {
                            onExpenseClick(entry.id.removePrefix("expense-"))
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
fun LedgerEntryRow(
    item: LedgerListItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val localDate =
        remember(item.sortEpochMs) {
            Instant.ofEpochMilli(item.sortEpochMs).atZone(zone).toLocalDate()
        }
    val monthAbbr =
        remember(localDate) {
            DateTimeFormatter.ofPattern("MMM", Locale.getDefault()).format(localDate)
        }
    val day =
        remember(localDate) {
            DateTimeFormatter.ofPattern("d", Locale.getDefault()).format(localDate)
        }
    val icon = categoryIcon(item.categoryIconKey)
    val pastel =
        CategoryPastels[
            (item.categoryIconKey ?: item.id).hashCode().mod(CategoryPastels.size),
        ]

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = monthAbbr,
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.NavyMuted,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pastel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SplitEaseColors.Navy.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val paidLine =
                if (item.payerLabel != null && item.paidAmount != null) {
                    stringResource(
                        R.string.ledger_paid_by,
                        item.payerLabel,
                        MoneyFormat.format(item.paidAmount, item.currencyCode),
                    )
                } else {
                    item.subtitle
                }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = paidLine,
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        BalanceTrailing(item = item)
    }
}

@Composable
private fun BalanceTrailing(item: LedgerListItem) {
    when {
        item.isPayment -> {
            val amount = item.balanceAmount ?: item.paidAmount ?: return
            Column(
                modifier = Modifier.widthIn(min = 88.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = MoneyFormat.format(amount, item.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SplitEaseColors.Navy,
                    textAlign = TextAlign.End,
                )
            }
        }
        item.balanceSide != null && item.balanceAmount != null -> {
            val color =
                when (item.balanceSide) {
                    LedgerBalanceSide.LENT -> SplitEaseColors.OwedToYou
                    LedgerBalanceSide.BORROWED -> SplitEaseColors.YouOwe
                }
            val label =
                when (item.balanceSide) {
                    LedgerBalanceSide.LENT -> stringResource(R.string.ledger_you_lent)
                    LedgerBalanceSide.BORROWED -> stringResource(R.string.ledger_you_borrowed)
                }
            Column(
                modifier = Modifier.widthIn(min = 88.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Text(
                    text = MoneyFormat.format(item.balanceAmount, item.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun categoryIcon(iconKey: String?): ImageVector =
    when (iconKey) {
        "category_food" -> Icons.Filled.Restaurant
        "category_travel" -> Icons.Filled.Flight
        "category_rent" -> Icons.Filled.Home
        "category_utilities" -> Icons.Filled.Bolt
        "category_entertainment" -> Icons.Filled.Movie
        "category_payment" -> Icons.Filled.Payments
        "category_general" -> Icons.Filled.Receipt
        else -> Icons.Filled.Category
    }

private fun List<LedgerListItem>.groupByMonth(
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Pair<String, List<LedgerListItem>>> {
    if (isEmpty()) return emptyList()
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    return groupBy { item ->
        Instant.ofEpochMilli(item.sortEpochMs).atZone(zoneId).toLocalDate().withDayOfMonth(1)
    }.entries
        .sortedByDescending { it.key }
        .map { (monthStart, rows) -> monthFormatter.format(monthStart) to rows }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LedgerEntryRowPreview() {
    SePreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "March 2021",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            LedgerEntryRow(
                LedgerListItem(
                    id = "1",
                    isPayment = false,
                    title = "Plane",
                    subtitle = "",
                    sortEpochMs = 1_616_428_800_000L,
                    categoryIconKey = "category_travel",
                    payerLabel = "Earl E.",
                    paidAmount = BigDecimal("600.00"),
                    currencyCode = "USD",
                    balanceSide = LedgerBalanceSide.BORROWED,
                    balanceAmount = BigDecimal("600.00"),
                ),
            )
            LedgerEntryRow(
                LedgerListItem(
                    id = "2",
                    isPayment = false,
                    title = "Fuel up",
                    subtitle = "",
                    sortEpochMs = 1_615_392_000_000L,
                    categoryIconKey = "category_utilities",
                    payerLabel = "You",
                    paidAmount = BigDecimal("48.06"),
                    currencyCode = "USD",
                    balanceSide = LedgerBalanceSide.LENT,
                    balanceAmount = BigDecimal("24.03"),
                ),
            )
        }
    }
}
