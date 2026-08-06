package com.splitease.app.presentation.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeLayout
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
 * Horizontal padding is applied *inside* each row so the press/ripple can span
 * the full list width while content stays inset.
 *
 * @param onExpenseClick Invoked with the raw expense id when an expense row is tapped.
 * @param horizontalPadding Inset for month labels and row content
 * (default [SeLayout.detailHorizontal] = 20dp, matching group detail).
 */
fun LazyListScope.ledgerEntries(
    items: List<LedgerListItem>,
    onExpenseClick: ((expenseId: String) -> Unit)? = null,
    horizontalPadding: Dp = SeLayout.detailHorizontal,
) {
    val groups = items.groupByMonth()
    groups.forEach { (monthLabel, monthItems) ->
        item(key = "month-$monthLabel") {
            Text(
                text = monthLabel,
                modifier =
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .padding(top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.NavyMuted,
            )
        }
        items(monthItems, key = { it.id }) { entry ->
            LedgerEntryRow(
                item = entry,
                contentHorizontalPadding = horizontalPadding,
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
    contentHorizontalPadding: Dp = SeLayout.detailHorizontal,
    onClick: (() -> Unit)? = null,
) {
    val icon = categoryIcon(item.categoryIconKey)
    val pastel =
        CategoryPastels[
            (item.categoryIconKey ?: item.id).hashCode().mod(CategoryPastels.size),
        ]
    val (monthLabel, dayLabel) =
        remember(item.sortEpochMs) {
            ledgerDateParts(item.sortEpochMs)
        }
    val balanceColor = balanceLineColor(item.balanceSide)
    val balanceLabel = balanceSideLabel(item.balanceSide)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = contentHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
                maxLines = 1,
            )
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.NavyMuted,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .size(44.dp)
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (balanceLabel != null && item.balanceAmount != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = balanceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = balanceColor,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Text(
                    text = MoneyFormat.format(item.balanceAmount, item.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun balanceSideLabel(side: LedgerBalanceSide?): String? =
    when (side) {
        LedgerBalanceSide.LENT -> stringResource(R.string.ledger_you_lent)
        LedgerBalanceSide.BORROWED -> stringResource(R.string.ledger_you_borrowed)
        LedgerBalanceSide.RECEIVED -> stringResource(R.string.ledger_you_received)
        LedgerBalanceSide.PAID -> stringResource(R.string.ledger_you_paid)
        null -> null
    }

@Composable
private fun balanceLineColor(side: LedgerBalanceSide?): Color =
    when (side) {
        LedgerBalanceSide.LENT, LedgerBalanceSide.RECEIVED -> SplitEaseColors.OwedToYou
        LedgerBalanceSide.BORROWED -> SplitEaseColors.YouOwe
        LedgerBalanceSide.PAID, null -> SplitEaseColors.NavyMuted
    }

private fun categoryIcon(iconKey: String?): ImageVector =
    when (iconKey) {
        "category_food" -> Icons.Filled.Restaurant
        "category_travel" -> Icons.Filled.Flight
        "category_home", "category_rent" -> Icons.Filled.Home
        "category_entertainment" -> Icons.Filled.Movie
        "category_utilities" -> Icons.Filled.Bolt
        "category_payment" -> Icons.Filled.Payments
        "category_general" -> Icons.Filled.Receipt
        else -> Icons.Filled.Category
    }

private fun ledgerDateParts(
    epochMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<String, String> {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault()).format(date)
    val day = DateTimeFormatter.ofPattern("dd", Locale.getDefault()).format(date)
    return month to day
}

private fun List<LedgerListItem>.groupByMonth(
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<String, List<LedgerListItem>>> {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    return groupBy { item ->
        Instant
            .ofEpochMilli(item.sortEpochMs)
            .atZone(zone)
            .toLocalDate()
            .withDayOfMonth(1)
    }.entries
        .sortedByDescending { it.key }
        .map { (monthStart, rows) -> monthFormatter.format(monthStart) to rows }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun LedgerEntryRowPreview() {
    SePreview {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "August 2026",
                modifier = Modifier.padding(horizontal = SeLayout.detailHorizontal, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.NavyMuted,
            )
            LedgerEntryRow(
                LedgerListItem(
                    id = "1",
                    isPayment = false,
                    title = "Laxmikant for rent",
                    subtitle = "You paid ₹5,500.00",
                    sortEpochMs = 1_754_320_000_000L,
                    categoryIconKey = "category_rent",
                    currencyCode = "INR",
                    balanceSide = LedgerBalanceSide.LENT,
                    balanceAmount = BigDecimal("5500.00"),
                ),
            )
            Text(
                text = "July 2026",
                modifier = Modifier.padding(horizontal = SeLayout.detailHorizontal, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.NavyMuted,
            )
            LedgerEntryRow(
                LedgerListItem(
                    id = "2",
                    isPayment = false,
                    title = "Pizza",
                    subtitle = "You paid ₹550.00",
                    sortEpochMs = 1_753_800_000_000L,
                    categoryIconKey = "category_food",
                    currencyCode = "INR",
                    balanceSide = LedgerBalanceSide.LENT,
                    balanceAmount = BigDecimal("366.67"),
                ),
            )
            LedgerEntryRow(
                LedgerListItem(
                    id = "3",
                    isPayment = false,
                    title = "Taxi",
                    subtitle = "Sam paid ₹420.00",
                    sortEpochMs = 1_753_700_000_000L,
                    categoryIconKey = "category_travel",
                    currencyCode = "INR",
                    balanceSide = LedgerBalanceSide.BORROWED,
                    balanceAmount = BigDecimal("210.00"),
                ),
            )
        }
    }
}
