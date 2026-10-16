package com.splitease.app.presentation.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.SecondaryEmail
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.PhoneNumberRow
import com.splitease.app.presentation.ui.SeAssistChip
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeListSection
import com.splitease.app.presentation.ui.SeMessageHost
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTrailingActionIconSize

private const val ACCORDION_TRANSITION_MS = 200
private val BodyStartInset = SeLayout.listInset + SeLayout.afterListRowIconTile

@Composable
fun SignInAndContactScreen(
    onBack: () -> Unit,
    viewModel: SignInAndContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    SignInAndContactContent(
        uiState = uiState,
        profile = profile,
        onBack = onBack,
        onToggleRow = viewModel::toggleRow,
        onResendPrimaryVerification = viewModel::resendPrimaryVerification,
        onPrimaryOtpDraftChange = viewModel::onPrimaryOtpDraftChange,
        onVerifyPrimaryEmail = viewModel::verifyPrimaryEmail,
        onResendSecondaryEmail = viewModel::resendSecondaryEmail,
        onRemoveSecondaryEmail = viewModel::removeSecondaryEmail,
        onSecondaryOtpDraftChange = viewModel::onSecondaryOtpDraftChange,
        onVerifySecondaryEmail = viewModel::verifySecondaryEmail,
        onToggleAddEmailForm = viewModel::toggleAddEmailForm,
        onNewEmailDraftChange = viewModel::onNewEmailDraftChange,
        onEmailPasswordDraftChange = viewModel::onEmailPasswordDraftChange,
        onAddSecondaryEmail = viewModel::addSecondaryEmail,
        onPhoneCountryCodeDraftChange = viewModel::onPhoneCountryCodeDraftChange,
        onPhoneNumberDraftChange = viewModel::onPhoneNumberDraftChange,
        onSavePhone = viewModel::savePhone,
        onCurrentPasswordDraftChange = viewModel::onCurrentPasswordDraftChange,
        onNewPasswordDraftChange = viewModel::onNewPasswordDraftChange,
        onUpdatePassword = viewModel::updatePassword,
    )
}

