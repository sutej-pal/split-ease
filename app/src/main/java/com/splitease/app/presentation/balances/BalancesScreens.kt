package com.splitease.app.presentation.balances

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.theme.*
import java.math.BigDecimal

@Composable
fun GroupBalanceHeader(
    groupId: String? = null,
    balance: GroupBalanceUi? = null,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val observed =
        if (groupId != null) {
            remember(groupId) { viewModel.observeGroupBalance(groupId) }
                .collectAsStateWithLifecycle()
                .value
        } else {
            null
        }
    val resolved = balance ?: observed
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
    ) {
        resolved?.let { GroupBalanceSummaryBlock(it) }
    }
}

@Composable
private fun GroupBalanceSummaryBlock(group: GroupBalanceUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SeTitleMedium(
            text = group.groupName,
            color = SplitEaseColors.Navy,
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
        if (group.simplifiedDebts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SeTitleSmall(
                text = stringResource(R.string.balances_who_owes_whom),
                color = SplitEaseColors.NavyMuted,
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
    SeBodyMedium(
        text =
            stringResource(
                R.string.balances_debt_line,
                debt.fromLabel,
                debt.toLabel,
                formatMoney(debt.amount, debt.currencyCode),
            ),
        color = SplitEaseColors.NavyMuted,
        modifier = Modifier.padding(vertical = 2.dp),
    )
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
