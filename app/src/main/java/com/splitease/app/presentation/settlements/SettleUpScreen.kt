package com.splitease.app.presentation.settlements

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.payment.PayActionKind
import com.splitease.app.domain.payment.PaymentDeepLinks
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

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
    val me by viewModel.currentUserId.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf(amountPrefill) }
    var editingAmount by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    val iAmPaying = me != null && me == fromUserId
    val context = LocalContext.current
    val payActions = PaymentDeepLinks.actionsForCurrency(currencyCode)
    val youLabel = stringResource(R.string.you_label)

    val invalidAmountMsg = stringResource(R.string.pay_invalid_amount)
    val sharePaymentMsg = stringResource(R.string.action_share_payment)
    val appMissingMsg = stringResource(R.string.pay_app_missing)

    LaunchedEffect(fromUserId, toUserId, counterpartyLabel, me) {
        // Wait for session so fallbacks are not both set to the same counterparty label.
        if (me == null) return@LaunchedEffect
        val fromFallback =
            when (me) {
                fromUserId -> youLabel
                toUserId -> counterpartyLabel
                else -> ""
            }
        val toFallback =
            when (me) {
                toUserId -> youLabel
                fromUserId -> counterpartyLabel
                else -> ""
            }
        viewModel.prepare(
            fromUserId = fromUserId,
            toUserId = toUserId,
            fromLabel = fromFallback,
            toLabel = toFallback,
        )
    }

    val payer = uiState.payer
    val payee = uiState.payee
    val payerName = payer?.displayName?.takeUnless { it.equals(youLabel, true) } ?: counterpartyLabel
    val payeeName = payee?.displayName?.takeUnless { it.equals(youLabel, true) } ?: counterpartyLabel
    val headline =
        when {
            me != null && me == toUserId ->
                stringResource(R.string.settle_paid_you, payerName)
            me != null && me == fromUserId ->
                stringResource(R.string.settle_you_paid, payeeName)
            else ->
                stringResource(R.string.settle_paid_other, payerName, payeeName)
        }
    val subtitleEmail =
        when {
            me != null && me == toUserId -> payer?.email
            me != null && me == fromUserId -> payee?.email
            else -> payer?.email ?: payee?.email
        }

    SeScreen(
        title = stringResource(R.string.settle_record_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        SeAvatarBadge(
                            name = payer?.displayName ?: "?",
                            photoUrl = payer?.photoUrl,
                            size = 64.dp,
                            borderWidth = 0.dp,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = SplitEaseColors.Navy,
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .size(28.dp),
                        )
                        SeAvatarBadge(
                            name = payee?.displayName ?: "?",
                            photoUrl = payee?.photoUrl,
                            size = 64.dp,
                            borderWidth = 0.dp,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SplitEaseColors.Navy,
                        textAlign = TextAlign.Center,
                    )
                    if (!subtitleEmail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitleEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SplitEaseColors.NavyMuted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    SettleAmountEditor(
                        amount = amount,
                        currencyCode = currencyCode,
                        editing = editingAmount,
                        enabled = !uiState.isSubmitting,
                        onEditingChange = { editingAmount = it },
                        onAmountChange = { amount = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SettleOutsideInfoBanner()

                    if (iAmPaying) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.pay_externally_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = SplitEaseColors.NavyMuted,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        payActions.forEach { action ->
                            SeOutlinedButton(
                                text =
                                    when (action) {
                                        PayActionKind.UPI -> stringResource(R.string.action_pay_upi)
                                        PayActionKind.PAYPAL ->
                                            stringResource(R.string.action_pay_paypal)
                                        PayActionKind.VENMO ->
                                            stringResource(R.string.action_pay_venmo)
                                        PayActionKind.SHARE ->
                                            stringResource(R.string.action_share_payment)
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

                    uiState.errorMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        SeErrorText(it)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                SePrimaryButton(
                    text = stringResource(R.string.action_record_a_payment),
                    onClick = {
                        editingAmount = false
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
                    isLoading = uiState.isSubmitting,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        },
    )
}

@Composable
private fun SettleAmountEditor(
    amount: String,
    currencyCode: String,
    editing: Boolean,
    enabled: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val symbol =
        runCatching {
            Currency.getInstance(currencyCode.ifBlank { "INR" }).getSymbol(Locale.getDefault())
        }.getOrElse { currencyCode.ifBlank { "₹" } }
    val parsed = amount.trim().toBigDecimalOrNull()
    val displayValue =
        if (parsed != null) {
            MoneyFormat.format(parsed, currencyCode.ifBlank { "INR" })
        } else {
            "$symbol ${amount.trim()}"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onEditingChange(true) },
    ) {
        if (editing) {
            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.width(180.dp),
                textStyle =
                    TextStyle(
                        color = SplitEaseColors.Navy,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                cursorBrush = SolidColor(SplitEaseColors.Primary),
                enabled = enabled,
            )
        } else {
            Text(
                text = displayValue,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = SplitEaseColors.Navy,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.cd_edit_amount),
            tint = SplitEaseColors.NavyMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettleOutsideInfoBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(12.dp))
                .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(0.dp, SplitEaseColors.Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = SplitEaseColors.Primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.settle_outside_note),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.Navy,
            modifier = Modifier.weight(1f),
        )
    }
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
