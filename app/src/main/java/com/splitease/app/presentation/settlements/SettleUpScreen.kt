package com.splitease.app.presentation.settlements

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.payment.PayActionKind
import com.splitease.app.domain.payment.PaymentDeepLinks
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextField
import java.math.BigDecimal

@Composable
fun SettleUpScreen(
    fromUserId: String,
    toUserId: String,
    counterpartyLabel: String,
    amountPrefill: String,
    currencyCode: String,
    groupId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf(amountPrefill) }
    var note by rememberSaveable { mutableStateOf("") }
    val me = viewModel.currentUserId()
    val iAmPaying = me == fromUserId
    val context = LocalContext.current
    val summary =
        if (iAmPaying) {
            stringResource(R.string.settle_summary_you_pay, counterpartyLabel)
        } else {
            stringResource(R.string.settle_summary_they_pay, counterpartyLabel)
        }
    val payActions = PaymentDeepLinks.actionsForCurrency(currencyCode)

    val invalidAmountMsg = stringResource(R.string.pay_invalid_amount)
    val sharePaymentMsg = stringResource(R.string.action_share_payment)
    val appMissingMsg = stringResource(R.string.pay_app_missing)
    SeScreen(
        title = stringResource(R.string.action_settle_up),
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
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.label_amount))
                SeTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.label_amount),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currencyCode.ifBlank { "—" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.label_payment_note),
                    enabled = !uiState.isSubmitting,
                )
                if (iAmPaying) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SeSectionHeader(text = stringResource(R.string.pay_externally_section))
                    Text(
                        text = stringResource(R.string.pay_externally_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    payActions.forEach { action ->
                        SeOutlinedButton(
                            text =
                                when (action) {
                                    PayActionKind.UPI -> stringResource(R.string.action_pay_upi)
                                    PayActionKind.PAYPAL -> stringResource(R.string.action_pay_paypal)
                                    PayActionKind.VENMO -> stringResource(R.string.action_pay_venmo)
                                    PayActionKind.SHARE -> stringResource(R.string.action_share_payment)
                                },
                            onClick = {
                                val parsed =
                                    runCatching { BigDecimal(amount.trim()) }.getOrNull()
                                if (parsed == null || parsed <= BigDecimal.ZERO) {
                                    Toast
                                        .makeText(
                                        context,
                                        invalidAmountMsg,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@SeOutlinedButton
                                }
                                launchPayAction(
                                    context = context,
                                    kind = action,
                                    amount = parsed,
                                    currencyCode = currencyCode,
                                    counterpartyLabel = counterpartyLabel,
                                    note = note,
                                    sharePaymentMsg = sharePaymentMsg,
                                    appMissingMsg = appMissingMsg,
                                )
                            },
                            enabled = !uiState.isSubmitting && amount.isNotBlank(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SePrimaryButton(
                    text = stringResource(R.string.action_record_payment),
                    onClick = {
                        viewModel.recordSettlement(
                            fromUserId = fromUserId,
                            toUserId = toUserId,
                            amountText = amount,
                            currencyCode = currencyCode,
                            groupId = groupId,
                            note = note,
                            onSuccess = onDone,
                        )
                    },
                    enabled = !uiState.isSubmitting && amount.isNotBlank(),
                )
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
            }
        },
    )
}

private fun launchPayAction(
    context: android.content.Context,
    kind: PayActionKind,
    amount: BigDecimal,
    currencyCode: String,
    counterpartyLabel: String,
    note: String,
    sharePaymentMsg: String,
    appMissingMsg: String,
) {
    try {
        when (kind) {
            PayActionKind.UPI -> {
                val uri =
                    PaymentDeepLinks
                        .upiPayUri(
                        amount = amount,
                        currencyCode = currencyCode,
                        payeeName = counterpartyLabel,
                        note = note.ifBlank { "SplitEase settlement" },
                    ).toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            PayActionKind.PAYPAL -> {
                val uri =
                    PaymentDeepLinks
                        .paypalUri(
                        amount = amount,
                        currencyCode = currencyCode,
                    ).toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            PayActionKind.VENMO -> {
                val uri =
                    PaymentDeepLinks
                        .venmoUri(
                        amount = amount,
                        note = note.ifBlank { "SplitEase" },
                    ).toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            PayActionKind.SHARE -> {
                val text =
                    PaymentDeepLinks.shareText(
                        amount = amount,
                        currencyCode = currencyCode,
                        counterpartyLabel = counterpartyLabel,
                        note = note,
                    )
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                context.startActivity(
                    Intent.createChooser(intent, sharePaymentMsg),
                )
            }
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, appMissingMsg, Toast.LENGTH_SHORT).show()
    }
}
