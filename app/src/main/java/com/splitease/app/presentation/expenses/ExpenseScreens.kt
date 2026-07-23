package com.splitease.app.presentation.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextField
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendUserId: String?,
    expenseId: String? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onEditGroupMembers: (() -> Unit)? = null,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val editingExpense by
        viewModel.observeExpenseDetail(expenseId.orEmpty()).collectAsStateWithLifecycle()
    val isEdit = !expenseId.isNullOrBlank()
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL.name) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var paidBy by rememberSaveable { mutableStateOf("") }
    var participants by remember { mutableStateOf<List<ParticipantOption>>(emptyList()) }
    var unequalTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var percentTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var shareTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var membersConfirmed by rememberSaveable { mutableStateOf(isEdit) }
    // Only show the members confirm dialog for empty groups (no expenses/payments yet).
    var membersDialogEligible by rememberSaveable { mutableStateOf(false) }
    var membersEligibilityChecked by remember { mutableStateOf(groupId == null || isEdit) }
    // Dismiss dialog window first, then leave the screen (avoids flash of previous route
    // under a still-visible Dialog).
    var exitAfterMembersDialog by remember { mutableStateOf(false) }
    var expenseDateMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showPaidByPicker by rememberSaveable { mutableStateOf(false) }
    var showSplitPicker by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val me by viewModel.signedInUserId.collectAsStateWithLifecycle()

    LaunchedEffect(groupId, isEdit) {
        if (groupId == null || isEdit) {
            membersDialogEligible = false
            membersEligibilityChecked = true
            return@LaunchedEffect
        }
        val hasEntries = viewModel.groupHasLedgerEntries(groupId)
        membersDialogEligible = !hasEntries
        if (hasEntries) membersConfirmed = true
        membersEligibilityChecked = true
    }

    LaunchedEffect(exitAfterMembersDialog) {
        if (exitAfterMembersDialog) onBack()
    }

    LaunchedEffect(groupId, friendUserId, me, editingExpense) {
        val userId = me ?: return@LaunchedEffect
        val existing = editingExpense?.expense
        participants =
            when {
                (groupId ?: existing?.groupId) != null ->
                    viewModel.resolveGroupParticipantOptions(groupId ?: existing!!.groupId!!)
                friendUserId != null -> viewModel.resolveFriendParticipantOptions(friendUserId)
                existing != null ->
                    editingExpense!!.splits.map {
                        ParticipantOption(it.userId, it.participantLabel)
                    }
                else -> emptyList()
            }
        if (!isEdit || !prefilled) {
            if (!isEdit) {
                paidBy = userId
                selected = participants.map { it.userId }.toSet()
            }
        }
    }

    LaunchedEffect(editingExpense, participants) {
        val detail = editingExpense ?: return@LaunchedEffect
        if (prefilled || participants.isEmpty()) return@LaunchedEffect
        val expense = detail.expense
        title = expense.description
        amount = expense.amount.toPlainString()
        notes = expense.notes.orEmpty()
        splitType = expense.splitType.name
        paidBy = expense.paidByUserId
        selected = detail.splits.map { it.userId }.toSet()
        expenseDateMs = expense.expenseDateEpochMs
        unequalTexts = detail.splits.associate { it.userId to it.owedAmount.toPlainString() }
        prefilled = true
    }

    LaunchedEffect(groupId, membersConfirmed, membersDialogEligible, lifecycleOwner) {
        if (groupId == null || membersConfirmed || !membersDialogEligible) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val refreshed = viewModel.resolveGroupParticipantOptions(groupId)
            participants = refreshed
            selected = refreshed.map { it.userId }.toSet()
            if (paidBy.isBlank()) paidBy = viewModel.currentUserId().orEmpty()
        }
    }

    val showMembersDialog =
        groupId != null &&
            membersEligibilityChecked &&
            membersDialogEligible &&
            !membersConfirmed &&
            !exitAfterMembersDialog

    if (showMembersDialog) {
        GroupExpenseMembersConfirmDialog(
            memberCount = participants.size,
            onStartAddingExpenses = { membersConfirmed = true },
            onEditGroupMembers = { onEditGroupMembers?.invoke() },
            onDismiss = { exitAfterMembersDialog = true },
        )
    }

    // Hide the form while the members gate applies (or while leaving after dismiss) so the
    // Dialog window is removed before navigation — avoids the underlay popping first.
    val blockingOnMembersGate =
        groupId != null &&
            !isEdit &&
            (exitAfterMembersDialog ||
                !membersEligibilityChecked ||
                (membersDialogEligible && !membersConfirmed))
    if (blockingOnMembersGate) return

    val mode = runCatching { SplitType.valueOf(splitType) }.getOrDefault(SplitType.EQUAL)
    val paidByLabel =
        participants.firstOrNull { it.userId == paidBy }?.label
            ?: stringResource(R.string.you_label)
    val splitLabel =
        when (mode) {
            SplitType.EQUAL -> stringResource(R.string.split_equally)
            SplitType.UNEQUAL -> stringResource(R.string.split_unequal)
            SplitType.PERCENTAGE -> stringResource(R.string.split_percentage)
            SplitType.SHARES -> stringResource(R.string.split_shares)
        }
    val others = participants.filter { it.userId != me }
    val allOthersSelected = others.isNotEmpty() && others.all { it.userId in selected }
    val dateTimeLabel =
        remember(expenseDateMs) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(expenseDateMs))
        }

    fun saveExpense() {
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
        if (isEdit && expenseId != null) {
            viewModel.updateExpense(
                expenseId = expenseId,
                description = title,
                amountText = amount,
                paidByUserId = paidBy,
                participantIds = selected.toList(),
                splitType = mode,
                groupId = groupId ?: editingExpense?.expense?.groupId,
                unequalAmounts = if (mode == SplitType.UNEQUAL) unequal else emptyMap(),
                percentages = if (mode == SplitType.PERCENTAGE) percents else emptyMap(),
                shares = if (mode == SplitType.SHARES) sharesMap else emptyMap(),
                categoryId = editingExpense?.expense?.categoryId,
                notes = notes.trim().ifBlank { null },
                expenseDateEpochMs = expenseDateMs,
                onSuccess = onDone,
            )
        } else {
            viewModel.createExpense(
                description = title,
                amountText = amount,
                paidByUserId = paidBy,
                participantIds = selected.toList(),
                splitType = mode,
                groupId = groupId,
                unequalAmounts = if (mode == SplitType.UNEQUAL) unequal else emptyMap(),
                percentages = if (mode == SplitType.PERCENTAGE) percents else emptyMap(),
                shares = if (mode == SplitType.SHARES) sharesMap else emptyMap(),
                recurrenceFrequency = RecurrenceFrequency.NONE,
                categoryId = null,
                notes = notes.trim().ifBlank { null },
                expenseDateEpochMs = expenseDateMs,
                onSuccess = onDone,
            )
        }
    }

    SeScreen(
        title =
            stringResource(
                if (isEdit) R.string.action_edit_expense else R.string.action_add_expense,
            ),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { saveExpense() },
                enabled =
                    !uiState.isSubmitting &&
                        title.isNotBlank() &&
                        amount.isNotBlank() &&
                        selected.isNotEmpty() &&
                        paidBy in selected,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_save_expense),
                    tint = SplitEaseColors.Primary,
                )
            }
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.expense_with_you_and),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (others.isNotEmpty()) {
                            ParticipantChip(
                                label = stringResource(R.string.expense_everyone),
                                selected = allOthersSelected && me in selected,
                                onClick = {
                                    selected =
                                        if (allOthersSelected) {
                                            setOfNotNull(me)
                                        } else {
                                            participants.map { it.userId }.toSet()
                                        }
                                    if (paidBy !in selected) {
                                        paidBy = me.orEmpty()
                                    }
                                },
                            )
                        }
                        others.forEach { option ->
                            ParticipantChip(
                                label = option.label,
                                selected = option.userId in selected,
                                onClick = {
                                    selected =
                                        if (option.userId in selected) {
                                            (selected - option.userId).ifEmpty { setOfNotNull(me) }
                                        } else {
                                            selected + option.userId
                                        }
                                    if (paidBy !in selected) {
                                        paidBy = me.orEmpty()
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                ExpenseUnderlineField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.label_expense_title),
                    icon = Icons.Filled.Receipt,
                    enabled = !uiState.isSubmitting,
                    textStyle =
                        MaterialTheme.typography.titleLarge.copy(
                            color = SplitEaseColors.Navy,
                            fontWeight = FontWeight.Medium,
                        ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExpenseUnderlineField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = stringResource(R.string.label_amount),
                    leadingLabel = currencySymbol(currencyCode),
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle =
                        MaterialTheme.typography.headlineMedium.copy(
                            color = SplitEaseColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExpenseUnderlineField(
                    value = dateTimeLabel,
                    onValueChange = {},
                    placeholder = stringResource(R.string.label_date_time),
                    icon = Icons.Filled.DateRange,
                    enabled = !uiState.isSubmitting,
                    readOnly = true,
                    onClick = { showDatePicker = true },
                    textStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            color = SplitEaseColors.Navy,
                        ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExpenseUnderlineField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = stringResource(R.string.label_notes_optional),
                    icon = Icons.AutoMirrored.Filled.Notes,
                    enabled = !uiState.isSubmitting,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = SplitEaseColors.Navy,
                        ),
                )

                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.expense_paid_by_and_split),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                    ChoicePill(
                        text = paidByLabel,
                        onClick = { showPaidByPicker = true },
                        enabled = !uiState.isSubmitting,
                    )
                    Text(
                        text = stringResource(R.string.expense_and_split),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                    ChoicePill(
                        text = splitLabel,
                        onClick = { showSplitPicker = true },
                        enabled = !uiState.isSubmitting,
                    )
                }

                if (mode == SplitType.UNEQUAL) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                    Spacer(modifier = Modifier.height(16.dp))
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
                    Spacer(modifier = Modifier.height(16.dp))
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

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeErrorText(it)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        },
    )

    if (showDatePicker) {
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis = localDateToUtcMillis(expenseDateMs),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { utcDay ->
                            expenseDateMs = mergeUtcDateWithLocalTime(utcDay, expenseDateMs)
                        }
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = expenseDateMs }
        val timeState =
            rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = false,
            )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        expenseDateMs =
                            applyLocalTime(expenseDateMs, timeState.hour, timeState.minute)
                        showTimePicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            title = { Text(stringResource(R.string.action_pick_time)) },
            text = { TimePicker(state = timeState) },
        )
    }

    if (showPaidByPicker) {
        SeModal(onDismissRequest = { showPaidByPicker = false }) {
            SeModalTitle(stringResource(R.string.label_paid_by))
            Spacer(modifier = Modifier.height(12.dp))
            participants.filter { it.userId in selected }.forEach { option ->
                Text(
                    text = option.label,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                paidBy = option.userId
                                showPaidByPicker = false
                            }
                            .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (option.userId == paidBy) {
                            SplitEaseColors.Primary
                        } else {
                            SplitEaseColors.Navy
                        },
                    fontWeight =
                        if (option.userId == paidBy) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }

    if (showSplitPicker) {
        SeModal(onDismissRequest = { showSplitPicker = false }) {
            SeModalTitle(stringResource(R.string.label_split_type))
            Spacer(modifier = Modifier.height(12.dp))
            SplitType.entries.forEach { type ->
                val label =
                    when (type) {
                        SplitType.EQUAL -> stringResource(R.string.split_equally)
                        SplitType.UNEQUAL -> stringResource(R.string.split_unequal)
                        SplitType.PERCENTAGE -> stringResource(R.string.split_percentage)
                        SplitType.SHARES -> stringResource(R.string.split_shares)
                    }
                Text(
                    text = label.replaceFirstChar { it.uppercase() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitType = type.name
                                showSplitPicker = false
                            }
                            .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (type.name == splitType) {
                            SplitEaseColors.Primary
                        } else {
                            SplitEaseColors.Navy
                        },
                    fontWeight =
                        if (type.name == splitType) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ParticipantChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(20.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = SplitEaseColors.PrimarySoft,
                selectedLabelColor = SplitEaseColors.PrimaryDark,
                containerColor = SplitEaseColors.Surface,
                labelColor = SplitEaseColors.Navy,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = SplitEaseColors.OutlineStrong,
                selectedBorderColor = SplitEaseColors.Primary,
            ),
    )
}

@Composable
private fun ChoicePill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, SplitEaseColors.OutlineStrong, RoundedCornerShape(10.dp))
                .background(SplitEaseColors.Surface)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = SplitEaseColors.Primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ExpenseUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingLabel: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SplitEaseColors.OutlineStrong, RoundedCornerShape(12.dp))
                    .background(SplitEaseColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            when {
                leadingLabel != null ->
                    Text(
                        text = leadingLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SplitEaseColors.Navy,
                    )
                icon != null ->
                    Icon(icon, contentDescription = null, tint = SplitEaseColors.NavyMuted)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = SplitEaseColors.NavyMuted),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && !readOnly,
                    readOnly = readOnly,
                    singleLine = true,
                    textStyle = textStyle,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SplitEaseColors.OutlineStrong)
        }
    }
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

private fun currencySymbol(code: String): String =
    when (code.uppercase()) {
        "INR" -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> code
    }

/** Material DatePicker uses UTC midnight; convert local calendar day to that form. */
private fun localDateToUtcMillis(localEpochMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localEpochMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(Calendar.YEAR, local.get(Calendar.YEAR))
        set(Calendar.MONTH, local.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun mergeUtcDateWithLocalTime(utcDayMillis: Long, currentLocalMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDayMillis }
    val localTime = Calendar.getInstance().apply { timeInMillis = currentLocalMs }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, localTime.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, localTime.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun applyLocalTime(currentLocalMs: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = currentLocalMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
