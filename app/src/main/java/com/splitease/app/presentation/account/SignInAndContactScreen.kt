package com.splitease.app.presentation.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SeBodyLarge
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.PhoneNumberRow
import com.splitease.app.presentation.ui.SeAssistChip
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeFilledTextField
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton

@Composable
fun SignInAndContactScreen(
    onBack: () -> Unit,
    viewModel: SignInAndContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var emailToRemoveId by remember { mutableStateOf<String?>(null) }
    var showDialCodePicker by remember { mutableStateOf(false) }

    SeScreen(
        title = stringResource(R.string.account_sign_in_contact_section),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            // Caption under header
            Spacer(modifier = Modifier.height(16.dp))
            SeBodySmall(
                text = stringResource(R.string.account_sign_in_contact_caption),
                color = SplitEaseColors.NavyMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Email Row
            val emailValue = profile.email
            SignInContactAccordionRow(
                title = stringResource(R.string.account_email_label),
                subtitle = emailValue,
                expanded = uiState.expandedRow == AccountRow.EMAIL,
                onToggle = { viewModel.toggleRow(AccountRow.EMAIL) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = SplitEaseColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                showDivider = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Primary email item
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SeBodyMedium(
                            text = profile.email,
                            fontWeight = FontWeight.SemiBold,
                            color = SplitEaseColors.Navy,
                        )
                        if (profile.emailConfirmed) {
                            SeAssistChip(
                                text = stringResource(R.string.account_primary_label),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeAssistChip(
                                    text = stringResource(R.string.account_unconfirmed_chip),
                                )
                                SeTextButton(
                                    text = stringResource(R.string.account_resend_action),
                                    onClick = viewModel::resendPrimaryVerification,
                                )
                            }
                        }
                        if (!profile.emailConfirmed && uiState.showPrimaryOtpInput) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SeFilledTextField(
                                    value = uiState.primaryOtpDraft,
                                    onValueChange = viewModel::onPrimaryOtpDraftChange,
                                    placeholder = "6-digit code",
                                    modifier = Modifier.weight(1f),
                                )
                                SePrimaryButton(
                                    text = stringResource(R.string.account_verify_action),
                                    onClick = viewModel::verifyPrimaryEmail,
                                    isLoading = uiState.isEmailSaving,
                                )
                            }
                        }
                    }

                    // Secondary emails list
                    uiState.secondaryEmails.forEach { sec ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeBodyMedium(
                                    text = sec.email,
                                    fontWeight = FontWeight.Medium,
                                    color = SplitEaseColors.Navy,
                                )
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
                            if (sec.isVerified) {
                                SeAssistChip(text = stringResource(R.string.account_secondary_label))
                            } else {
                                SeAssistChip(text = stringResource(R.string.account_unconfirmed_chip))
                            }
                            if (!sec.isVerified) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val otp = uiState.secondaryOtpDrafts[sec.id].orEmpty()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SeFilledTextField(
                                        value = otp,
                                        onValueChange = { viewModel.onSecondaryOtpDraftChange(sec.id, it) },
                                        placeholder = "6-digit code",
                                        modifier = Modifier.weight(1f),
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = { viewModel.verifySecondaryEmail(sec.id) },
                                        isLoading = uiState.isEmailSaving,
                                    )
                                }
                            }
                        }
                    }

                    // Add new email form toggle / panel
                    if (!uiState.showAddEmailForm) {
                        SeTextButton(
                            text = "+ " + stringResource(R.string.account_add_another_email),
                            onClick = viewModel::toggleAddEmailForm,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SeFilledTextField(
                                value = uiState.newEmailDraft,
                                onValueChange = viewModel::onNewEmailDraftChange,
                                placeholder = "name@company.com",
                                isError = uiState.emailError != null,
                            )
                            if (uiState.emailError != null) {
                                SeErrorText(text = uiState.emailError!!)
                            }
                            if (!profile.isGoogleOnly) {
                                SeFilledTextField(
                                    value = uiState.emailPasswordDraft,
                                    onValueChange = viewModel::onEmailPasswordDraftChange,
                                    placeholder = stringResource(R.string.account_current_password_label),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeTextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = viewModel::toggleAddEmailForm,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                SePrimaryButton(
                                    text = stringResource(R.string.action_send_otp),
                                    onClick = viewModel::addSecondaryEmail,
                                    isLoading = uiState.isEmailSaving,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // Phone Row
            val phoneDisplay =
                if (profile.phoneNumber.isNotBlank()) {
                    "${profile.phoneCountryCode} ${profile.phoneNumber}"
                } else {
                    stringResource(R.string.account_add_phone_action)
                }
            SignInContactAccordionRow(
                title = stringResource(R.string.account_phone_label),
                subtitle = phoneDisplay,
                expanded = uiState.expandedRow == AccountRow.PHONE,
                onToggle = { viewModel.toggleRow(AccountRow.PHONE) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = SplitEaseColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                showDivider = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneNumberRow(
                        dialFlag = flagEmojiForDialCode(uiState.phoneCountryCodeDraft),
                        dialCode = uiState.phoneCountryCodeDraft,
                        phoneNumber = uiState.phoneNumberDraft,
                        enabled = !uiState.isPhoneSaving,
                        onDialClick = { showDialCodePicker = true },
                        onPhoneChange = viewModel::onPhoneNumberDraftChange,
                    )
                    if (uiState.phoneError != null) {
                        SeErrorText(text = uiState.phoneError!!)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeTextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = { viewModel.toggleRow(AccountRow.PHONE) },
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        SePrimaryButton(
                            text = stringResource(R.string.action_send_otp),
                            onClick = viewModel::savePhone,
                            isLoading = uiState.isPhoneSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Password Row (hidden for Google-only)
            if (!profile.isGoogleOnly) {
                SignInContactAccordionRow(
                    title = stringResource(R.string.account_password_label),
                    subtitle = "Change",
                    expanded = uiState.expandedRow == AccountRow.PASSWORD,
                    onToggle = { viewModel.toggleRow(AccountRow.PASSWORD) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = SplitEaseColors.Primary,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    showDivider = false,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SeFilledTextField(
                            value = uiState.currentPasswordDraft,
                            onValueChange = viewModel::onCurrentPasswordDraftChange,
                            placeholder = stringResource(R.string.account_current_password_label),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        SeFilledTextField(
                            value = uiState.newPasswordDraft,
                            onValueChange = viewModel::onNewPasswordDraftChange,
                            placeholder = stringResource(R.string.account_new_password_label),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        if (uiState.passwordError != null) {
                            SeErrorText(text = uiState.passwordError!!)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SeTextButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { viewModel.toggleRow(AccountRow.PASSWORD) },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            SePrimaryButton(
                                text = stringResource(R.string.account_update_password_action),
                                onClick = viewModel::updatePassword,
                                isLoading = uiState.isPasswordSaving,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        if (showDialCodePicker) {
            DialCodePickerDialog(
                selectedCode = uiState.phoneCountryCodeDraft,
                selectedFlag = flagEmojiForDialCode(uiState.phoneCountryCodeDraft),
                onSelect = { option ->
                    viewModel.onPhoneCountryCodeDraftChange(option.code)
                    showDialCodePicker = false
                },
                onDismiss = { showDialCodePicker = false },
            )
        }

        emailToRemoveId?.let { id ->
            val sec = uiState.secondaryEmails.firstOrNull { it.id == id }
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
}

@Composable
private fun SignInContactAccordionRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    leadingIcon: @Composable () -> Unit,
    showDivider: Boolean,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotate",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SplitEaseColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                leadingIcon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                SeBodyLarge(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(2.dp))
                SeBodySmall(
                    text = subtitle,
                    color = SplitEaseColors.NavyMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = SplitEaseColors.IconDefault,
                modifier = Modifier.size(24.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 80.dp, end = 20.dp, bottom = 20.dp),
            ) {
                content()
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 80.dp),
                thickness = 1.dp,
                color = SplitEaseColors.Outline,
            )
        }
    }
}
