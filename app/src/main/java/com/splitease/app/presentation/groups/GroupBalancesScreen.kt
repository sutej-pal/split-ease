package com.splitease.app.presentation.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import java.math.BigDecimal

/**
 * Group settle-up + totals screen. Back returns to the group detail that pushed it.
 */
@Composable
fun GroupBalancesScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSpending: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
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
    val nothingToSettle = stringResource(R.string.group_nothing_to_settle)
    val lifecycleOwner = LocalLifecycleOwner.current
    val currencyFallback =
        group?.defaultCurrencyCode?.takeIf { it.isNotBlank() } ?: AppCurrencies.DEFAULT

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshGroupFromCloud(groupId)
        }
    }

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
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
            ) {
                GroupOverallBalanceBlock(
                    balance = groupBalance,
                    currencyFallback = currencyFallback,
                )

                Spacer(modifier = Modifier.height(20.dp))
                SeSectionHeader(text = stringResource(R.string.action_settle_up))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.group_balances_settle_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val myDebts =
                    groupBalance
                        ?.simplifiedDebts
                        ?.filter { debt ->
                            me != null && (debt.fromUserId == me || debt.toUserId == me)
                        }
                        .orEmpty()

                if (groupBalance == null) {
                    Text(
                        text = stringResource(R.string.balances_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (myDebts.isEmpty()) {
                    SeInfoText(nothingToSettle)
                } else {
                    myDebts.forEach { debt ->
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(20.dp))

                SeSectionHeader(text = stringResource(R.string.group_chip_totals))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.group_balances_totals_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                GroupBalanceHeader(
                    groupId = groupId,
                    balance = groupBalance,
                )

                val nets = groupBalance?.myNetByCurrency.orEmpty()
                if (nets.isNotEmpty() && nets.values.any { it.compareTo(BigDecimal.ZERO) != 0 }) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                SePrimaryButton(
                    text = stringResource(R.string.group_balances_open_spending),
                    onClick = onOpenSpending,
                )

                (expensesUi.errorMessage ?: groupsUi.errorMessage)?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(msg)
                }
                (expensesUi.infoMessage ?: groupsUi.infoMessage)?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(msg)
                }
            }
        },
    )
}
