package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.payment.PaymentDeepLinks
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.math.BigDecimal

/**
 * Group settle-up / balances screen. Back returns to group detail.
 *
 * Lists each member with a non-zero net; expanding a row shows who owes whom
 * with separate Remind and Settle up actions.
 */
@Composable
fun GroupBalancesScreen(
    groupId: String,
    onBack: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
    onRemindViaApp: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        fromLabel: String,
        toLabel: String,
        groupName: String?,
    ) -> Unit,
    groupsViewModel: GroupsViewModel = hiltViewModel(),
    expensesViewModel: ExpensesViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
) {
    val expensesUi by expensesViewModel.uiState.collectAsStateWithLifecycle()
    val groupsUi by groupsViewModel.uiState.collectAsStateWithLifecycle()
    val group by remember(groupId) { groupsViewModel.observeGroup(groupId) }
        .collectAsStateWithLifecycle()
    val groupBalance by remember(groupId) { balancesViewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    val me = expensesViewModel.currentUserId()
    val myDisplayName by expensesViewModel.currentUserDisplayName.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currencyFallback =
        group?.defaultCurrencyCode?.takeIf { it.isNotBlank() } ?: AppCurrencies.DEFAULT
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.cd_share_remind)
    val youLabel = stringResource(R.string.you_label)

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            kotlinx.coroutines.yield()
            expensesViewModel.refreshGroupFromCloud(groupId)
        }
    }

    fun reminderBody(debt: LabeledDebt): String {
        val payeeLabel =
            when {
                debt.toLabel.equals(youLabel, ignoreCase = true) ||
                    debt.toLabel.equals("You", ignoreCase = true) ->
                    myDisplayName?.takeIf { it.isNotBlank() } ?: debt.toLabel
                else -> debt.toLabel
            }
        return PaymentDeepLinks.shareText(
            amount = debt.amount,
            currencyCode = debt.currencyCode,
            counterpartyLabel = payeeLabel,
        )
    }

    fun shareReminder(debt: LabeledDebt) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, reminderBody(debt))
            }
        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
    }

    GroupBalancesContent(
        groupBalance = groupBalance,
        currencyFallback = currencyFallback,
        currentUserId = me,
        errorMessage = expensesUi.errorMessage ?: groupsUi.errorMessage,
        infoMessage = expensesUi.infoMessage ?: groupsUi.infoMessage,
        onBack = onBack,
        onEmailRemind = { debt ->
            onRemindViaApp(
                debt.fromUserId,
                debt.toUserId,
                debt.amount.toPlainString(),
                debt.currencyCode,
                debt.fromLabel,
                debt.toLabel,
                groupBalance?.groupName ?: group?.name,
            )
        },
        onShareRemind = ::shareReminder,
        onSettleDebt = onSettleDebt,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupBalancesContent(
    groupBalance: GroupBalanceUi?,
    currencyFallback: String,
    currentUserId: String?,
    errorMessage: String?,
    infoMessage: String?,
    onBack: () -> Unit,
    onEmailRemind: (LabeledDebt) -> Unit,
    onShareRemind: (LabeledDebt) -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
) {
    val nothingToSettle = stringResource(R.string.group_nothing_to_settle)
    val youLabel = stringResource(R.string.you_label)
    val memberRows =
        remember(groupBalance, currentUserId, youLabel, currencyFallback) {
            buildMemberBalanceRows(
                balance = groupBalance,
                currentUserId = currentUserId,
                youLabel = youLabel,
                currencyFallback = currencyFallback,
            )
        }
    var remindDebt by remember { mutableStateOf<LabeledDebt?>(null) }
    val remindSheetState = rememberModalBottomSheetState()

    SeScreen(
        title = stringResource(R.string.balances_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = SeLayout.screenBottom),
            ) {
                when {
                    groupBalance == null -> {
                        Text(
                            text = stringResource(R.string.balances_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .seDetailHorizontal()
                                    .padding(top = SeLayout.sectionGap),
                        )
                    }

                    memberRows.isEmpty() -> {
                        SeInfoText(
                            nothingToSettle,
                            modifier =
                                Modifier
                                    .seDetailHorizontal()
                                    .padding(top = SeLayout.sectionGap),
                        )
                    }

                    else -> {
                        memberRows.forEach { row ->
                            MemberBalanceAccordion(
                                row = row,
                                currentUserId = currentUserId,
                                onRemindDebt = { debt -> remindDebt = debt },
                                onSettleDebt = onSettleDebt,
                            )
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                    SeErrorText(msg, modifier = Modifier.seDetailHorizontal())
                }
                infoMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                    SeInfoText(msg, modifier = Modifier.seDetailHorizontal())
                }
            }
        },
    )

    val pendingRemind = remindDebt
    if (pendingRemind != null) {
        val remindName =
            remindTargetName(
                debt = pendingRemind,
                currentUserId = currentUserId,
                youLabel = youLabel,
            )
        ModalBottomSheet(
            onDismissRequest = { remindDebt = null },
            sheetState = remindSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RemindOptionsSheet(
                personName = remindName,
                onSendViaApp = {
                    val debt = pendingRemind
                    remindDebt = null
                    onEmailRemind(debt)
                },
                onMoreShareOptions = {
                    val debt = pendingRemind
                    remindDebt = null
                    onShareRemind(debt)
                },
            )
        }
    }
}

/** Person who should receive the reminder (debtor, or creditor when you are the debtor). */
private fun remindTargetName(
    debt: LabeledDebt,
    currentUserId: String?,
    youLabel: String,
): String {
    val debtorIsYou =
        (currentUserId != null && debt.fromUserId == currentUserId) ||
            debt.fromLabel.equals(youLabel, ignoreCase = true)
    return if (debtorIsYou) debt.toLabel else debt.fromLabel
}

@Composable
private fun RemindOptionsSheet(
    personName: String,
    onSendViaApp: () -> Unit,
    onMoreShareOptions: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.remind_sheet_title, personName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SplitEaseColors.Navy,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        RemindSheetOption(
            icon = Icons.AutoMirrored.Filled.Reply,
            title = stringResource(R.string.remind_send_via_app),
            subtitle = stringResource(R.string.remind_send_via_app_body),
            onClick = onSendViaApp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        RemindSheetOption(
            icon = Icons.Filled.Share,
            title = stringResource(R.string.remind_more_share),
            subtitle = null,
            onClick = onMoreShareOptions,
        )
    }
}

@Composable
private fun RemindSheetOption(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SplitEaseColors.Navy,
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
    }
}

