package com.splitease.app.presentation.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.split.SplitCalculator
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeScreen
import java.math.BigDecimal
import java.math.RoundingMode

data class AdjustSplitResult(
    val splitType: SplitType,
    val selectedIds: Set<String>,
    val unequalTexts: Map<String, String>,
    val percentTexts: Map<String, String>,
    val shareTexts: Map<String, String>,
    val adjustmentTexts: Map<String, String>,
)

/**
 * Full-screen split editor with Equally / Unequally / Percentages / Shares / Adjustment.
 */
@Composable
fun AdjustSplitScreen(
    participants: List<ParticipantOption>,
    currencyCode: String,
    totalAmount: BigDecimal,
    initialSplitType: SplitType,
    initialSelectedIds: Set<String>,
    initialUnequalTexts: Map<String, String>,
    initialPercentTexts: Map<String, String>,
    initialShareTexts: Map<String, String>,
    initialAdjustmentTexts: Map<String, String>,
    onBack: () -> Unit,
    onConfirm: (AdjustSplitResult) -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by remember(initialSplitType) { mutableStateOf(initialSplitType) }
    var selectedIds by remember(initialSelectedIds) {
        mutableStateOf(initialSelectedIds.ifEmpty { participants.map { it.userId }.toSet() })
    }
    var unequalTexts by remember(participants, initialUnequalTexts) {
        mutableStateOf(defaultMoneyMap(participants, initialUnequalTexts))
    }
    var percentTexts by remember(participants, initialPercentTexts) {
        mutableStateOf(defaultMoneyMap(participants, initialPercentTexts, blank = "0"))
    }
    var shareTexts by remember(participants, initialShareTexts) {
        mutableStateOf(
            participants.associate { option ->
                option.userId to (initialShareTexts[option.userId].orEmpty().ifBlank { "0" })
            },
        )
    }
    var adjustmentTexts by remember(participants, initialAdjustmentTexts) {
        mutableStateOf(defaultMoneyMap(participants, initialAdjustmentTexts))
    }

    val total = totalAmount.setScale(2, RoundingMode.HALF_UP)
    val symbol = currencySymbolForCode(currencyCode)
    val selectedOrdered = participants.filter { it.userId in selectedIds }
    val canConfirm = canConfirmSplit(tab, selectedIds, total, unequalTexts, percentTexts, shareTexts, adjustmentTexts)

    SeScreen(
        title = stringResource(R.string.expense_adjust_split),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    if (!canConfirm) return@IconButton
                    onConfirm(
                        AdjustSplitResult(
                            splitType = tab,
                            selectedIds = selectedIds,
                            unequalTexts = unequalTexts,
                            percentTexts = percentTexts,
                            shareTexts = shareTexts,
                            adjustmentTexts = adjustmentTexts,
                        ),
                    )
                },
                enabled = canConfirm,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_confirm_split),
                    tint =
                        if (canConfirm) {
                            SplitEaseColors.Primary
                        } else {
                            SplitEaseColors.OutlineStrong
                        },
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
                SplitTypeTabs(selected = tab, onSelect = { tab = it })
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    SplitIntro(tab = tab)
                    Spacer(modifier = Modifier.height(16.dp))
                    when (tab) {
                        SplitType.EQUAL -> {
                            participants.forEach { option ->
                                EqualSplitRow(
                                    name = option.label,
                                    photoUrl = option.photoUrl,
                                    checked = option.userId in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds =
                                            if (checked) {
                                                selectedIds + option.userId
                                            } else {
                                                (selectedIds - option.userId).ifEmpty { selectedIds }
                                            }
                                    },
                                )
                            }
                        }
                        SplitType.UNEQUAL -> {
                            selectedOrdered.forEach { option ->
                                AmountInputRow(
                                    name = option.label,
                                    photoUrl = option.photoUrl,
                                    leading = symbol,
                                    value = unequalTexts[option.userId].orEmpty(),
                                    onValueChange = {
                                        unequalTexts =
                                            unequalTexts + (option.userId to filterDecimalInput(it))
                                    },
                                )
                            }
                        }
                        SplitType.PERCENTAGE -> {
                            val owed =
                                previewOwed(
                                    total = total,
                                    splitType = SplitType.PERCENTAGE,
                                    ids = selectedOrdered.map { it.userId },
                                    percentages =
                                        selectedOrdered.associate {
                                            it.userId to
                                                parseDecimal(percentTexts[it.userId])
                                        },
                                )
                            selectedOrdered.forEach { option ->
                                AmountInputRow(
                                    name = option.label,
                                    photoUrl = option.photoUrl,
                                    subtitle = MoneyFormat.format(owed[option.userId] ?: ZERO, currencyCode),
                                    value = percentTexts[option.userId].orEmpty(),
                                    onValueChange = {
                                        percentTexts =
                                            percentTexts + (option.userId to filterDecimalInput(it))
                                    },
                                    trailing = "%",
                                )
                            }
                        }
                        SplitType.SHARES -> {
                            val owed =
                                previewOwed(
                                    total = total,
                                    splitType = SplitType.SHARES,
                                    ids = selectedOrdered.map { it.userId },
                                    shares =
                                        selectedOrdered.associate {
                                            it.userId to (shareTexts[it.userId]?.toIntOrNull() ?: 0)
                                        },
                                )
                            selectedOrdered.forEach { option ->
                                AmountInputRow(
                                    name = option.label,
                                    photoUrl = option.photoUrl,
                                    subtitle = MoneyFormat.format(owed[option.userId] ?: ZERO, currencyCode),
                                    value = shareTexts[option.userId].orEmpty(),
                                    onValueChange = {
                                        shareTexts =
                                            shareTexts + (option.userId to filterIntInput(it))
                                    },
                                    trailing = stringResource(R.string.split_shares_suffix),
                                    keyboardType = KeyboardType.Number,
                                )
                            }
                        }
                        SplitType.ADJUSTMENT -> {
                            val owed =
                                previewOwed(
                                    total = total,
                                    splitType = SplitType.ADJUSTMENT,
                                    ids = selectedOrdered.map { it.userId },
                                    adjustments =
                                        selectedOrdered.associate {
                                            it.userId to parseDecimal(adjustmentTexts[it.userId])
                                        },
                                )
                            selectedOrdered.forEach { option ->
                                AmountInputRow(
                                    name = option.label,
                                    photoUrl = option.photoUrl,
                                    subtitle = MoneyFormat.format(owed[option.userId] ?: ZERO, currencyCode),
                                    leading = "+",
                                    value = adjustmentTexts[option.userId].orEmpty(),
                                    onValueChange = {
                                        adjustmentTexts =
                                            adjustmentTexts + (option.userId to filterDecimalInput(it))
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = SplitEaseColors.Outline)
                SplitFooter(
                    tab = tab,
                    currencyCode = currencyCode,
                    total = total,
                    selectedCount = selectedIds.size,
                    unequalTexts = unequalTexts,
                    percentTexts = percentTexts,
                    shareTexts = shareTexts,
                    selectedIds = selectedIds,
                    allSelected = selectedIds.size == participants.size && participants.isNotEmpty(),
                    onToggleAll = {
                        selectedIds =
                            if (selectedIds.size == participants.size) {
                                setOfNotNull(participants.firstOrNull()?.userId)
                            } else {
                                participants.map { it.userId }.toSet()
                            }
                    },
                )
            }
        },
    )
}

