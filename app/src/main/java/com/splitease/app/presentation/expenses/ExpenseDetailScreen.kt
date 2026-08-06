package com.splitease.app.presentation.expenses

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.common.shortDisplayName
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton
import java.text.DateFormat
import java.util.Date

/**
 * Expense detail with edit and delete actions in the top bar.
 */
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    onBack: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val detail by viewModel.observeExpenseDetail(expenseId).collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val hasExpense = detail != null
    val me = viewModel.currentUserId()

    SeScreen(
        title = stringResource(R.string.expense_detail_title),
        onBack = onBack,
        actions = {
            if (hasExpense) {
                IconButton(onClick = { onEdit(expenseId) }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_edit_expense),
                        tint = SplitEaseColors.Navy,
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_delete_expense),
                        tint = SplitEaseColors.YouOwe,
                    )
                }
            }
        },
        content = { padding ->
            val snapshot = detail
            if (snapshot == null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = SeLayout.detailHorizontal),
                ) {
                    SeErrorText(uiState.errorMessage ?: stringResource(R.string.expense_not_found))
                    Spacer(Modifier.height(16.dp))
                    SeTextButton(
                        text = stringResource(R.string.cd_back),
                        onClick = onBack,
                    )
                }
                return@SeScreen
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SeLayout.detailHorizontal, vertical = 8.dp),
            ) {
                Text(
                    text = snapshot.expense.description,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            MoneyFormat.format(
                                snapshot.expense.amount,
                                snapshot.expense.currencyCode,
                            ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = SplitEaseColors.Primary,
                    )
                    val side = snapshot.viewerBalanceSide
                    val balanceAmount = snapshot.viewerBalanceAmount
                    if (side != null && balanceAmount != null) {
                        val color =
                            when (side) {
                                LedgerBalanceSide.LENT -> SplitEaseColors.OwedToYou
                                LedgerBalanceSide.BORROWED -> SplitEaseColors.YouOwe
                                LedgerBalanceSide.RECEIVED -> SplitEaseColors.OwedToYou
                                LedgerBalanceSide.PAID -> SplitEaseColors.NavyMuted
                            }
                        val label =
                            when (side) {
                                LedgerBalanceSide.LENT ->
                                    stringResource(R.string.ledger_you_lent)
                                LedgerBalanceSide.BORROWED ->
                                    stringResource(R.string.ledger_you_borrowed)
                                LedgerBalanceSide.RECEIVED ->
                                    stringResource(R.string.balances_you_are_owed_plain)
                                LedgerBalanceSide.PAID ->
                                    stringResource(R.string.balances_you_owe_plain)
                            }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                textAlign = TextAlign.End,
                            )
                            Text(
                                text =
                                    MoneyFormat.format(
                                        balanceAmount,
                                        snapshot.expense.currencyCode,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = color,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)
                DetailLine(stringResource(R.string.label_paid_by), snapshot.payerLabel)
                DetailLine(
                    stringResource(R.string.label_date),
                    DateFormat
                        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(snapshot.expense.expenseDateEpochMs)),
                )
                DetailLine(
                    stringResource(R.string.label_split_type),
                    snapshot.expense.splitType.name
                        .lowercase()
                        .replaceFirstChar { it.titlecase() },
                )
                if (!snapshot.groupName.isNullOrBlank()) {
                    DetailLine(stringResource(R.string.label_group), snapshot.groupName)
                }
                if (!snapshot.expense.notes.isNullOrBlank()) {
                    DetailLine(stringResource(R.string.label_notes), snapshot.expense.notes.orEmpty())
                }
                Spacer(Modifier.height(12.dp))
                ExpenseSplitsSection(
                    detail = snapshot,
                    currentUserId = me,
                )
                val error = uiState.errorMessage
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    SeErrorText(error)
                }
            }
        },
    )

    if (showDeleteConfirm) {
        SeModal(onDismissRequest = { showDeleteConfirm = false }) {
            SeModalTitle(stringResource(R.string.expense_delete_title))
            Spacer(Modifier.height(8.dp))
            SeModalBody(stringResource(R.string.expense_delete_body))
            Spacer(Modifier.height(20.dp))
            SePrimaryButton(
                text = stringResource(R.string.action_delete_expense),
                onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteExpense(expenseId, onSuccess = onDeleted)
                },
            )
            Spacer(Modifier.height(8.dp))
            SeTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { showDeleteConfirm = false },
            )
        }
    }
}

@Composable
private fun ExpenseSplitsSection(
    detail: ExpenseDetailUi,
    currentUserId: String?,
) {
    Text(
        text = stringResource(R.string.label_splits),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
    )
    Spacer(Modifier.height(8.dp))

    val paidPrefix = stringResource(R.string.expense_split_paid)
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paidMoney =
        MoneyFormat.format(detail.expense.amount, detail.expense.currencyCode)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            SeIconTile(Icons.Filled.Person, SplitEaseColors.IconFriends, size = 56)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.payerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text =
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = bodyColor)) {
                                append(paidPrefix)
                                append(" ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = SplitEaseColors.Primary,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ) {
                                append(paidMoney)
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (detail.splits.isNotEmpty()) {
            ExpenseSplitBranchList(
                splits = detail.splits,
                currencyCode = detail.expense.currencyCode,
                currentUserId = currentUserId,
                modifier = Modifier.padding(start = 70.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun ExpenseSplitBranchList(
    splits: List<ExpenseSplitLineUi>,
    currencyCode: String,
    currentUserId: String?,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.5.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        splits.forEachIndexed { index, line ->
            val isLast = index == splits.lastIndex
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(width = 20.dp, height = 28.dp)) {
                    val x = size.width * 0.35f
                    val midY = size.height / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, if (isLast) midY else size.height),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(x, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                ExpenseSplitOwesLine(
                    line = line,
                    currencyCode = currencyCode,
                    isCurrentUser = line.userId == currentUserId,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpenseSplitOwesLine(
    line: ExpenseSplitLineUi,
    currencyCode: String,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val money = MoneyFormat.format(line.owedAmount, currencyCode)
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = SplitEaseColors.YouOwe
    val youLabel = stringResource(R.string.you_label)
    val owesWord = stringResource(R.string.expense_split_owes)
    val shortName = remember(line.participantLabel) { shortDisplayName(line.participantLabel) }

    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = body)) {
                    if (isCurrentUser) {
                        append(youLabel)
                        append(" ")
                        append(owesWord.lowercase())
                        append(" ")
                    } else {
                        append(shortName)
                        append(" ")
                        append(owesWord)
                        append(" ")
                    }
                }
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                    append(money)
                }
            },
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SplitEaseColors.NavyMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = SplitEaseColors.Navy,
        )
    }
}