private data class MemberBalanceRow(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val isCurrentUser: Boolean,
    /** Non-zero nets keyed by currency code (may include multiple currencies). */
    val netsByCurrency: Map<String, BigDecimal>,
    val debts: List<LabeledDebt>,
) {
    /** Largest absolute net — used for sort order and owe/get-back tone. */
    val primaryNet: BigDecimal =
        netsByCurrency.values.maxByOrNull { it.abs() } ?: BigDecimal.ZERO

    val primaryCurrency: String =
        netsByCurrency.entries.maxByOrNull { it.value.abs() }?.key.orEmpty()
}

private fun buildMemberBalanceRows(
    balance: GroupBalanceUi?,
    currentUserId: String?,
    youLabel: String,
    currencyFallback: String,
): List<MemberBalanceRow> {
    if (balance == null) return emptyList()

    val nameById = linkedMapOf<String, String>()
    val photoById = linkedMapOf<String, String?>()
    balance.simplifiedDebts.forEach { debt ->
        nameById.putIfAbsent(debt.fromUserId, debt.fromLabel)
        nameById.putIfAbsent(debt.toUserId, debt.toLabel)
        if (!debt.fromPhotoUrl.isNullOrBlank()) {
            photoById[debt.fromUserId] = debt.fromPhotoUrl
        }
        if (!debt.toPhotoUrl.isNullOrBlank()) {
            photoById[debt.toUserId] = debt.toPhotoUrl
        }
    }

    val userIds =
        (
            balance.memberNetsByCurrency.values.flatMap { it.keys } +
                balance.simplifiedDebts.flatMap { listOf(it.fromUserId, it.toUserId) }
        ).toSet()

    return userIds
        .mapNotNull { userId ->
            val nets =
                balance.memberNetsByCurrency
                    .mapNotNull { (currency, byUser) ->
                        val net = byUser[userId] ?: return@mapNotNull null
                        if (net.compareTo(BigDecimal.ZERO) == 0) null
                        else (currency.ifBlank { currencyFallback }) to net
                    }.toMap()
            if (nets.isEmpty()) return@mapNotNull null

            val isYou = currentUserId != null && userId == currentUserId
            val rawName = nameById[userId].orEmpty()
            val displayName =
                when {
                    isYou -> youLabel
                    rawName.equals(youLabel, ignoreCase = true) -> youLabel
                    else -> rawName.ifBlank { userId.take(8) }
                }
            MemberBalanceRow(
                userId = userId,
                displayName = displayName,
                photoUrl = photoById[userId],
                isCurrentUser = isYou,
                netsByCurrency = nets,
                debts =
                    balance.simplifiedDebts.filter {
                        it.fromUserId == userId || it.toUserId == userId
                    },
            )
        }.sortedWith(
            compareByDescending<MemberBalanceRow> { it.primaryNet.abs() }
                .thenBy { it.displayName.lowercase() },
        )
}

