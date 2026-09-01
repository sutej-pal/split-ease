package com.splitease.app.presentation.expenses

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.domain.category.DefaultCategories
import com.splitease.app.domain.category.ExpenseCategoryMatcher
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.ExchangeRateSource
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.ads.AdConfig
import com.splitease.app.presentation.ads.SeBannerAd
import com.splitease.app.presentation.ads.SeBannerAdSize
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBarActionButton
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val userCurrency by viewModel.currencyCode.collectAsStateWithLifecycle()
    val group by viewModel.observeGroup(groupId.orEmpty()).collectAsStateWithLifecycle(null)
    val targetDefaultCurrency = group?.defaultCurrencyCode ?: userCurrency

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val editingExpense by
        viewModel.observeExpenseDetail(expenseId.orEmpty()).collectAsStateWithLifecycle()
    val fxState by viewModel.exchangeRateState.collectAsStateWithLifecycle()

    val isEdit = !expenseId.isNullOrBlank()
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var selectedCurrencyCode by rememberSaveable { mutableStateOf(AppCurrencies.DEFAULT) }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }

    var notes by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf(DefaultCategories.ALL.first().id) }
    var categoryManuallySet by rememberSaveable { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL.name) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var paidBy by rememberSaveable { mutableStateOf("") }
    var isMultiPayer by rememberSaveable { mutableStateOf(false) }
    var paidAmountTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var participants by remember { mutableStateOf<List<ParticipantOption>>(emptyList()) }
    var unequalTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var percentTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var shareTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var adjustmentTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var membersConfirmed by rememberSaveable { mutableStateOf(isEdit) }
    // Only show the members confirm dialog for empty groups (no expenses/payments yet).
    var membersDialogEligible by rememberSaveable { mutableStateOf(false) }
    var membersEligibilityChecked by remember { mutableStateOf(groupId == null || isEdit) }
    // Dismiss dialog window first, then leave the screen (avoids flash of previous route
    // under a still-visible Dialog).
    var exitAfterMembersDialog by remember { mutableStateOf(false) }
    var expenseDateMs by rememberSaveable(expenseId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var closingAfterSave by remember { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var paidByStep by rememberSaveable { mutableStateOf(PaidByStep.None.name) }
    var showAdjustSplit by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable(expenseId) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val me by viewModel.signedInUserId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureDefaultCategories()
    }

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

    LaunchedEffect(groupId, friendUserId, me, editingExpense, targetDefaultCurrency) {
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
                selectedCurrencyCode = targetDefaultCurrency
            }
        }
    }

    LaunchedEffect(editingExpense) {
        val detail = editingExpense ?: return@LaunchedEffect
        // Apply business date as soon as the expense loads — do not wait on participants,
        // so a quick save cannot overwrite the original added/expense date with "now".
        if (!prefilled) {
            expenseDateMs = detail.expense.expenseDateEpochMs
        }
    }

    LaunchedEffect(editingExpense, participants) {
        val detail = editingExpense ?: return@LaunchedEffect
        if (prefilled || participants.isEmpty()) return@LaunchedEffect
        val expense = detail.expense
        title = expense.description
        amount = expense.amount.toPlainString()
        selectedCurrencyCode = expense.currencyCode
        notes = expense.notes.orEmpty()
        selectedCategoryId = expense.categoryId ?: DefaultCategories.ALL.first().id
        categoryManuallySet = true
        splitType = expense.splitType.name
        paidBy = expense.paidByUserId
        selected = detail.splits.map { it.userId }.toSet()
        expenseDateMs = expense.expenseDateEpochMs
        unequalTexts = detail.splits.associate { it.userId to it.owedAmount.toPlainString() }
        percentTexts =
            detail.splits.associate { split ->
                split.userId to (split.percentage?.toPlainString().orEmpty())
            }
        shareTexts =
            detail.splits.associate { split ->
                split.userId to (split.shares?.toString().orEmpty())
            }
        adjustmentTexts =
            detail.splits.associate { split ->
                split.userId to
                    (split.adjustmentAmount ?: BigDecimal.ZERO.setScale(2)).toPlainString()
            }
        if (detail.splits.any { it.paidAmount != null }) {
            isMultiPayer = true
            paidAmountTexts =
                detail.splits.associate { split ->
                    val paid = split.paidAmount ?: BigDecimal.ZERO
                    split.userId to
                        if (paid.compareTo(BigDecimal.ZERO) == 0) {
                            ""
                        } else {
                            paid.setScale(2).toPlainString()
                        }
                }
        } else {
            isMultiPayer = false
            paidAmountTexts = emptyMap()
        }
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

    val blockingOnMembersGate =
        groupId != null &&
            !isEdit &&
            (
                exitAfterMembersDialog ||
                !membersEligibilityChecked ||
                (membersDialogEligible && !membersConfirmed)
            )
    if (blockingOnMembersGate) return

    val mode = runCatching { SplitType.valueOf(splitType) }.getOrDefault(SplitType.EQUAL)
    val currentPaidByStep =
        runCatching { PaidByStep.valueOf(paidByStep) }.getOrDefault(PaidByStep.None)
    val paidByLabel =
        if (isMultiPayer) {
            stringResource(R.string.expense_multiple_people)
        } else {
            participants.firstOrNull { it.userId == paidBy }?.label
                ?: stringResource(R.string.you_label)
        }
    val splitLabel =
        when (mode) {
            SplitType.EQUAL -> stringResource(R.string.split_equally)
            SplitType.UNEQUAL -> stringResource(R.string.split_unequal)
            SplitType.PERCENTAGE -> stringResource(R.string.split_percentage)
            SplitType.SHARES -> stringResource(R.string.split_shares)
            SplitType.ADJUSTMENT -> stringResource(R.string.split_adjustment)
        }
    var groupName by remember { mutableStateOf<String?>(null) }
    val expenseGroupId = groupId ?: editingExpense?.expense?.groupId
    LaunchedEffect(expenseGroupId) {
        groupName = expenseGroupId?.let { viewModel.getGroupName(it) }
    }
    val others = participants.filter { it.userId != me }
    val allOthersSelected = others.isNotEmpty() && others.all { it.userId in selected }
    val allGroupMembersInExpense =
        expenseGroupId != null &&
            participants.isNotEmpty() &&
            participants.all { it.userId in selected }
    val dateTimeLabel =
        remember(expenseDateMs) {
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(expenseDateMs))
        }
    val selectedCategory =
        categories.firstOrNull { it.id == selectedCategoryId }
            ?: DefaultCategories.byId(selectedCategoryId)?.let { DefaultCategories.toCategory(it) }
            ?: DefaultCategories.toCategory(DefaultCategories.ALL.first())
    val titleCategoryIcon = categoryIcon(selectedCategory.iconKey)
    val pickerCategories =
        remember(categories) {
            val defaults =
                DefaultCategories.ALL.map { definition ->
                    categories.firstOrNull { it.id == definition.id }
                        ?: DefaultCategories.toCategory(definition)
                }
            val customs =
                categories.filter { category ->
                    !DefaultCategories.isStableId(category.id)
                }
            defaults + customs
        }

    val expenseTotal =
        runCatching { BigDecimal(amount.trim().ifBlank { "0" }) }
            .getOrDefault(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
    val multiPayerValid =
        !isMultiPayer ||
            run {
                val sum =
                    paidAmountTexts.keys
                        .fold(BigDecimal.ZERO) { acc, id ->
                            acc.add(
                                runCatching {
                                    BigDecimal(paidAmountTexts[id]?.trim().orEmpty().ifBlank { "0" })
                                }.getOrDefault(BigDecimal.ZERO),
                            )
                        }.setScale(2, RoundingMode.HALF_UP)
                sum.compareTo(expenseTotal) == 0 &&
                    paidAmountTexts.keys.any {
                        runCatching {
                            BigDecimal(paidAmountTexts[it]?.trim().orEmpty().ifBlank { "0" })
                        }.getOrDefault(BigDecimal.ZERO) > BigDecimal.ZERO
                    }
            }
    val amountValid = isValidExpenseAmount(amount)
    val fxRequired = selectedCurrencyCode != targetDefaultCurrency
    val fxValid = !fxRequired || viewModel.isFxReady(selectedCurrencyCode, targetDefaultCurrency)
    val canSaveExpense =
        title.isNotBlank() &&
            amountValid &&
            selected.isNotEmpty() &&
            paidBy.isNotBlank() &&
            multiPayerValid &&
            fxValid
    val titleError = showValidation && title.isBlank()
    val amountError = showValidation && !amountValid
    val participantsError = showValidation && selected.isEmpty()
    val paidByError = showValidation && paidBy.isBlank()
    val paidAmountsError = showValidation && isMultiPayer && !multiPayerValid
    val participantsFocus = remember { BringIntoViewRequester() }
    val titleFocus = remember { BringIntoViewRequester() }
    val amountFocus = remember { BringIntoViewRequester() }
    val paidByFocus = remember { BringIntoViewRequester() }

    LaunchedEffect(
        showValidation,
        titleError,
        amountError,
        participantsError,
        paidByError,
        paidAmountsError,
    ) {
        if (!showValidation) return@LaunchedEffect
        when {
            participantsError -> participantsFocus.bringIntoView()
            titleError -> titleFocus.bringIntoView()
            amountError -> amountFocus.bringIntoView()
            paidByError || paidAmountsError -> paidByFocus.bringIntoView()
        }
    }

    fun saveExpense() {
        if (uiState.isSubmitting || closingAfterSave) return
        showValidation = true
        if (!canSaveExpense) return
        val multiPaidAmounts =
            if (isMultiPayer) {
                paidAmountTexts.keys.associateWith { id ->
                    BigDecimal(paidAmountTexts[id]?.trim().orEmpty().ifBlank { "0" })
                }
            } else {
                emptyMap()
            }
        val unequal =
            if (mode == SplitType.UNEQUAL) {
                selected.associateWith { id ->
                    BigDecimal(unequalTexts[id]?.trim().orEmpty().ifBlank { "0" })
                }
            } else {
                emptyMap()
            }
        val percents =
            if (mode == SplitType.PERCENTAGE) {
                selected.associateWith { id ->
                    BigDecimal(percentTexts[id]?.trim().orEmpty().ifBlank { "0" })
                }
            } else {
                emptyMap()
            }
        val sharesMap =
            if (mode == SplitType.SHARES) {
                selected.associateWith { id ->
                    shareTexts[id]?.trim()?.toIntOrNull() ?: 0
                }
            } else {
                emptyMap()
            }
        val adjustmentsMap =
            if (mode == SplitType.ADJUSTMENT) {
                selected.associateWith { id ->
                    BigDecimal(adjustmentTexts[id]?.trim().orEmpty().ifBlank { "0" })
                }
            } else {
                emptyMap()
            }
        if (isEdit) {
            viewModel.updateExpense(
                expenseId = expenseId,
                description = title,
                amountText = amount,
                paidByUserId = paidBy,
                participantIds = selected.toList(),
                splitType = mode,
                groupId = groupId ?: editingExpense?.expense?.groupId,
                unequalAmounts = unequal,
                percentages = percents,
                shares = sharesMap,
                adjustments = adjustmentsMap,
                paidAmounts = multiPaidAmounts,
                categoryId = selectedCategoryId,
                notes = notes.trim().ifBlank { null },
                expenseDateEpochMs = expenseDateMs,
                onSuccess = onDone,
            )
        } else {
            closingAfterSave = true
            onDone()
            viewModel.createExpenseInBackground(
                description = title,
                amountText = amount,
                currencyCode = selectedCurrencyCode,
                targetDefaultCurrency = targetDefaultCurrency,
                paidByUserId = paidBy,
                participantIds = selected.toList(),
                splitType = mode,
                groupId = groupId,
                unequalAmounts = unequal,
                percentages = percents,
                shares = sharesMap,
                adjustments = adjustmentsMap,
                paidAmounts = multiPaidAmounts,
                recurrenceFrequency = RecurrenceFrequency.NONE,
                categoryId = selectedCategoryId,
                notes = notes.trim().ifBlank { null },
                expenseDateEpochMs = expenseDateMs,
            )
        }
    }

    LaunchedEffect(selectedCurrencyCode, targetDefaultCurrency) {
        if (selectedCurrencyCode != targetDefaultCurrency) {
            viewModel.fetchExchangeRate(selectedCurrencyCode, targetDefaultCurrency)
        } else {
            viewModel.resetExchangeRate()
        }
    }

    when (currentPaidByStep) {
        PaidByStep.WhoPaid -> {
            WhoPaidScreen(
                participants = participants,
                selectedUserId = paidBy,
                isMultiplePeople = isMultiPayer,
                onBack = { paidByStep = PaidByStep.None.name },
                onSelectPerson = { userId ->
                    paidBy = userId
                    isMultiPayer = false
                    paidAmountTexts = emptyMap()
                    paidByStep = PaidByStep.None.name
                },
                onMultiplePeople = { paidByStep = PaidByStep.EnterAmounts.name },
            )
            return
        }
        PaidByStep.EnterAmounts -> {
            EnterPaidAmountsScreen(
                participants = participants,
                currencyCode = selectedCurrencyCode,
                totalAmount = expenseTotal,
                initialAmounts = paidAmountTexts,
                onBack = { paidByStep = PaidByStep.WhoPaid.name },
                onConfirm = { amounts ->
                    paidAmountTexts = amounts.mapValues { it.value.toPlainString() }
                    isMultiPayer = true
                    paidBy =
                        amounts
                            .filter { it.value > BigDecimal.ZERO }
                            .maxByOrNull { it.value }
                            ?.key
                            ?: paidBy
                    paidByStep = PaidByStep.None.name
                },
            )
            return
        }
        PaidByStep.None -> Unit
    }

    if (showAdjustSplit) {
        AdjustSplitScreen(
            participants = participants,
            currencyCode = selectedCurrencyCode,
            totalAmount = expenseTotal,
            initialSplitType = mode,
            initialSelectedIds = selected,
            initialUnequalTexts = unequalTexts,
            initialPercentTexts = percentTexts,
            initialShareTexts = shareTexts,
            initialAdjustmentTexts = adjustmentTexts,
            onBack = { showAdjustSplit = false },
            onConfirm = { result ->
                splitType = result.splitType.name
                selected = result.selectedIds
                unequalTexts = result.unequalTexts
                percentTexts = result.percentTexts
                shareTexts = result.shareTexts
                adjustmentTexts = result.adjustmentTexts
                if (paidBy.isBlank()) {
                    paidBy = me.orEmpty()
                }
                showAdjustSplit = false
            },
        )
        return
    }

    SeScreen(
        title =
            stringResource(
                if (isEdit) R.string.action_edit_expense else R.string.action_add_expense,
            ),
        onBack = onBack,
        actions = {
            SeTopBarActionButton(
                onClick = { saveExpense() },
                enabled = !uiState.isSubmitting,
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
                        .padding(padding.values),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(participantsFocus),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.expense_with_you_and),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (groupId != null && groupName != null && !isEdit) {
                        ParticipantChip(
                            label = stringResource(R.string.expense_all_of_group, groupName!!),
                            selected = true,
                            onClick = {},
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isEdit && allGroupMembersInExpense) {
                                ParticipantChip(
                                    label = stringResource(R.string.expense_everyone),
                                    selected = true,
                                    onClick = {},
                                )
                            } else {
                                // Select-all only when there are multiple other people.
                                // A 1:1 friend expense would otherwise show a redundant "Everyone".
                                if (!isEdit && others.size > 1) {
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
                                            if (paidBy.isBlank()) {
                                                paidBy = me.orEmpty()
                                            }
                                        },
                                    )
                                }
                                val membersToShow =
                                    if (isEdit) {
                                        others.filter { it.userId in selected }
                                    } else {
                                        others
                                    }
                                membersToShow.forEach { option ->
                                    ParticipantChip(
                                        label = option.label,
                                        selected = option.userId in selected,
                                        onClick = {
                                            selected =
                                                if (option.userId in selected) {
                                                    (selected - option.userId).ifEmpty {
                                                        setOfNotNull(me)
                                                    }
                                                } else {
                                                    selected + option.userId
                                                }
                                            if (paidBy.isBlank()) {
                                                paidBy = me.orEmpty()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (participantsError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SeErrorText(stringResource(R.string.msg_expense_participants_required))
                }

                Spacer(modifier = Modifier.height(28.dp))
                ExpenseUnderlineField(
                    value = title,
                    onValueChange = { newTitle ->
                        title = newTitle
                        if (!categoryManuallySet) {
                            selectedCategoryId = ExpenseCategoryMatcher.matchCategoryId(newTitle)
                        }
                    },
                    placeholder = stringResource(R.string.label_expense_title),
                    icon = titleCategoryIcon,
                    onIconClick = { showCategoryPicker = true },
                    iconContentDescription = stringResource(R.string.cd_expense_category),
                    enabled = !uiState.isSubmitting,
                    isError = titleError,
                    errorText =
                        if (titleError) {
                            stringResource(R.string.msg_expense_title_required)
                        } else {
                            null
                        },
                    modifier = Modifier.bringIntoViewRequester(titleFocus),
                    textStyle =
                        MaterialTheme.typography.titleLarge.copy(
                            color = SplitEaseColors.Navy,
                            fontWeight = FontWeight.Medium,
                        ),
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExpenseUnderlineField(
                    value = amount,
                    onValueChange = { raw ->
                        val filtered = raw.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split(".")
                        amount = when {
                            parts.size <= 1 -> filtered
                            else -> parts[0] + "." + parts[1].take(2)
                        }
                    },
                    placeholder = "0.00",
                    leadingLabel = currencySymbol(selectedCurrencyCode),
                    onIconClick = if (isEdit) null else { { showCurrencyPicker = true } },
                    iconBoxSize = 56.dp,
                    leadingTextStyle =
                        MaterialTheme.typography.headlineMedium.copy(
                            color = SplitEaseColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    errorText =
                        if (amountError) {
                            stringResource(R.string.msg_enter_valid_amount)
                        } else {
                            null
                        },
                    modifier = Modifier.bringIntoViewRequester(amountFocus),
                    textStyle =
                        MaterialTheme.typography.headlineMedium.copy(
                            color = SplitEaseColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
                
                ExchangeRateRow(
                    fxState = fxState,
                    onRateChange = { viewModel.setManualExchangeRate(it) }
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
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = SplitEaseColors.Navy,
                        ),
                )

                Spacer(modifier = Modifier.height(28.dp))
                PaidByAndSplitRow(
                    paidByLabel = paidByLabel,
                    splitLabel = splitLabel,
                    onPaidByClick = { paidByStep = PaidByStep.WhoPaid.name },
                    onSplitClick = { showAdjustSplit = true },
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.bringIntoViewRequester(paidByFocus),
                )
                if (paidByError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SeErrorText(stringResource(R.string.msg_expense_paid_by_required))
                } else if (paidAmountsError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SeErrorText(stringResource(R.string.msg_expense_paid_amounts_mismatch))
                }

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeErrorText(it)
                }
                    Spacer(modifier = Modifier.height(24.dp))
                    }
                    SeBannerAd(
                        adUnitId = AdConfig.addExpenseBannerUnitId,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        horizontalPadding = 20.dp,
                        size = SeBannerAdSize.Inline(),
                        showBottomDivider = false,
                    )
                }
        },
    )

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selected = selectedCurrencyCode,
            onSelect = { selectedCurrencyCode = it },
            onDismiss = { showCurrencyPicker = false }
        )
    }

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
        SeModal(
            onDismissRequest = { showTimePicker = false },
            title = stringResource(R.string.action_pick_time),
            icon = Icons.Filled.AccessTime,
            dismissLabel = stringResource(R.string.action_close),
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = {
                expenseDateMs =
                    applyLocalTime(expenseDateMs, timeState.hour, timeState.minute)
                showTimePicker = false
            },
        ) {
            TimePicker(state = timeState)
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = pickerCategories,
            selectedCategoryId = selectedCategoryId,
            onDismiss = { showCategoryPicker = false },
            onSelect = { id ->
                selectedCategoryId = id
                categoryManuallySet = true
                showCategoryPicker = false
            },
        )
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
                borderColor = SplitEaseColors.Outline,
                selectedBorderColor = SplitEaseColors.Primary,
            ),
    )
}

