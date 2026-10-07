package com.splitease.app.presentation.account

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SeLabelSmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.PhoneNumberRow
import com.splitease.app.presentation.ui.SeAccordionRow
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField

@Composable
fun SignInAndContactScreen(
    onBack: () -> Unit,
    viewModel: SignInAndContactViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var emailToRemoveId by remember { mutableStateOf<String?>(null) }
    var showDialCodePicker by remember { mutableStateOf(false) }

    LaunchedEffect(ui.infoMessage) {
        val message = ui.infoMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    SeScreen(
        title = stringResource(R.string.account_sign_in_contact_section),
        subtitle = stringResource(R.string.account_sign_in_contact_subtitle),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            SignInContactCard {
                val phoneDisplay =
                    if (profile.phoneNumber.isNotBlank()) {
                        "${profile.phoneCountryCode} ${profile.phoneNumber}"
                    } else {
                        stringResource(R.string.account_add_phone_action)
                    }
                val hasSecondary = ui.secondaryEmails.isNotEmpty()

                SeAccordionRow(
                    label = stringResource(R.string.account_email_label),
                    value = profile.email,
                    expanded = ui.expandedRow == SignInContactRow.EMAIL,
                    onToggle = { viewModel.toggleRow(SignInContactRow.EMAIL) },
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Email,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                    trailingChip = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!profile.emailConfirmed) {
                                StatusChip(
                                    text = stringResource(R.string.account_unconfirmed_chip),
                                    background = SplitEaseColors.YouOwe.copy(alpha = 0.15f),
                                    color = SplitEaseColors.YouOwe,
                                )
                            }
                            if (hasSecondary) {
                                StatusChip(
                                    text = "+${ui.secondaryEmails.size}",
                                    background = SplitEaseColors.PrimarySoft,
                                    color = SplitEaseColors.Primary,
                                )
                            }
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    SeBodyMedium(
                                        text = profile.email,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SplitEaseColors.Navy,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusChip(
                                        text = stringResource(R.string.account_primary_label),
                                        background = SplitEaseColors.PrimarySoft,
                                        color = SplitEaseColors.Primary,
                                    )
                                }
                                if (!profile.emailConfirmed) {
                                    SeTextButton(
                                        text = stringResource(R.string.account_resend_action),
                                        onClick = viewModel::resendPrimaryVerification,
                                    )
                                }
                            }
                            if (!profile.emailConfirmed && ui.showPrimaryOtpInput) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SeTextField(
                                        value = ui.primaryOtpDraft,
                                        onValueChange = viewModel::onPrimaryOtpDraftChange,
                                        placeholder = stringResource(R.string.account_otp_placeholder),
                                        modifier = Modifier.weight(1f),
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = viewModel::verifyPrimaryEmail,
                                        isLoading = ui.isEmailSaving,
                                    )
                                }
                            }
                        }

                        ui.secondaryEmails.forEach { sec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    SeBodyMedium(
                                        text = sec.email,
                                        fontWeight = FontWeight.Medium,
                                        color = SplitEaseColors.Navy,
                                    )
                                    SeBodySmall(
                                        text =
                                            if (sec.isVerified) {
                                                stringResource(R.string.account_secondary_label)
                                            } else {
                                                stringResource(R.string.account_unconfirmed_chip)
                                            },
                                        color =
                                            if (sec.isVerified) {
                                                SplitEaseColors.NavyMuted
                                            } else {
                                                SplitEaseColors.YouOwe
                                            },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!sec.isVerified) {
                                        SeTextButton(
                                            text = stringResource(R.string.account_resend_action),
                                            onClick = { viewModel.resendSecondaryEmail(sec.id) },
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.account_remove_action),
                                        tint = SplitEaseColors.YouOwe,
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clickable { emailToRemoveId = sec.id },
                                    )
                                }
                            }
                            if (!sec.isVerified) {
                                val otp = ui.secondaryOtpDrafts[sec.id].orEmpty()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SeTextField(
                                        value = otp,
                                        onValueChange = { viewModel.onSecondaryOtpDraftChange(sec.id, it) },
                                        placeholder = stringResource(R.string.account_otp_placeholder),
                                        modifier = Modifier.weight(1f),
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = { viewModel.verifySecondaryEmail(sec.id) },
                                        isLoading = ui.isEmailSaving,
                                    )
                                }
                            }
                        }

                        if (!ui.showAddEmailForm) {
                            if (ui.emailError != null) {
                                SeBodySmall(
                                    text = ui.emailError!!,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            SeTextButton(
                                text = "+ " + stringResource(R.string.account_add_email_action),
                                onClick = viewModel::toggleAddEmailForm,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeTextField(
                                    value = ui.newEmailDraft,
                                    onValueChange = viewModel::onNewEmailDraftChange,
                                    label = stringResource(R.string.account_email_label),
                                    isError = ui.emailError != null,
                                    supportingText = ui.emailError,
                                )
                                if (!profile.isGoogleOnly) {
                                    SeTextField(
                                        value = ui.emailPasswordDraft,
                                        onValueChange = viewModel::onEmailPasswordDraftChange,
                                        label = stringResource(R.string.account_current_password_label),
                                        visualTransformation = PasswordVisualTransformation(),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    SeTextButton(
                                        text = stringResource(R.string.action_cancel),
                                        onClick = viewModel::toggleAddEmailForm,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_send_otp_action),
                                        onClick = viewModel::addSecondaryEmail,
                                        isLoading = ui.isEmailSaving,
                                    )
                                }
                            }
                        }
                    }
                }

                SeAccordionRow(
                    label = stringResource(R.string.account_phone_label),
                    value = phoneDisplay,
                    expanded = ui.expandedRow == SignInContactRow.PHONE,
                    onToggle = { viewModel.toggleRow(SignInContactRow.PHONE) },
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Phone,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhoneNumberRow(
                            dialFlag = flagEmojiForDialCode(ui.phoneCountryCodeDraft),
                            dialCode = ui.phoneCountryCodeDraft,
                            phoneNumber = ui.phoneNumberDraft,
                            enabled = !ui.isPhoneSaving,
                            onDialClick = { showDialCodePicker = true },
                            onPhoneChange = viewModel::onPhoneNumberDraftChange,
                        )
                        if (ui.phoneError != null) {
                            SeBodySmall(
                                text = ui.phoneError!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SeTextButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { viewModel.toggleRow(SignInContactRow.PHONE) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SePrimaryButton(
                                text = stringResource(R.string.action_save),
                                onClick = viewModel::savePhone,
                                isLoading = ui.isPhoneSaving,
                            )
                        }
                    }
                }

                if (!profile.isGoogleOnly) {
                    SeAccordionRow(
                        label = stringResource(R.string.account_password_label),
                        value = stringResource(R.string.account_password_change_value),
                        expanded = ui.expandedRow == SignInContactRow.PASSWORD,
                        onToggle = { viewModel.toggleRow(SignInContactRow.PASSWORD) },
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Lock,
                                tint = SplitEaseColors.Primary,
                                size = 40,
                            )
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SeTextField(
                                value = ui.currentPasswordDraft,
                                onValueChange = viewModel::onCurrentPasswordDraftChange,
                                label = stringResource(R.string.account_current_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            SeTextField(
                                value = ui.newPasswordDraft,
                                onValueChange = viewModel::onNewPasswordDraftChange,
                                label = stringResource(R.string.account_new_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = ui.passwordError != null,
                                supportingText = ui.passwordError,
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SeTextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { viewModel.toggleRow(SignInContactRow.PASSWORD) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SePrimaryButton(
                                    text = stringResource(R.string.account_update_password_action),
                                    onClick = viewModel::updatePassword,
                                    isLoading = ui.isPasswordSaving,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialCodePicker) {
        DialCodePickerDialog(
            selectedCode = ui.phoneCountryCodeDraft,
            selectedFlag = flagEmojiForDialCode(ui.phoneCountryCodeDraft),
            onSelect = { option ->
                viewModel.onPhoneCountryCodeDraftChange(option.code)
                showDialCodePicker = false
            },
            onDismiss = { showDialCodePicker = false },
        )
    }

    emailToRemoveId?.let { id ->
        val sec = ui.secondaryEmails.firstOrNull { it.id == id }
        SeConfirmDialog(
            title = stringResource(R.string.account_remove_email_title),
            body = stringResource(R.string.account_remove_email_body, sec?.email.orEmpty()),
            confirmLabel = stringResource(R.string.account_remove_action),
            onDismissRequest = { emailToRemoveId = null },
            onConfirm = {
                viewModel.removeSecondaryEmail(id)
                emailToRemoveId = null
            },
            tone = SeConfirmTone.Danger,
        )
    }
}

@Composable
private fun SignInContactCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SplitEaseColors.Surface)
                .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        SeLabelSmall(
            text = text,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun flagEmojiForDialCode(code: String): String =
    when (code.trim()) {
        "+91" -> "🇮🇳"
        "+1" -> "🇺🇸"
        "+44" -> "🇬🇧"
        "+61" -> "🇦🇺"
        "+1 CA", "+1CA" -> "🇨🇦"
        "+49" -> "🇩🇪"
        "+33" -> "🇫🇷"
        "+81" -> "🇯🇵"
        "+86" -> "🇨🇳"
        "+55" -> "🇧🇷"
        "+971" -> "🇦🇪"
        "+65" -> "🇸🇬"
        else -> "🌐"
    }

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun SignInAndContactPreview() {
    SePreview {
        SeBodyMedium(text = "Sign in and contact")
    }
}
