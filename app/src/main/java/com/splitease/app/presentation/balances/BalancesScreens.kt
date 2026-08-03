package com.splitease.app.presentation.balances

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.FriendBalanceUi
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import java.math.BigDecimal

@Composable
fun BalancesScreen(
    onBack: () -> Unit,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val overall by viewModel.overall.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.balances_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                when (val snapshot = overall) {
                    null -> {
                        Text(
                            stringResource(R.string.balances_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> OverallBalancesContent(snapshot)
                }
            }
        },
    )
}

@Composable
private fun OverallBalancesContent(snapshot: OverallBalancesUi) {
    val empty =
        snapshot.totalOwedToMeByCurrency.isEmpty() &&
            snapshot.totalIOweByCurrency.isEmpty() &&
            snapshot.friendBalances.isEmpty() &&
            snapshot.groupBalances.isEmpty()
    if (empty) {
        SeEmptyState(message = stringResource(R.string.balances_empty))
        return
    }

    SeSectionHeader(text = stringResource(R.string.balances_summary))
    CurrencyTotalsBlock(
        owedToMe = snapshot.totalOwedToMeByCurrency,
        iOwe = snapshot.totalIOweByCurrency,
    )

    if (snapshot.friendBalances.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        SeSectionHeader(text = stringResource(R.string.balances_friends))
        snapshot.friendBalances.forEach { row ->
            FriendBalanceRow(row)
        }
    }

    if (snapshot.groupBalances.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        SeSectionHeader(text = stringResource(R.string.balances_groups))
        snapshot.groupBalances.forEach { group ->
            GroupBalanceSummaryBlock(group, showSimplified = true)
        }
    }
}

@Composable
fun FriendBalanceHeader(
    friendUserId: String,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val balance by remember(friendUserId) { viewModel.observeFriendBalance(friendUserId) }
        .collectAsStateWithLifecycle()
    balance?.let { FriendBalanceRow(it) }
}

@Composable
fun GroupBalanceHeader(
    groupId: String,
    balance: GroupBalanceUi? = null,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val observed by remember(groupId) { viewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    val resolved = balance ?: observed
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
    ) {
        resolved?.let { GroupBalanceSummaryBlock(it, showSimplified = true) }
    }
}

@Composable
private fun FriendBalanceRow(row: FriendBalanceUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(row.displayName, style = MaterialTheme.typography.titleMedium)
        row.netByCurrency.toSortedMap().forEach { (currency, net) ->
            SeMoneyText(
                amount = net.abs(),
                currencyCode = currency,
                tone = netTone(net),
                prefix = moneyPrefix(net),
            )
        }
    }
}

@Composable
private fun GroupBalanceSummaryBlock(
    group: GroupBalanceUi,
    showSimplified: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            group.groupName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (group.myNetByCurrency.isEmpty()) {
            SeMoneyText(
                amount = BigDecimal.ZERO,
                currencyCode = "",
                tone = SeMoneyTone.SETTLED,
                prefix = stringResource(R.string.balances_settled_up),
            )
        } else {
            group.myNetByCurrency.toSortedMap().forEach { (currency, net) ->
                SeMoneyText(
                    amount = net.abs(),
                    currencyCode = currency,
                    tone = netTone(net),
                    prefix = moneyPrefix(net),
                )
            }
        }
        if (showSimplified && group.simplifiedDebts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.balances_who_owes_whom),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            group.simplifiedDebts.forEach { debt ->
                DebtLine(debt)
            }
        }
    }
}

@Composable
private fun DebtLine(debt: LabeledDebt) {
    Text(
        text =
            stringResource(
                R.string.balances_debt_line,
                debt.fromLabel,
                debt.toLabel,
                formatMoney(debt.amount, debt.currencyCode),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun CurrencyTotalsBlock(
    owedToMe: Map<String, BigDecimal>,
    iOwe: Map<String, BigDecimal>,
) {
    if (owedToMe.isEmpty() && iOwe.isEmpty()) {
        SeMoneyText(
            amount = BigDecimal.ZERO,
            currencyCode = "",
            tone = SeMoneyTone.SETTLED,
            prefix = stringResource(R.string.balances_settled_up),
        )
        return
    }
    owedToMe.toSortedMap().forEach { (currency, amount) ->
        SeMoneyText(
            amount = amount,
            currencyCode = currency,
            tone = SeMoneyTone.OWED_TO_YOU,
            prefix = "You are owed",
        )
    }
    iOwe.toSortedMap().forEach { (currency, amount) ->
        SeMoneyText(
            amount = amount,
            currencyCode = currency,
            tone = SeMoneyTone.YOU_OWE,
            prefix = stringResource(R.string.balances_you_owe_plain),
        )
    }
}

private fun netTone(net: BigDecimal): SeMoneyTone =
    when {
        net > BigDecimal.ZERO -> SeMoneyTone.OWED_TO_YOU
        net < BigDecimal.ZERO -> SeMoneyTone.YOU_OWE
        else -> SeMoneyTone.SETTLED
    }

@Composable
private fun moneyPrefix(net: BigDecimal): String? =
    when {
        net > BigDecimal.ZERO -> stringResource(R.string.balances_you_are_owed_plain)
        net < BigDecimal.ZERO -> stringResource(R.string.balances_you_owe_plain)
        else -> stringResource(R.string.balances_settled_up)
    }

private fun formatMoney(amount: BigDecimal, currency: String): String =
    "$currency ${amount.setScale(2)}"