@Composable
private fun PaidByAndSplitRow(
    paidByLabel: String,
    splitLabel: String,
    onPaidByClick: () -> Unit,
    onSplitClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PaidBySplitPhrase(
            lead = stringResource(R.string.expense_paid_by_and_split),
            pillText = paidByLabel,
            onPillClick = onPaidByClick,
            enabled = enabled,
        )
        PaidBySplitPhrase(
            lead = stringResource(R.string.expense_and_split),
            pillText = splitLabel,
            onPillClick = onSplitClick,
            enabled = enabled,
        )
    }
}

@Composable
private fun PaidBySplitPhrase(
    lead: String,
    pillText: String,
    onPillClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = lead,
            style = MaterialTheme.typography.bodyLarge,
            color = SplitEaseColors.Navy,
            maxLines = 1,
        )
        ChoicePill(
            text = pillText,
            onClick = onPillClick,
            enabled = enabled,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun ChoicePill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(10.dp))
                .background(SplitEaseColors.Surface)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = SplitEaseColors.Primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
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
    iconBoxSize: Dp = 48.dp,
    leadingTextStyle: TextStyle? = null,
    onIconClick: (() -> Unit)? = null,
    iconContentDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
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
        verticalAlignment =
            if (singleLine) {
                Alignment.CenterVertically
            } else {
                Alignment.Top
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(iconBoxSize)
                    .background(SplitEaseColors.SurfaceMuted, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitEaseColors.OutlineStrong, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (onIconClick != null) {
                            Modifier.clickable(enabled = enabled, onClick = onIconClick)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                leadingLabel != null ->
                    Text(
                        text = leadingLabel,
                        style =
                            leadingTextStyle
                                ?: MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SplitEaseColors.Navy,
                    )
                icon != null ->
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = SplitEaseColors.NavyMuted,
                        modifier = Modifier.size(26.dp),
                    )
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
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else minLines,
                    maxLines = if (singleLine) 1 else maxLines,
                    textStyle = textStyle,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        SplitEaseColors.OutlineStrong
                    },
            )
            if (errorText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    SeModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.action_pick_category),
        icon = Icons.Filled.Category,
        body = stringResource(R.string.pick_category_body),
        dismissLabel = stringResource(R.string.action_close),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                val selected = category.id == selectedCategoryId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = categoryIcon(category.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
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
                            borderColor = SplitEaseColors.Outline,
                            selectedBorderColor = SplitEaseColors.Primary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ExchangeRateRow(
    fxState: ExchangeRateUiState,
    onRateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fxState.fromCurrency == null || fxState.toCurrency == null || fxState.fromCurrency == fxState.toCurrency) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(SplitEaseColors.SurfaceMuted, RoundedCornerShape(12.dp))
            .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.label_exchange_rate),
                style = MaterialTheme.typography.labelMedium,
                color = SplitEaseColors.NavyMuted
            )
            if (fxState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (fxState.source == ExchangeRateSource.LIVE)
                        stringResource(R.string.rate_source_live)
                    else
                        stringResource(R.string.rate_source_custom),
                    style = MaterialTheme.typography.labelSmall,
                    color = SplitEaseColors.Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (fxState.fetchError != null) {
            Text(
                text = fxState.fetchError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "1 ${fxState.fromCurrency} =",
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy
            )
            Spacer(modifier = Modifier.width(8.dp))
            val underlineColor = SplitEaseColors.Primary
            BasicTextField(
                value = fxState.manualRateText,
                onValueChange = onRateChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = SplitEaseColors.Navy,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(underlineColor),
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 60.dp)
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val y = this.size.height - strokeWidth / 2
                        drawLine(
                            color = underlineColor,
                            start = Offset(0f, y),
                            end = Offset(this.size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fxState.toCurrency,
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy
            )
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val options = remember(filter) { AppCurrencies.filter(filter) }
    SeModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_currency_item),
        icon = Icons.Filled.Language,
    ) {
        SeTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = stringResource(R.string.label_search_currencies),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        Box(modifier = Modifier.heightIn(max = 300.dp)) {
            LazyColumn {
                items(options) { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (code == selected) SplitEaseColors.Primary else SplitEaseColors.Navy,
                            modifier = Modifier.width(50.dp)
                        )
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SplitEaseColors.NavyMuted
                        )
                    }
                }
            }
        }
    }
}

