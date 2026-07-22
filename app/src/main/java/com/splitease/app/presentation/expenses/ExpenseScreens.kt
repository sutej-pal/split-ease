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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.splitease.app.domain.model.SplitType
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendUserId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL.name) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_add_expense)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.add_expense_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_description)) },
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isSubmitting,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.label_split_type), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
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
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.label_participants), style = MaterialTheme.typography.titleSmall)
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
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.label_paid_by), style = MaterialTheme.typography.titleSmall)
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
                    )
                }
            }

            val mode = runCatching { SplitType.valueOf(splitType) }.getOrDefault(SplitType.EQUAL)
            if (mode == SplitType.UNEQUAL) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.label_unequal_amounts), style = MaterialTheme.typography.titleSmall)
                selected.forEach { id ->
                    val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                    OutlinedTextField(
                        value = unequalTexts[id].orEmpty(),
                        onValueChange = { unequalTexts = unequalTexts + (id to it) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            if (mode == SplitType.PERCENTAGE) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.label_percentages), style = MaterialTheme.typography.titleSmall)
                selected.forEach { id ->
                    val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                    OutlinedTextField(
                        value = percentTexts[id].orEmpty(),
                        onValueChange = { percentTexts = percentTexts + (id to it) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        label = { Text("$label %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            if (mode == SplitType.SHARES) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.label_shares), style = MaterialTheme.typography.titleSmall)
                selected.forEach { id ->
                    val label = participants.firstOrNull { it.userId == id }?.label ?: id.take(8)
                    OutlinedTextField(
                        value = shareTexts[id].orEmpty(),
                        onValueChange = { shareTexts = shareTexts + (id to it) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
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
                        onSuccess = onDone,
                    )
                },
                enabled = !uiState.isSubmitting && description.isNotBlank() && amount.isNotBlank() && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save_expense))
            }
            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ExpenseListSection(
    expenses: List<Expense>,
    emptyText: String,
) {
    if (expenses.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    } else {
        expenses.forEach { expense ->
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(expense.description, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${expense.currencyCode} ${expense.amount.toPlainString()} · ${expense.splitType.name.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            HorizontalDivider()
        }
    }
}
