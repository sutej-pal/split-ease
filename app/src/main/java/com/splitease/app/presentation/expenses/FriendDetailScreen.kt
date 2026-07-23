package com.splitease.app.presentation.expenses

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.balances.FriendBalanceHeader
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SeScreen
import java.math.BigDecimal

@Composable
fun FriendDetailScreen(
    friendUserId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: (fromUserId: String, toUserId: String, amount: String, currency: String, label: String) -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
) {
    val ledger by remember(friendUserId) { viewModel.observeFriendLedger(friendUserId) }
        .collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val balance by remember(friendUserId) { balancesViewModel.observeFriendBalance(friendUserId) }
        .collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(friendUserId.take(8)) }
    val me = viewModel.currentUserId()

    LaunchedEffect(friendUserId) {
        title = viewModel.friendLabel(friendUserId)
        viewModel.refreshMyExpenses()
    }

    SeScreen(
        title = title,
        onBack = onBack,
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                icon = Icons.Filled.Receipt,
            )
        },
        content = { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item {
                    FriendBalanceHeader(friendUserId = friendUserId)
                    val settleCandidate =
                        balance?.netByCurrency?.entries?.firstOrNull {
                            it.value.compareTo(BigDecimal.ZERO) != 0
                        }
                    if (me != null && settleCandidate != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val net = settleCandidate.value
                        val currency = settleCandidate.key
                        SeOutlinedButton(
                            text = stringResource(R.string.action_settle_up),
                            onClick = {
                                if (net < BigDecimal.ZERO) {
                                    onSettleUp(me, friendUserId, net.abs().toPlainString(), currency, title)
                                } else {
                                    onSettleUp(friendUserId, me, net.toPlainString(), currency, title)
                                }
                            },
                        )
                    }
                }
                if (ledger.isEmpty()) {
                    item {
                        SeEmptyState(message = stringResource(R.string.ledger_empty))
                    }
                } else {
                    ledgerEntries(ledger)
                }
                uiState.errorMessage?.let { msg ->
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SeErrorText(msg)
                    }
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        },
    )
}