@Composable
fun SignInAndContactContent(
    uiState: SignInAndContactUiState,
    profile: AccountProfileUi,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onToggleRow: (AccountRow) -> Unit = {},
    onResendPrimaryVerification: () -> Unit = {},
    onPrimaryOtpDraftChange: (String) -> Unit = {},
    onVerifyPrimaryEmail: () -> Unit = {},
    onResendSecondaryEmail: (String) -> Unit = {},
    onRemoveSecondaryEmail: (String) -> Unit = {},
    onSecondaryOtpDraftChange: (String, String) -> Unit = { _, _ -> },
    onVerifySecondaryEmail: (String) -> Unit = {},
    onToggleAddEmailForm: () -> Unit = {},
    onNewEmailDraftChange: (String) -> Unit = {},
    onEmailPasswordDraftChange: (String) -> Unit = {},
    onAddSecondaryEmail: () -> Unit = {},
    onPhoneCountryCodeDraftChange: (String) -> Unit = {},
    onPhoneNumberDraftChange: (String) -> Unit = {},
    onSavePhone: () -> Unit = {},
    onCurrentPasswordDraftChange: (String) -> Unit = {},
    onNewPasswordDraftChange: (String) -> Unit = {},
    onUpdatePassword: () -> Unit = {},
) {
    var emailToRemoveId by remember { mutableStateOf<String?>(null) }
    var showDialCodePicker by remember { mutableStateOf(false) }

    SeScreen(
        title = stringResource(R.string.account_sign_in_contact_section),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = {
            SeMessageHost(
                errorMessage = uiState.errorMessage,
                infoMessage = uiState.infoMessage,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = SeLayout.screenBottom),
        ) {
            // Caption under header
            Spacer(modifier = Modifier.height(SeLayout.sectionGap))
            SeBodySmall(
                text = stringResource(R.string.account_sign_in_contact_caption),
                color = SplitEaseColors.NavyMuted,
                modifier = Modifier.padding(horizontal = SeLayout.listInset),
            )
            Spacer(modifier = Modifier.height(SeLayout.sectionGap))

            SeListSection(horizontalPadding = SeLayout.listInset) {
                // Email Row
                val emailValue = profile.email
                AccordionSection(
                    title = stringResource(R.string.account_email_label),
                    subtitle = emailValue,
                    icon = Icons.Filled.Email,
                    expanded = uiState.expandedRow == AccountRow.EMAIL,
                    onToggle = { onToggleRow(AccountRow.EMAIL) },
                    statusLabel =
                        if (!profile.emailConfirmed) {
                            stringResource(R.string.account_unconfirmed_chip)
                        } else {
                            null
                        },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SeLayout.sectionGap)) {
                        // Primary email item
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(SeLayout.itemGap),
                        ) {
                            SeBodyMedium(
                                text = profile.email,
                                fontWeight = FontWeight.Normal,
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
                                        onClick = onResendPrimaryVerification,
                                    )
                                }
                            }
                            if (!profile.emailConfirmed && uiState.showPrimaryOtpInput) {
                                Spacer(modifier = Modifier.height(SeLayout.itemGap))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap),
                                ) {
                                    SeTextField(
                                        value = uiState.primaryOtpDraft,
                                        onValueChange = onPrimaryOtpDraftChange,
                                        placeholder = stringResource(R.string.account_otp_placeholder),
                                        modifier = Modifier.weight(1f),
                                        cornerRadius = 8.dp,
                                        fieldHeight = 38.dp,
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = onVerifyPrimaryEmail,
                                        isLoading = uiState.isEmailSaving,
                                        fillMaxWidth = false,
                                        minHeight = 38.dp,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }

                        // Secondary emails list
                        uiState.secondaryEmails.forEach { sec ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(SeLayout.itemGap),
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap)) {
                                        if (!sec.isVerified) {
                                            SeTextButton(
                                                text = stringResource(R.string.account_resend_action),
                                                onClick = { onResendSecondaryEmail(sec.id) },
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.account_remove_action),
                                            tint = SplitEaseColors.YouOwe,
                                            modifier =
                                                Modifier
                                                    .size(SeTrailingActionIconSize)
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
                                    val otp = uiState.secondaryOtpDrafts[sec.id].orEmpty()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap),
                                    ) {
                                        SeTextField(
                                            value = otp,
                                            onValueChange = { onSecondaryOtpDraftChange(sec.id, it) },
                                            placeholder = stringResource(R.string.account_otp_placeholder),
                                            modifier = Modifier.weight(1f),
                                            cornerRadius = 8.dp,
                                            fieldHeight = 38.dp,
                                        )
                                        SePrimaryButton(
                                            text = stringResource(R.string.account_verify_action),
                                            onClick = { onVerifySecondaryEmail(sec.id) },
                                            isLoading = uiState.isEmailSaving,
                                            fillMaxWidth = false,
                                            minHeight = 38.dp,
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Add new email form toggle / panel
                        if (!uiState.showAddEmailForm) {
                            if (uiState.emailError != null) {
                                SeErrorText(text = uiState.emailError)
                            }
                            SeTextButton(
                                text = stringResource(R.string.account_add_another_email_action),
                                onClick = onToggleAddEmailForm,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(SeLayout.itemGap)) {
                                SeTextField(
                                    value = uiState.newEmailDraft,
                                    onValueChange = onNewEmailDraftChange,
                                    placeholder = stringResource(R.string.account_email_placeholder),
                                    isError = uiState.emailError != null,
                                    cornerRadius = 8.dp,
                                    fieldHeight = 38.dp,
                                )
                                if (uiState.emailError != null) {
                                    SeErrorText(text = uiState.emailError)
                                }
                                if (!profile.isGoogleOnly) {
                                    SeTextField(
                                        value = uiState.emailPasswordDraft,
                                        onValueChange = onEmailPasswordDraftChange,
                                        placeholder = stringResource(R.string.account_current_password_label),
                                        visualTransformation = PasswordVisualTransformation(),
                                        isError = uiState.emailPasswordError != null,
                                        cornerRadius = 8.dp,
                                        fieldHeight = 38.dp,
                                    )
                                    if (uiState.emailPasswordError != null) {
                                        SeErrorText(text = uiState.emailPasswordError)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SeTextButton(
                                        text = stringResource(R.string.action_cancel),
                                        onClick = onToggleAddEmailForm,
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.action_send_otp),
                                        onClick = onAddSecondaryEmail,
                                        isLoading = uiState.isEmailSaving,
                                        fillMaxWidth = false,
                                        minHeight = 38.dp,
                                        contentPadding =
                                            PaddingValues(
                                                horizontal = SeLayout.detailHorizontal,
                                                vertical = 8.dp,
                                            ),
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
                AccordionSection(
                    title = stringResource(R.string.account_phone_label),
                    subtitle = phoneDisplay,
                    icon = Icons.Filled.Phone,
                    expanded = uiState.expandedRow == AccountRow.PHONE,
                    onToggle = { onToggleRow(AccountRow.PHONE) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SeLayout.itemGap)) {
                        PhoneNumberRow(
                            dialFlag = flagEmojiForDialCode(uiState.phoneCountryCodeDraft),
                            dialCode = uiState.phoneCountryCodeDraft,
                            phoneNumber = uiState.phoneNumberDraft,
                            enabled = !uiState.isPhoneSaving,
                            onDialClick = { showDialCodePicker = true },
                            onPhoneChange = onPhoneNumberDraftChange,
                            cornerRadius = 8.dp,
                            fieldHeight = 38.dp,
                        )
                        if (uiState.phoneError != null) {
                            SeErrorText(text = uiState.phoneError)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SeTextButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { onToggleRow(AccountRow.PHONE) },
                            )
                            SePrimaryButton(
                                text = stringResource(R.string.action_save),
                                onClick = onSavePhone,
                                isLoading = uiState.isPhoneSaving,
                                fillMaxWidth = false,
                                minHeight = 38.dp,
                                contentPadding =
                                    PaddingValues(
                                        horizontal = SeLayout.detailHorizontal,
                                        vertical = 8.dp,
                                    ),
                            )
                        }
                    }
                }

                // Password Row (hidden for Google-only)
                if (!profile.isGoogleOnly) {
                    AccordionSection(
                        title = stringResource(R.string.account_password_label),
                        subtitle = stringResource(R.string.account_password_change_value),
                        icon = Icons.Filled.Lock,
                        expanded = uiState.expandedRow == AccountRow.PASSWORD,
                        onToggle = { onToggleRow(AccountRow.PASSWORD) },
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(SeLayout.itemGap)) {
                            SeTextField(
                                value = uiState.currentPasswordDraft,
                                onValueChange = onCurrentPasswordDraftChange,
                                placeholder = stringResource(R.string.account_current_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = uiState.currentPasswordError != null,
                                cornerRadius = 8.dp,
                                fieldHeight = 38.dp,
                            )
                            if (uiState.currentPasswordError != null) {
                                SeErrorText(text = uiState.currentPasswordError)
                            }
                            SeTextField(
                                value = uiState.newPasswordDraft,
                                onValueChange = onNewPasswordDraftChange,
                                placeholder = stringResource(R.string.account_new_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = uiState.newPasswordError != null || uiState.passwordError != null,
                                cornerRadius = 8.dp,
                                fieldHeight = 38.dp,
                            )
                            if (uiState.newPasswordError != null) {
                                SeErrorText(text = uiState.newPasswordError)
                            } else if (uiState.passwordError != null) {
                                SeErrorText(text = uiState.passwordError)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                horizontalArrangement = Arrangement.spacedBy(SeLayout.itemGap, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeTextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { onToggleRow(AccountRow.PASSWORD) },
                                )
                                SePrimaryButton(
                                    text = stringResource(R.string.account_update_password_action),
                                    onClick = onUpdatePassword,
                                    isLoading = uiState.isPasswordSaving,
                                    fillMaxWidth = false,
                                    minHeight = 38.dp,
                                    contentPadding =
                                        PaddingValues(
                                            horizontal = SeLayout.detailHorizontal,
                                            vertical = 8.dp,
                                        ),
                                )
                            }
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
                    onPhoneCountryCodeDraftChange(option.code)
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
                    onRemoveSecondaryEmail(id)
                    emailToRemoveId = null
                },
                tone = SeConfirmTone.Danger,
            )
        }
    }
}

@Composable
private fun AccordionSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    statusLabel: String? = null,
    body: @Composable () -> Unit,
) {
    SeListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onToggle,
        statusLabel = statusLabel,
        trailingIcon = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
    )
    AnimatedVisibility(
        visible = expanded,
        enter =
            expandVertically(animationSpec = tween(ACCORDION_TRANSITION_MS)) +
                fadeIn(animationSpec = tween(ACCORDION_TRANSITION_MS)),
        exit =
            shrinkVertically(animationSpec = tween(ACCORDION_TRANSITION_MS)) +
                fadeOut(animationSpec = tween(ACCORDION_TRANSITION_MS)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SeLayout.listInset + 8.dp,
                        end = SeLayout.listInset,
                        bottom = SeLayout.ctaTopGap,
                    ),
        ) {
            body()
        }
    }
}

@Preview(name = "Sign-in & Contact · Default (Confirmed)", showBackground = true, heightDp = 720)
@Composable
private fun SignInAndContactScreenPreview() {
    SePreview {
        SignInAndContactContent(
            uiState =
                SignInAndContactUiState(
                    secondaryEmails =
                        listOf(
                            SecondaryEmail(id = "1", email = "alex.work@company.com", isVerified = true),
                        ),
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex@example.com",
                    emailConfirmed = true,
                    phoneCountryCode = "+91",
                    phoneNumber = "9876543210",
                ),
        )
    }
}

@Preview(name = "Sign-in & Contact · Email unconfirmed collapsed", showBackground = true, heightDp = 720)
@Composable
private fun SignInAndContactScreenEmailUnconfirmedPreview() {
    SePreview {
        SignInAndContactContent(
            uiState = SignInAndContactUiState(),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex@example.com",
                    emailConfirmed = false,
                    phoneCountryCode = "+91",
                    phoneNumber = "9876543210",
                ),
        )
    }
}