@Composable
private fun MemberBalanceAccordion(
    row: MemberBalanceRow,
    currentUserId: String?,
    onRemindDebt: (LabeledDebt) -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
) {
    var expanded by rememberSaveable(row.userId) { mutableStateOf(false) }
    val canExpand = row.debts.isNotEmpty()
    val money =
        row.netsByCurrency.entries
            .sortedByDescending { it.value.abs() }
            .joinToString(", ") { (currency, net) ->
                MoneyFormat.format(net.abs(), currency.ifBlank { row.primaryCurrency })
            }
    val owes = row.primaryNet < BigDecimal.ZERO
    val accent = if (owes) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
    val headline =
        when {
            row.isCurrentUser && owes ->
                stringResource(R.string.balances_you_owe_total, money)
            row.isCurrentUser && !owes ->
                stringResource(R.string.balances_you_get_back_total, money)
            owes ->
                stringResource(R.string.balances_member_owes_total, row.displayName, money)
            else ->
                stringResource(R.string.balances_member_gets_back_total, row.displayName, money)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(horizontal = SeLayout.detailHorizontal, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeAvatarBadge(
                name = row.displayName,
                photoUrl = row.photoUrl,
                size = 40.dp,
                borderWidth = 0.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text =
                    buildAnnotatedString {
                        val start = headline.indexOf(money)
                        if (start < 0) {
                            append(headline)
                        } else {
                            append(headline.substring(0, start))
                            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                                append(money)
                            }
                            append(headline.substring(start + money.length))
                        }
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (canExpand) {
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = stringResource(R.string.cd_toggle_balance_details),
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        }

        AnimatedVisibility(visible = canExpand && expanded) {
            Column(modifier = Modifier.padding(start = 52.dp, top = 10.dp)) {
                row.debts.forEach { debt ->
                    MemberDebtDetailRow(
                        debt = debt,
                        focusUserId = row.userId,
                        currentUserId = currentUserId,
                        onRemind = { onRemindDebt(debt) },
                        onSettle = {
                            val label =
                                when (currentUserId) {
                                    debt.fromUserId -> debt.toLabel
                                    debt.toUserId -> debt.fromLabel
                                    else ->
                                        if (debt.toUserId == row.userId) {
                                            debt.fromLabel
                                        } else {
                                            debt.toLabel
                                        }
                                }
                            onSettleDebt(
                                debt.fromUserId,
                                debt.toUserId,
                                debt.amount.toPlainString(),
                                debt.currencyCode,
                                label,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun MemberDebtDetailRow(
    debt: LabeledDebt,
    focusUserId: String,
    currentUserId: String?,
    onRemind: () -> Unit,
    onSettle: () -> Unit,
) {
    val counterpartyIsFrom = debt.toUserId == focusUserId
    val otherLabel =
        if (counterpartyIsFrom) debt.fromLabel else debt.toLabel
    val otherPhoto =
        if (counterpartyIsFrom) debt.fromPhotoUrl else debt.toPhotoUrl
    val money = MoneyFormat.format(debt.amount, debt.currencyCode)
    val line =
        stringResource(
            R.string.balances_debt_owes_to,
            debt.fromLabel,
            money,
            debt.toLabel,
        )
    val accent =
        when (currentUserId) {
            debt.fromUserId -> SplitEaseColors.YouOwe
            debt.toUserId -> SplitEaseColors.OwedToYou
            else ->
                if (counterpartyIsFrom) {
                    SplitEaseColors.OwedToYou
                } else {
                    SplitEaseColors.YouOwe
                }
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SeAvatarBadge(
                name = otherLabel.ifBlank { debt.fromLabel },
                photoUrl = otherPhoto,
                size = 28.dp,
                borderWidth = 0.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text =
                    buildAnnotatedString {
                        val start = line.indexOf(money)
                        if (start < 0) {
                            append(line)
                        } else {
                            append(line.substring(0, start))
                            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                                append(money)
                            }
                            append(line.substring(start + money.length))
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.Navy,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 38.dp),
        ) {
            BalanceActionPill(
                label = stringResource(R.string.action_remind),
                onClick = onRemind,
            )
            BalanceActionPill(
                label = stringResource(R.string.action_settle_up),
                onClick = onSettle,
            )
        }
    }
}

@Composable
private fun BalanceActionPill(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = SplitEaseColors.Primary,
            ),
        border = BorderStroke(1.dp, SplitEaseColors.Primary),
        modifier = Modifier.height(36.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(name = "Group balances", showBackground = true, heightDp = 720)
@Composable
private fun GroupBalancesScreenPreview() {
    SePreview {
        GroupBalancesContent(
            groupBalance =
                GroupBalanceUi(
                    groupId = "g1",
                    groupName = "Goa trip",
                    myNetByCurrency = mapOf("INR" to BigDecimal("12961.10")),
                    memberNetsByCurrency =
                        mapOf(
                            "INR" to
                                mapOf(
                                    "u1" to BigDecimal("12961.10"),
                                    "u2" to BigDecimal("-5499.50"),
                                    "u3" to BigDecimal("-7461.60"),
                                ),
                        ),
                    simplifiedDebts =
                        listOf(
                            LabeledDebt(
                                fromUserId = "u2",
                                fromLabel = "Deepak joshi",
                                toUserId = "u1",
                                toLabel = "Laxmikant",
                                amount = BigDecimal("5499.50"),
                                currencyCode = "INR",
                            ),
                            LabeledDebt(
                                fromUserId = "u3",
                                fromLabel = "Sutej",
                                toUserId = "u1",
                                toLabel = "Laxmikant",
                                amount = BigDecimal("7461.60"),
                                currencyCode = "INR",
                            ),
                        ),
                ),
            currencyFallback = "INR",
            currentUserId = "u3",
            errorMessage = null,
            infoMessage = null,
            onBack = {},
            onEmailRemind = {},
            onShareRemind = {},
            onSettleDebt = { _, _, _, _, _ -> },
        )
    }
}

@Preview(name = "Group balances · nothing to settle", showBackground = true, heightDp = 480)
@Composable
private fun GroupBalancesEmptyPreview() {
    SePreview {
        GroupBalancesContent(
            groupBalance =
                GroupBalanceUi(
                    groupId = "g1",
                    groupName = "Goa trip",
                    myNetByCurrency = emptyMap(),
                    memberNetsByCurrency = emptyMap(),
                    simplifiedDebts = emptyList(),
                ),
            currencyFallback = "INR",
            currentUserId = "u1",
            errorMessage = null,
            infoMessage = null,
            onBack = {},
            onEmailRemind = {},
            onShareRemind = {},
            onSettleDebt = { _, _, _, _, _ -> },
        )
    }
}
