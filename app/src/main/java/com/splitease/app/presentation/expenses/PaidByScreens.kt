package com.splitease.app.presentation.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeScreen
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Full-screen single-payer picker with a "Multiple people" entry point.
 */
@Composable
fun WhoPaidScreen(
    participants: List<ParticipantOption>,
    selectedUserId: String?,
    isMultiplePeople: Boolean,
    onBack: () -> Unit,
    onSelectPerson: (userId: String) -> Unit,
    onMultiplePeople: () -> Unit,
) {
    BackHandler(onBack = onBack)
    SeScreen(
        title = stringResource(R.string.expense_who_paid),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                participants.forEach { option ->
                    val selected = !isMultiplePeople && option.userId == selectedUserId
                    WhoPaidRow(
                        name = option.label,
                        photoUrl = option.photoUrl,
                        selected = selected,
                        onClick = { onSelectPerson(option.userId) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.expense_multiple_people),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onMultiplePeople)
                            .padding(vertical = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (isMultiplePeople) {
                            SplitEaseColors.Primary
                        } else {
                            SplitEaseColors.Navy
                        },
                    fontWeight =
                        if (isMultiplePeople) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        },
    )
}

/**
 * Full-screen editor for per-person paid amounts (multi-payer).
 *
 * Confirm is enabled only when entered amounts sum exactly to [totalAmount].
 */
@Composable
fun EnterPaidAmountsScreen(
    participants: List<ParticipantOption>,
    currencyCode: String,
    totalAmount: BigDecimal,
    initialAmounts: Map<String, String>,
    onBack: () -> Unit,
    onConfirm: (amounts: Map<String, BigDecimal>) -> Unit,
) {
    BackHandler(onBack = onBack)
    var drafts by remember(participants, initialAmounts) {
        mutableStateOf(
            participants.associate { option ->
                option.userId to initialAmounts[option.userId].orEmpty().ifBlank { "0.00" }
            },
        )
    }
    val symbol = currencySymbolFor(currencyCode)
    val enteredSum =
        remember(drafts) {
            drafts.values
                .fold(BigDecimal.ZERO) { acc, text ->
                    acc.add(
                        runCatching { BigDecimal(text.trim().ifBlank { "0" }) }
                            .getOrDefault(BigDecimal.ZERO),
                    )
                }.setScale(2, RoundingMode.HALF_UP)
        }
    val total = totalAmount.setScale(2, RoundingMode.HALF_UP)
    val remaining = total.subtract(enteredSum).setScale(2, RoundingMode.HALF_UP)
    val canConfirm = enteredSum.compareTo(total) == 0 && total >= BigDecimal.ZERO

    SeScreen(
        title = stringResource(R.string.expense_enter_paid_amounts),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    if (!canConfirm) return@IconButton
                    val parsed =
                        drafts.mapValues { (_, text) ->
                            runCatching { BigDecimal(text.trim().ifBlank { "0" }) }
                                .getOrDefault(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                        }
                    onConfirm(parsed)
                },
                enabled = canConfirm,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_confirm_paid_amounts),
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
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    participants.forEach { option ->
                        PaidAmountRow(
                            name = option.label,
                            photoUrl = option.photoUrl,
                            currencySymbol = symbol,
                            value = drafts[option.userId].orEmpty(),
                            onValueChange = { raw ->
                                drafts = drafts + (option.userId to filterMoneyInput(raw))
                            },
                        )
                    }
                }
                HorizontalDivider(color = SplitEaseColors.Outline)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.expense_paid_of_total,
                                MoneyFormat.format(enteredSum, currencyCode),
                                MoneyFormat.format(total, currencyCode),
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = SplitEaseColors.Navy,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val remainderLabel =
                        when {
                            remaining.compareTo(BigDecimal.ZERO) > 0 ->
                                stringResource(
                                    R.string.expense_paid_left,
                                    MoneyFormat.format(remaining, currencyCode),
                                )
                            remaining.compareTo(BigDecimal.ZERO) < 0 ->
                                stringResource(
                                    R.string.expense_paid_over,
                                    MoneyFormat.format(remaining.abs(), currencyCode),
                                )
                            else ->
                                stringResource(
                                    R.string.expense_paid_left,
                                    MoneyFormat.format(BigDecimal.ZERO.setScale(2), currencyCode),
                                )
                        }
                    Text(
                        text = remainderLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                                SplitEaseColors.NavyMuted
                            } else {
                                SplitEaseColors.YouOwe
                            },
                    )
                }
            }
        },
    )
}

@Composable
private fun WhoPaidRow(
    name: String,
    photoUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeAvatarBadge(
            name = name,
            photoUrl = photoUrl,
            size = 44.dp,
            borderWidth = 0.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = SplitEaseColors.Navy,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PaidAmountRow(
    name: String,
    photoUrl: String?,
    currencySymbol: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeAvatarBadge(
            name = name,
            photoUrl = photoUrl,
            size = 44.dp,
            borderWidth = 0.dp,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currencySymbol,
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.NavyMuted,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.width(88.dp)) {
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        }
    }
}

private fun filterMoneyInput(raw: String): String {
    val filtered = raw.filter { c -> c.isDigit() || c == '.' }
    val parts = filtered.split(".")
    return when {
        parts.size <= 1 -> filtered
        else -> parts[0] + "." + parts[1].take(2)
    }
}

private fun currencySymbolFor(code: String): String =
    runCatching {
        java.util.Currency
            .getInstance(code)
            .getSymbol(java.util.Locale.getDefault())
    }.getOrElse { code }