@Preview(name = "Sign-in & Contact · Email expanded", showBackground = true, heightDp = 800)
@Composable
private fun SignInAndContactScreenEmailExpandedPreview() {
    SePreview {
        SignInAndContactContent(
            uiState =
                SignInAndContactUiState(
                    expandedRow = AccountRow.EMAIL,
                    secondaryEmails =
                        listOf(
                            SecondaryEmail(id = "1", email = "alex.work@company.com", isVerified = true),
                            SecondaryEmail(id = "2", email = "alex.personal@gmail.com", isVerified = false),
                        ),
                    showAddEmailForm = true,
                    newEmailDraft = "alex.backup@domain.com",
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex@example.com",
                    emailConfirmed = false,
                    phoneCountryCode = "+91",
                    phoneNumber = "9876543210",
                ),
        )
    }
}

@Preview(name = "Sign-in & Contact · Phone expanded", showBackground = true, heightDp = 720)
@Composable
private fun SignInAndContactScreenPhoneExpandedPreview() {
    SePreview {
        SignInAndContactContent(
            uiState =
                SignInAndContactUiState(
                    expandedRow = AccountRow.PHONE,
                    phoneCountryCodeDraft = "+91",
                    phoneNumberDraft = "9876543210",
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex@example.com",
                    emailConfirmed = true,
                    phoneCountryCode = "+91",
                    phoneNumber = "9876543210",
                ),
        )
    }
}

@Preview(name = "Sign-in & Contact · Dark mode", showBackground = true, heightDp = 720)
@Composable
private fun SignInAndContactScreenDarkPreview() {
    SePreview(darkTheme = true) {
        SignInAndContactContent(
            uiState =
                SignInAndContactUiState(
                    expandedRow = AccountRow.PASSWORD,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex@example.com",
                    emailConfirmed = true,
                    phoneCountryCode = "+91",
                    phoneNumber = "9876543210",
                ),
        )
    }
}
