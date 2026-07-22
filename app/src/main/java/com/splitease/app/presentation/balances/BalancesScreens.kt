package com.splitease.app.presentation.balances

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.FriendBalanceUi
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.data.balance.OverallBalancesUi
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesScreen(
    onBack: () -> Unit,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val overall by viewModel.overall.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.balances_title)) },
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
        ) {
            when (val snapshot = overall) {
                null -> {
                    Text(
                        stringResource(R.string.balances_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> OverallBalancesContent(snapshot)
            }
        }
    }
}

@Composable
private fun OverallBalancesContent(snapshot: OverallBalancesUi) {
    val empty =
        snapshot.totalOwedToMeByCurrency.isEmpty() &&
            snapshot.totalIOweByCurrency.isEmpty() &&
            snapshot.friendBalances.isEmpty() &&
            snapshot.groupBalances.isEmpty()
    if (empty) {
        Text(
            stringResource(R.string.balances_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
        return
    }

    Text(stringResource(R.string.balances_summary), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    CurrencyTotalsBlock(
        owedToMe = snapshot.totalOwedToMeByCurrency,
        iOwe = snapshot.totalIOweByCurrency,
    )

    if (snapshot.friendBalances.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.balances_friends), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        snapshot.friendBalances.forEach { row ->
            FriendBalanceRow(row)
            HorizontalDivider()
        }
    }

    if (snapshot.groupBalances.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.balances_groups), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        snapshot.groupBalances.forEach { group ->
            GroupBalanceSummaryBlock(group, showSimplified = true)
            HorizontalDivider()
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
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val balance by remember(groupId) { viewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    balance?.let { GroupBalanceSummaryBlock(it, showSimplified = true) }
}

@Composable
private fun FriendBalanceRow(row: FriendBalanceUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
        row.netByCurrency.toSortedMap().forEach { (currency, net) ->
            Text(
                text = formatViewerNet(net, currency),
                style = MaterialTheme.typography.bodyMedium,
                color = netColor(net),
            )
        }
    }
}

@Composable
private fun GroupBalanceSummaryBlock(
    group: GroupBalanceUi,
    showSimplified: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(group.groupName, style = MaterialTheme.typography.bodyLarge)
        if (group.myNetByCurrency.isEmpty()) {
            Text(
                stringResource(R.string.balances_settled_up),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        } else {
            group.myNetByCurrency.toSortedMap().forEach { (currency, net) ->
                Text(
                    text = formatViewerNet(net, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = netColor(net),
                )
            }
        }
        if (showSimplified && group.simplifiedDebts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.balances_who_owes_whom),
                style = MaterialTheme.typography.titleSmall,
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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun CurrencyTotalsBlock(
    owedToMe: Map<String, BigDecimal>,
    iOwe: Map<String, BigDecimal>,
) {
    if (owedToMe.isEmpty() && iOwe.isEmpty()) {
        Text(
            stringResource(R.string.balances_settled_up),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    owedToMe.toSortedMap().forEach { (currency, amount) ->
        Text(
            text = stringResource(R.string.balances_owed_to_you, formatMoney(amount, currency)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    iOwe.toSortedMap().forEach { (currency, amount) ->
        Text(
            text = stringResource(R.string.balances_you_owe, formatMoney(amount, currency)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun netColor(net: BigDecimal) =
    when {
        net > BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
        net < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun formatViewerNet(net: BigDecimal, currency: String): String =
    when {
        net > BigDecimal.ZERO ->
            stringResource(R.string.balances_owed_to_you, formatMoney(net, currency))
        net < BigDecimal.ZERO ->
            stringResource(R.string.balances_you_owe, formatMoney(net.abs(), currency))
        else -> stringResource(R.string.balances_settled_up)
    }

private fun formatMoney(amount: BigDecimal, currency: String): String =
    "$currency ${amount.setScale(2)}"
