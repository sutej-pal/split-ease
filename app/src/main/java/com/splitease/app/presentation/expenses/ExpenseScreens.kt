package com.splitease.app.presentation.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import java.math.BigDecimal

@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendUserId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL.name) }
    var recurrence by rememberSaveable { mutableStateOf(RecurrenceFrequency.NONE.name) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var customCategory by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var paidBy by rememberSaveable { mutableStateOf("") }
    var participants by remember { mutableStateOf<List<ParticipantOption>>(emptyList()) }
    var unequalTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var percentTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var shareTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(groupId, friendUserId) {
        participants =
            when {
                groupId != null -> viewModel.resolveGroupParticipantOptions(groupId)
                friendUserId != null -> viewModel.resolveFriendParticipantOptions(friendUserId)
                else -> emptyList()
            }
        val me = viewModel.currentUserId().orEmpty()
        paidBy = me
        selected =
            if (friendUserId != null) {
                participants.map { it.userId }.toSet()
            } else {
                participants.map { it.userId }.toSet()
            }
    }

    SeScreen(
        title = stringResource(R.string.action_add_expense),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = stringResource(R.string.add_expense_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SeTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.label_description),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SeTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.label_amount),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !uiState.isSubmitting,
                )

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.label_category))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.chunked(3).forEach { rowCats ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowCats.forEach { cat ->
                                FilterChip(
                                    selected = categoryId == cat.id,
                                    onClick = {
                                        categoryId = if (categoryId == cat.id) null else cat.id
                                    },
                                    label = { Text(cat.name) },
                                    enabled = !uiState.isSubmitting,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SplitEaseColors.Primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = SplitEaseColors.Surface,
                                            labelColor = SplitEaseColors.Navy,
                                        ),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SeTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = stringResource(R.string.label_custom_category),
                    enabled = !uiState.isSubmitting,
                )
                if (customCategory.isNotBlank()) {
                    SeTextButton(
                        text = stringResource(R.string.action_add_category),
                        onClick = {
                            viewModel.addCustomCategory(customCategory) { id ->
                                categoryId = id
                                customCategory = ""
                            }
                        },
                        enabled = !uiState.isSubmitting,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.label_recurrence))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecurrenceFrequency.entries.chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { freq ->
                                FilterChip(
                                    selected = recurrence == freq.name,
                                    onClick = { recurrence = freq.name },
                                    label = {
                                        Text(
                                            when (freq) {
                                                RecurrenceFrequency.NONE ->
                                                    stringResource(R.string.recurrence_none)
                                                RecurrenceFrequency.WEEKLY ->
                                                    stringResource(R.string.recurrence_weekly)
                                                RecurrenceFrequency.MONTHLY ->
                                                    stringResource(R.string.recurrence_monthly)
                                                RecurrenceFrequency.YEARLY ->
                                                    stringResource(R.string.recurrence_yearly)
                                            },
                                        )
                                    },
                                    enabled = !uiState.isSubmitting,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SplitEaseColors.Primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = SplitEaseColors.Surface,
                                            labelColor = SplitEaseColors.Navy,
                                        ),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.label_split_type))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SplitType.entries.chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { type ->
                                FilterChip(
                                    selected = splitType == type.name,
                                    onClick = { splitType = type.name },
                                    label = {
                                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                                    },
                                    enabled = !uiState.isSubmitting,
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SplitEaseColors.Primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = SplitEaseColors.Surface,
                                            labelColor = SplitEaseColors.Navy,
                                        ),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.label_participants))
                participants.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isSubmitting) {
                                    selected =
                                        if (selected.contains(option.userId)) {
                                            selected - option.userId
                                        } else {
                                            selected + option.userId
                                        }
                                }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected.contains(option.userId),
                            onCheckedChange = { checked ->
                                selected =
                                    if (checked) selected + option.userId else selected - option.userId
                            },
                            enabled = !uiState.isSubmitting,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.label)
                            if (option.isPendingInvite) {
                                Text(
                                    stringResource(R.string.invite_pending_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SplitEaseColors.Primary,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SeSectionHeader(text = stringResource(R.string.label_paid_by))
                participants.filter { it.userId in selected }.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { paidBy = option.userId }
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (paidBy == option.userId) "● ${option.label}" else "○ ${option.label}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                val mode = runCatching { SplitType.valueOf(splitType) }.getOrDefault(SplitType.EQUAL)
                val recurrenceMode =
                    runCatching { RecurrenceFrequency.valueOf(recurrence) }
                        .getOrDefault(RecurrenceFrequency.NONE)
                if (mode == SplitType.UNEQUAL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeSectionHeader(text = stringResource(R.string.label_unequal_amounts))
                    selected.forEach { id ->
                        val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                        SeTextField(
                            value = unequalTexts[id].orEmpty(),
                            onValueChange = { unequalTexts = unequalTexts + (id to it) },
                            label = label,
                            modifier = Modifier.padding(vertical = 4.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                if (mode == SplitType.PERCENTAGE) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeSectionHeader(text = stringResource(R.string.label_percentages))
                    selected.forEach { id ->
                        val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                        SeTextField(
                            value = percentTexts[id].orEmpty(),
                            onValueChange = { percentTexts = percentTexts + (id to it) },
                            label = "$label %",
                            modifier = Modifier.padding(vertical = 4.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                if (mode == SplitType.SHARES) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeSectionHeader(text = stringResource(R.string.label_shares))
                    selected.forEach { id ->
                        val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                        SeTextField(
                            value = shareTexts[id].orEmpty(),
                            onValueChange = { shareTexts = shareTexts + (id to it) },
                            label = label,
                            modifier = Modifier.padding(vertical = 4.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SePrimaryButton(
                    text = stringResource(R.string.action_save_expense),
                    onClick = {
                        val unequal =
                            selected.associateWith { id ->
                                BigDecimal(unequalTexts[id]?.trim().orEmpty().ifBlank { "0" })
                            }
                        val percents =
                            selected.associateWith { id ->
                                BigDecimal(percentTexts[id]?.trim().orEmpty().ifBlank { "0" })
                            }
                        val sharesMap =
                            selected.associateWith { id ->
                                shareTexts[id]?.trim()?.toIntOrNull() ?: 1
                            }
                        viewModel.createExpense(
                            description = description,
                            amountText = amount,
                            paidByUserId = paidBy,
                            participantIds = selected.toList(),
                            splitType = mode,
                            groupId = groupId,
                            unequalAmounts = if (mode == SplitType.UNEQUAL) unequal else emptyMap(),
                            percentages = if (mode == SplitType.PERCENTAGE) percents else emptyMap(),
                            shares = if (mode == SplitType.SHARES) sharesMap else emptyMap(),
                            recurrenceFrequency = recurrenceMode,
                            categoryId = categoryId,
                            onSuccess = onDone,
                        )
                    },
                    enabled =
                        !uiState.isSubmitting &&
                            description.isNotBlank() &&
                            amount.isNotBlank() &&
                            selected.isNotEmpty(),
                )
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
            }
        },
    )
}

@Composable
fun ExpenseListSection(
    expenses: List<Expense>,
    emptyText: String,
    categoryNames: Map<String, String> = emptyMap(),
) {
    if (expenses.isEmpty()) {
        SeEmptyState(message = emptyText)
    } else {
        expenses.forEach { expense ->
            val categoryLabel =
                expense.categoryId?.let { categoryNames[it] }?.let { " · $it" }.orEmpty()
            SeListRow(
                title = expense.description,
                subtitle =
                    "${expense.currencyCode} ${expense.amount.toPlainString()} · ${expense.splitType.name.lowercase()}$categoryLabel",
            )
        }
    }
}
