package com.splitease.app.presentation.settlements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.math.BigDecimal

@Composable
fun SettleSelectionScreen(
    groupId: String?,
    onBack: () -> Unit,
    onNavigateRecord: (from: String, to: String, amount: String, currency: String, label: String) -> Unit,
    onNavigateMoreOptions: () -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel(),
) {
    val suggested by viewModel.observeSuggestedSettlements(groupId).collectAsStateWithLifecycle(emptyList())
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.settle_selection_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values),
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(suggested) { debt ->
                    val isIOWe = debt.fromUserId == me
                    val otherLabel = if (isIOWe) debt.toLabel else debt.fromLabel
                    val otherPhoto = if (isIOWe) debt.toPhotoUrl else debt.fromPhotoUrl
                    
                    SuggestedSettleRow(
                        name = otherLabel,
                        photoUrl = otherPhoto,
                        amount = debt.amount,
                        currencyCode = debt.currencyCode,
                        isIOwe = isIOWe,
                        onClick = {
                            onNavigateRecord(
                                debt.fromUserId,
                                debt.toUserId,
                                debt.amount.toPlainString(),
                                debt.currencyCode,
                                otherLabel
                            )
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = SplitEaseColors.Outline
                    )
                }

                item {
                    SeTextButton(
                        text = stringResource(R.string.action_more_options),
                        onClick = onNavigateMoreOptions,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedSettleRow(
    name: String,
    photoUrl: String?,
    amount: BigDecimal,
    currencyCode: String,
    isIOwe: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeAvatarBadge(name = name, photoUrl = photoUrl, size = 48.dp, borderWidth = 0.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = SplitEaseColors.Navy,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isIOwe) stringResource(R.string.balances_you_owe_plain) else stringResource(R.string.balances_you_are_owed_plain),
                style = MaterialTheme.typography.labelSmall,
                color = if (isIOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
            )
            Text(
                text = MoneyFormat.format(amount, currencyCode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isIOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
            )
        }
    }
}
