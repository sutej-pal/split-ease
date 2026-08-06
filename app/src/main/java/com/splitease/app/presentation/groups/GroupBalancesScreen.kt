package com.splitease.app.presentation.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.balances.GroupBalanceHeader
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeScreenSubtitleStyle
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.math.BigDecimal

/**
 * Group settle-up / balances screen. Back returns to group detail.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val currencyFallback =
        group?.defaultCurrencyCode?.takeIf { it.isNotBlank() } ?: AppCurrencies.DEFAULT

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshGroupFromCloud(groupId)
        }
    }

    GroupBalancesContent(
        groupBalance = groupBalance,
        currencyFallback = currencyFallback,
        currentUserId = me,
        errorMessage = expensesUi.errorMessage ?: groupsUi.errorMessage,
        infoMessage = expensesUi.infoMessage ?: groupsUi.infoMessage,
        onBack = onBack,
        onSettleDebt = onSettleDebt,
    )
}

@Composable
private fun GroupBalancesContent(
    groupBalance: GroupBalanceUi?,
    currencyFallback: String,
    currentUserId: String?,
    errorMessage: String?,
    infoMessage: String?,
    onBack: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
) {
    val nothingToSettle = stringResource(R.string.group_nothing_to_settle)

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
                // Full-bleed: block owns its own horizontal inset (same as group detail).
                GroupOverallBalanceBlock(
                    balance = groupBalance,
                    currencyFallback = currencyFallback,
                )

                Column(
                    modifier = Modifier.seDetailHorizontal(),
                ) {
                    Spacer(modifier = Modifier.height(SeLayout.ctaTopGap))
                    SeSectionHeader(text = stringResource(R.string.action_settle_up))
                    Spacer(modifier = Modifier.height(SeLayout.itemGap))
                    Text(
                        text = stringResource(R.string.group_balances_settle_hint),
                        style = SeScreenSubtitleStyle(),
                    )
                    Spacer(modifier = Modifier.height(SeLayout.sectionGap))

                    val myDebts =
                        groupBalance
                            ?.simplifiedDebts
                            ?.filter { debt ->
                                currentUserId != null &&
                                    (debt.fromUserId == currentUserId || debt.toUserId == currentUserId)
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
                                        if (currentUserId == debt.fromUserId) {
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
                            Spacer(modifier = Modifier.height(SeLayout.itemGap))
                        }
                    }

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                        SeErrorText(msg)
                    }
                    infoMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                        SeInfoText(msg)
                    }
                }
            }
        },
    )
}

@Preview(name = "Group balances", showBackground = true, heightDp = 640)
@Composable
private fun GroupBalancesScreenPreview() {
    SePreview {
        GroupBalancesContent(
            groupBalance =
                GroupBalanceUi(
                    groupId = "g1",
                    groupName = "Goa trip",
                    myNetByCurrency = mapOf("INR" to BigDecimal("-420.00")),
                    memberNetsByCurrency = emptyMap(),
                    simplifiedDebts =
                        listOf(
                            LabeledDebt(
                                fromUserId = "u1",
                                fromLabel = "You",
                                toUserId = "u2",
                                toLabel = "Sam",
                                amount = BigDecimal("420.00"),
                                currencyCode = "INR",
                            ),
                        ),
                ),
            currencyFallback = "INR",
            currentUserId = "u1",
            errorMessage = null,
            infoMessage = null,
            onBack = {},
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
            onSettleDebt = { _, _, _, _, _ -> },
        )
    }
}

/**
 * Group totals screen (nets by currency / who-owes-whom). Back returns to group detail.
 */
@Composable
fun GroupTotalsScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSpending: () -> Unit,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val currencyFallback =
        group?.defaultCurrencyCode?.takeIf { it.isNotBlank() } ?: AppCurrencies.DEFAULT

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshGroupFromCloud(groupId)
        }
    }

    SeScreen(
        title = stringResource(R.string.group_chip_totals),
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
                // Full-bleed: block owns its own horizontal inset (same as group detail).
                GroupOverallBalanceBlock(
                    balance = groupBalance,
                    currencyFallback = currencyFallback,
                )

                Column(
                    modifier = Modifier.seDetailHorizontal(),
                ) {
                    Spacer(modifier = Modifier.height(SeLayout.ctaTopGap))
                    SeSectionHeader(text = stringResource(R.string.balances_summary))
                    Spacer(modifier = Modifier.height(SeLayout.itemGap))
                    Text(
                        text = stringResource(R.string.group_balances_totals_hint),
                        style = SeScreenSubtitleStyle(),
                    )
                    Spacer(modifier = Modifier.height(SeLayout.sectionGap))

                    if (groupBalance == null) {
                        Text(
                            text = stringResource(R.string.balances_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        GroupBalanceHeader(
                            groupId = groupId,
                            balance = groupBalance,
                        )
                    }

                    Spacer(modifier = Modifier.height(SeLayout.ctaTopGap))
                    SePrimaryButton(
                        text = stringResource(R.string.group_balances_open_spending),
                        onClick = onOpenSpending,
                    )

                    (expensesUi.errorMessage ?: groupsUi.errorMessage)?.let { msg ->
                        Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                        SeErrorText(msg)
                    }
                    (expensesUi.infoMessage ?: groupsUi.infoMessage)?.let { msg ->
                        Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                        SeInfoText(msg)
                    }
                }
            }
        },
    )
}