@Composable
private fun SplitTypeTabs(
    selected: SplitType,
    onSelect: (SplitType) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SplitType.entries.forEach { type ->
            val label =
                when (type) {
                    SplitType.EQUAL -> stringResource(R.string.split_tab_equally)
                    SplitType.UNEQUAL -> stringResource(R.string.split_tab_unequally)
                    SplitType.PERCENTAGE -> stringResource(R.string.split_tab_percentages)
                    SplitType.SHARES -> stringResource(R.string.split_tab_shares)
                    SplitType.ADJUSTMENT -> stringResource(R.string.split_tab_adjustment)
                }
            val active = type == selected
            Column(
                modifier =
                    Modifier
                        .clickable { onSelect(type) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) SplitEaseColors.Navy else SplitEaseColors.NavyMuted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .height(2.dp)
                            .width(if (active) 28.dp else 0.dp),
                ) {
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = if (active) SplitEaseColors.Navy else SplitEaseColors.Outline,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = SplitEaseColors.Outline)
}

@Composable
private fun SplitIntro(tab: SplitType) {
    val title =
        when (tab) {
            SplitType.EQUAL -> stringResource(R.string.split_equal_title)
            SplitType.UNEQUAL -> stringResource(R.string.split_unequal_title)
            SplitType.PERCENTAGE -> stringResource(R.string.split_percent_title)
            SplitType.SHARES -> stringResource(R.string.split_shares_title)
            SplitType.ADJUSTMENT -> stringResource(R.string.split_adjustment_title)
        }
    val subtitle =
        when (tab) {
            SplitType.EQUAL -> stringResource(R.string.split_equal_subtitle)
            SplitType.UNEQUAL -> stringResource(R.string.split_unequal_subtitle)
            SplitType.PERCENTAGE -> stringResource(R.string.split_percent_subtitle)
            SplitType.SHARES -> stringResource(R.string.split_shares_subtitle)
            SplitType.ADJUSTMENT -> stringResource(R.string.split_adjustment_subtitle)
        }
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = SplitEaseColors.Navy,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = SplitEaseColors.NavyMuted,
    )
}

