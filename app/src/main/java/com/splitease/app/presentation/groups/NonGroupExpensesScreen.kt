package com.splitease.app.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.balances.GroupBalanceHeader
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.expenses.ledgerEntries
import com.splitease.app.presentation.friends.FriendsViewModel
import com.splitease.app.presentation.theme.IndigoLight
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.TextPrimaryLight
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton

private enum class NonGroupDetailPane {
    Expenses,
    Balances,
}

/**
 * Group-detail-style hub for all 1:1 (non-group) expenses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonGroupExpensesScreen(
    onBack: () -> Unit,
    onAddExpenseForFriend: (friendUserId: String) -> Unit,
    onOpenExpense: (expenseId: String) -> Unit,
    onOpenSpending: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
    onAddFriend: () -> Unit,
    expensesViewModel: ExpensesViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val expensesUi by expensesViewModel.uiState.collectAsStateWithLifecycle()
    val ledger by remember { expensesViewModel.observeNonGroupLedger() }
        .collectAsStateWithLifecycle()
    val balance by balancesViewModel.nonGroupBalance.collectAsStateWithLifecycle()
    val friends by friendsViewModel.friends.collectAsStateWithLifecycle()
    var paneName by rememberSaveable { mutableStateOf(NonGroupDetailPane.Expenses.name) }
    val pane =
        runCatching { NonGroupDetailPane.valueOf(paneName) }
            .getOrDefault(NonGroupDetailPane.Expenses)
    var settleHint by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showFriendPicker by remember { mutableStateOf(false) }
    val friendSheetState = rememberModalBottomSheetState()
    val me = expensesViewModel.currentUserId()
    val nothingToSettle = stringResource(R.string.group_nothing_to_settle)
    val lifecycleOwner = LocalLifecycleOwner.current
    val bannerColor = lerp(IndigoLight, TextPrimaryLight, 0.35f)
    val currencyFallback = AppCurrencies.DEFAULT

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshMyExpenses()
        }
    }

    fun openSettle() {
        val debt =
            balance?.simplifiedDebts?.firstOrNull { d ->
                me != null && (d.fromUserId == me || d.toUserId == me)
            }
        if (debt == null || me == null) {
            settleHint = nothingToSettle
            paneName = NonGroupDetailPane.Balances.name
            return
        }
        settleHint = null
        val label = if (me == debt.fromUserId) debt.toLabel else debt.fromLabel
        onSettleDebt(
            debt.fromUserId,
            debt.toUserId,
            debt.amount.toPlainString(),
            debt.currencyCode,
            label,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = {
                    when {
                        friends.size == 1 -> onAddExpenseForFriend(friends.first().friendUserId)
                        else -> showFriendPicker = true
                    }
                },
                icon = Icons.Filled.Receipt,
            )
        },
    ) { padding ->
        SeSystemBars(
            statusBarColor = bannerColor,
            // Keep dark system-nav glyphs on the light content/FAB area at the bottom.
            navigationBarColor = MaterialTheme.colorScheme.background,
            statusBarDarkIcons = false,
            navigationBarDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
        ) {
            NonGroupDetailBanner(
                bannerColor = bannerColor,
                onBack = onBack,
                onOpenInfo = { showInfo = true },
            )

            GroupOverallBalanceBlock(
                balance = balance,
                currencyFallback = currencyFallback,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeActionChip(
                    label = stringResource(R.string.action_settle_up),
                    onClick = { openSettle() },
                    icon = Icons.Filled.Payments,
                )
                SeActionChip(
                    label = stringResource(R.string.group_chip_balances),
                    selected = pane == NonGroupDetailPane.Balances,
                    onClick = { paneName = NonGroupDetailPane.Balances.name },
                    icon = Icons.Filled.AccountBalance,
                )
                SeActionChip(
                    label = stringResource(R.string.group_chip_totals),
                    onClick = onOpenSpending,
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                )
            }

            when (pane) {
                NonGroupDetailPane.Expenses -> {
                    SePullRefreshBox(
                        isRefreshing = expensesUi.isRefreshing,
                        onRefresh = { expensesViewModel.refreshMyExpenses() },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(SplitEaseColors.Surface),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            if (ledger.isEmpty()) {
                                item {
                                    SeEmptyState(
                                        message = stringResource(R.string.non_group_ledger_empty),
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    )
                                }
                            } else {
                                ledgerEntries(ledger, onExpenseClick = onOpenExpense)
                            }
                            expensesUi.errorMessage?.let { msg ->
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SeErrorText(msg, modifier = Modifier.padding(horizontal = 20.dp))
                                }
                            }
                            expensesUi.infoMessage?.let { msg ->
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SeInfoText(msg, modifier = Modifier.padding(horizontal = 20.dp))
                                }
                            }
                            item { Spacer(modifier = Modifier.height(88.dp)) }
                        }
                    }
                }

                NonGroupDetailPane.Balances -> {
                    SePullRefreshBox(
                        isRefreshing = expensesUi.isRefreshing,
                        onRefresh = { expensesViewModel.refreshMyExpenses() },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp)
                                    .padding(bottom = 88.dp),
                        ) {
                            SeSectionHeader(text = stringResource(R.string.balances_title))
                            GroupBalanceHeader(balance = balance)
                            val myDebts =
                                balance
                                    ?.simplifiedDebts
                                    ?.filter { debt ->
                                    me != null && (debt.fromUserId == me || debt.toUserId == me)
                                }.orEmpty()
                            if (myDebts.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SeInfoText(settleHint ?: nothingToSettle)
                            } else {
                                myDebts.forEach { debt ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SeOutlinedButton(
                                        text =
                                            stringResource(
                                                R.string.balances_debt_line,
                                                debt.fromLabel,
                                                debt.toLabel,
                                                "${debt.currencyCode} ${debt.amount.toPlainString()}",
                                            ) + " · " + stringResource(R.string.action_settle_up),
                                        onClick = {
                                            val label =
                                                if (me == debt.fromUserId) {
                                                    debt.toLabel
                                                } else {
                                                    debt.fromLabel
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
                                }
                            }

                            expensesUi.errorMessage?.let {
                                Spacer(modifier = Modifier.height(12.dp))
                                SeErrorText(it)
                            }
                            expensesUi.infoMessage?.let {
                                Spacer(modifier = Modifier.height(12.dp))
                                SeInfoText(it)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfo) {
        SeModal(onDismissRequest = { showInfo = false }) {
            SeModalTitle(text = stringResource(R.string.non_group_expenses))
            SeModalBody(text = stringResource(R.string.non_group_expenses_info))
            Spacer(modifier = Modifier.height(8.dp))
            SeTextButton(
                text = stringResource(R.string.action_done),
                onClick = { showInfo = false },
            )
        }
    }

    if (showFriendPicker) {
        ModalBottomSheet(
            onDismissRequest = { showFriendPicker = false },
            sheetState = friendSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = stringResource(R.string.pick_friend_for_expense),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (friends.isEmpty()) {
                Text(
                    text = stringResource(R.string.friends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SeOutlinedButton(
                    text = stringResource(R.string.action_add_friend),
                    onClick = {
                        showFriendPicker = false
                        onAddFriend()
                    },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                friends.forEach { friend ->
                    Text(
                        text = friend.displayNameSnapshot,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showFriendPicker = false
                                    onAddExpenseForFriend(friend.friendUserId)
                                }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NonGroupDetailBanner(
    bannerColor: Color,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bannerColor)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BannerCircleIconButton(
                onClick = onBack,
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.cd_back),
            )
            BannerCircleIconButton(
                onClick = onOpenInfo,
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.cd_non_group_info),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.non_group_expenses),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