private fun isValidExpenseAmount(amount: String): Boolean {
    val parsed = runCatching { BigDecimal(amount.trim()) }.getOrNull() ?: return false
    return parsed > BigDecimal.ZERO
}

private fun currencySymbol(code: String): String =
    when (AppCurrencies.normalizeOrDefault(code)) {
        AppCurrencies.INR -> "₹"
        AppCurrencies.USD -> "$"
        else -> code
    }

/** Material DatePicker uses UTC midnight; convert local calendar day to that form. */
private fun localDateToUtcMillis(localEpochMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localEpochMs }
    return Calendar
        .getInstance(TimeZone.getTimeZone("UTC"))
        .apply {
        clear()
        set(Calendar.YEAR, local.get(Calendar.YEAR))
        set(Calendar.MONTH, local.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun mergeUtcDateWithLocalTime(utcDayMillis: Long, currentLocalMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDayMillis }
    val localTime = Calendar.getInstance().apply { timeInMillis = currentLocalMs }
    return Calendar
        .getInstance()
        .apply {
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
    Calendar
        .getInstance()
        .apply {
        timeInMillis = currentLocalMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private enum class PaidByStep {
    None,
    WhoPaid,
    EnterAmounts,
}

/**
 * Preview of the add-expense form. Uses local sample state because [AddExpenseScreen]
 * requires a Hilt [ExpensesViewModel].
 */
@Preview(name = "Add expense", showBackground = true, heightDp = 780)
@Composable
private fun AddExpenseScreenPreview() {
    SePreview {
        var title by remember { mutableStateOf("Dinner") }
        var amount by remember { mutableStateOf("1240.00") }
        var notes by remember { mutableStateOf("") }
        val currencyCode = AppCurrencies.INR
        val dateTimeLabel =
            remember {
                DateFormat
                    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date())
            }

        SeScreen(
            title = stringResource(R.string.action_add_expense),
            onBack = {},
            actions = {
                SeTopBarActionButton(onClick = {}) {
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
                            .padding(padding.values),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
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
                            ParticipantChip(
                                label = stringResource(R.string.expense_all_of_group, "Noida Room"),
                                selected = true,
                                onClick = {},
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        ExpenseUnderlineField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = stringResource(R.string.label_expense_title),
                            icon = categoryIcon("category_food"),
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
                            placeholder = "0.00",
                            leadingLabel = currencySymbol(currencyCode),
                            iconBoxSize = 56.dp,
                            leadingTextStyle =
                                MaterialTheme.typography.headlineMedium.copy(
                                    color = SplitEaseColors.Navy,
                                    fontWeight = FontWeight.SemiBold,
                                ),
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
                            readOnly = true,
                            onClick = {},
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
                            singleLine = false,
                            minLines = 3,
                            maxLines = 6,
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = SplitEaseColors.Navy,
                                ),
                        )

                        Spacer(modifier = Modifier.height(28.dp))
                        PaidByAndSplitRow(
                            paidByLabel = stringResource(R.string.you_label),
                            splitLabel = stringResource(R.string.split_equally),
                            onPaidByClick = {},
                            onSplitClick = {},
                            enabled = true,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    SeBannerAd(
                        adUnitId = AdConfig.addExpenseBannerUnitId,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        horizontalPadding = 20.dp,
                        size = SeBannerAdSize.Inline(),
                        showBottomDivider = false,
                    )
                }
            },
        )
    }
}