@Composable
private fun EqualSplitRow(
    name: String,
    photoUrl: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeAvatarBadge(name = name, photoUrl = photoUrl, size = 44.dp, borderWidth = 0.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = SplitEaseColors.Primary,
                    uncheckedColor = SplitEaseColors.OutlineStrong,
                ),
        )
    }
}

@Composable
private fun AmountInputRow(
    name: String,
    photoUrl: String?,
    value: String,
    onValueChange: (String) -> Unit,
    subtitle: String? = null,
    leading: String? = null,
    trailing: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeAvatarBadge(name = name, photoUrl = photoUrl, size = 44.dp, borderWidth = 0.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Text(
                    text = leading,
                    style = MaterialTheme.typography.titleMedium,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(modifier = Modifier.width(72.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            color = SplitEaseColors.Navy,
                            textAlign = TextAlign.End,
                        ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                )
                HorizontalDivider(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(top = 22.dp),
                    color = SplitEaseColors.OutlineStrong,
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.titleMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
    }
}

@Composable
private fun SplitFooter(
    tab: SplitType,
    currencyCode: String,
    total: BigDecimal,
    selectedCount: Int,
    unequalTexts: Map<String, String>,
    percentTexts: Map<String, String>,
    shareTexts: Map<String, String>,
    selectedIds: Set<String>,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
) {
    when (tab) {
        SplitType.EQUAL -> {
            val perPerson =
                if (selectedCount > 0 && total > ZERO) {
                    total.divide(BigDecimal(selectedCount), 2, RoundingMode.HALF_UP)
                } else {
                    ZERO
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.split_per_person,
                            selectedCount,
                            MoneyFormat.format(perPerson, currencyCode),
                            selectedCount,
                        ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = SplitEaseColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.split_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = SplitEaseColors.Navy,
                )
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleAll() },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = SplitEaseColors.Primary,
                            uncheckedColor = SplitEaseColors.OutlineStrong,
                        ),
                )
            }
        }
        SplitType.UNEQUAL -> {
            val entered =
                selectedIds
                    .fold(ZERO) { acc, id -> acc.add(parseDecimal(unequalTexts[id])) }
                    .setScale(2, RoundingMode.HALF_UP)
            FooterOfTotal(
                primary =
                    stringResource(
                        R.string.expense_paid_of_total,
                        MoneyFormat.format(entered, currencyCode),
                        MoneyFormat.format(total, currencyCode),
                    ),
                remaining = total.subtract(entered),
                currencyCode = currencyCode,
            )
        }
        SplitType.PERCENTAGE -> {
            val entered =
                selectedIds
                    .fold(ZERO) { acc, id -> acc.add(parseDecimal(percentTexts[id])) }
                    .setScale(2, RoundingMode.HALF_UP)
            val remaining = HUNDRED.subtract(entered)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.split_percent_of_total,
                            entered.stripTrailingZeros().toPlainString(),
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    color = SplitEaseColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        when {
                            remaining > ZERO ->
                                stringResource(
                                    R.string.split_percent_left,
                                    remaining.stripTrailingZeros().toPlainString(),
                                )
                            remaining < ZERO ->
                                stringResource(
                                    R.string.split_percent_over,
                                    remaining.abs().stripTrailingZeros().toPlainString(),
                                )
                            else ->
                                stringResource(R.string.split_percent_left, "0")
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (remaining.compareTo(ZERO) == 0) {
                            SplitEaseColors.NavyMuted
                        } else {
                            SplitEaseColors.YouOwe
                        },
                )
            }
        }
        SplitType.SHARES -> {
            val totalShares =
                selectedIds.sumOf { id -> shareTexts[id]?.toIntOrNull() ?: 0 }
            Text(
                text = pluralStringResource(R.plurals.split_total_shares, totalShares, totalShares),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (totalShares > 0) SplitEaseColors.Navy else SplitEaseColors.YouOwe,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        SplitType.ADJUSTMENT -> {
            // No dedicated footer beyond list subtitles; keep spacing consistent.
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FooterOfTotal(
    primary: String,
    remaining: BigDecimal,
    currencyCode: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = primary,
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                when {
                    remaining > ZERO ->
                        stringResource(
                            R.string.expense_paid_left,
                            MoneyFormat.format(remaining, currencyCode),
                        )
                    remaining < ZERO ->
                        stringResource(
                            R.string.expense_paid_over,
                            MoneyFormat.format(remaining.abs(), currencyCode),
                        )
                    else ->
                        stringResource(
                            R.string.expense_paid_left,
                            MoneyFormat.format(ZERO, currencyCode),
                        )
                },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (remaining.compareTo(ZERO) == 0) {
                    SplitEaseColors.NavyMuted
                } else {
                    SplitEaseColors.YouOwe
                },
        )
    }
}

private val ZERO = BigDecimal.ZERO.setScale(2)
private val HUNDRED = BigDecimal("100").setScale(2)

private fun defaultMoneyMap(
    participants: List<ParticipantOption>,
    initial: Map<String, String>,
    blank: String = "0.00",
): Map<String, String> =
    participants.associate { option ->
        option.userId to initial[option.userId].orEmpty().ifBlank { blank }
    }

private fun parseDecimal(text: String?): BigDecimal =
    runCatching { BigDecimal(text?.trim().orEmpty().ifBlank { "0" }) }
        .getOrDefault(BigDecimal.ZERO)
        .setScale(2, RoundingMode.HALF_UP)

private fun filterDecimalInput(raw: String): String {
    val filtered = raw.filter { c -> c.isDigit() || c == '.' }
    val parts = filtered.split(".")
    return when {
        parts.size <= 1 -> filtered
        else -> parts[0] + "." + parts[1].take(2)
    }
}

private fun filterIntInput(raw: String): String = raw.filter { it.isDigit() }.take(6)

private fun currencySymbolForCode(code: String): String =
    runCatching {
        java.util.Currency
            .getInstance(code)
            .getSymbol(java.util.Locale.getDefault())
    }.getOrElse { code }

private fun canConfirmSplit(
    tab: SplitType,
    selectedIds: Set<String>,
    total: BigDecimal,
    unequalTexts: Map<String, String>,
    percentTexts: Map<String, String>,
    shareTexts: Map<String, String>,
    adjustmentTexts: Map<String, String>,
): Boolean {
    if (selectedIds.isEmpty()) return false
    return when (tab) {
        SplitType.EQUAL -> true
        SplitType.UNEQUAL -> {
            val sum =
                selectedIds
                    .fold(ZERO) { acc, id -> acc.add(parseDecimal(unequalTexts[id])) }
                    .setScale(2, RoundingMode.HALF_UP)
            sum.compareTo(total) == 0
        }
        SplitType.PERCENTAGE -> {
            val sum =
                selectedIds
                    .fold(ZERO) { acc, id -> acc.add(parseDecimal(percentTexts[id])) }
                    .setScale(2, RoundingMode.HALF_UP)
            sum.compareTo(HUNDRED) == 0
        }
        SplitType.SHARES -> selectedIds.sumOf { shareTexts[it]?.toIntOrNull() ?: 0 } > 0
        SplitType.ADJUSTMENT -> {
            val adjSum =
                selectedIds
                    .fold(ZERO) { acc, id -> acc.add(parseDecimal(adjustmentTexts[id])) }
                    .setScale(2, RoundingMode.HALF_UP)
            adjSum <= total
        }
    }
}

private fun previewOwed(
    total: BigDecimal,
    splitType: SplitType,
    ids: List<String>,
    percentages: Map<String, BigDecimal> = emptyMap(),
    shares: Map<String, Int> = emptyMap(),
    adjustments: Map<String, BigDecimal> = emptyMap(),
): Map<String, BigDecimal> {
    if (ids.isEmpty() || total <= ZERO) return ids.associateWith { ZERO }
    return runCatching {
        SplitCalculator.calculate(
            total = total,
            splitType = splitType,
            participantIds = ids,
            percentages = percentages,
            shares = shares,
            adjustments = adjustments,
        )
    }.getOrElse { ids.associateWith { ZERO } }
}
