package com.splitease.app.presentation.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.social.ContactIdentifier
import com.splitease.app.data.social.ContactKind
import com.splitease.app.presentation.invite.InviteDeliveryHandler
import com.splitease.app.presentation.invite.InviteDeliveryPolicy
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.*
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBarActionButton

@Composable
fun EditContactScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onConfirmedForReview: () -> Unit = onDone,
    viewModel: EditContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showDialPicker by rememberSaveable { mutableStateOf(false) }
    val nameError = showValidation && uiState.name.isBlank()

    InviteDeliveryHandler(
        shareText = uiState.pendingShareText,
        phone = uiState.invitePhone,
        showSmsPrompt = uiState.showSmsPrompt,
        onFinished = {
            viewModel.consumeShareText()
            onDone()
        },
        confirmBeforeOpening = InviteDeliveryPolicy.EDIT_CONTACT_CONFIRMS,
    )

    SeScreen(
        title = stringResource(R.string.edit_contact_title),
        onBack = onBack,
        centeredTitle = true,
        actions = {
            SeTopBarActionButton(
                onClick = {
                    showValidation = true
                    if (uiState.isSubmitting || uiState.isLoading) return@SeTopBarActionButton
                    if (uiState.name.isBlank()) return@SeTopBarActionButton
                    viewModel.submit(
                        onLinked = onDone,
                        onConfirmedForReview = onConfirmedForReview,
                    )
                },
                enabled = !uiState.isSubmitting && !uiState.isLoading,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_done),
                    tint = SplitEaseColors.Primary,
                )
            }
        },
        content = { padding ->
            if (uiState.isLoading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding.values),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(color = SplitEaseColors.Primary)
                }
                return@SeScreen
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SeTextField(
                    value = uiState.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.label_name),
                    enabled = !uiState.isSubmitting,
                    isError = nameError,
                    supportingText =
                        if (nameError) stringResource(R.string.msg_name_required) else null,
                    trailingIcon =
                        if (uiState.name.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setName("") }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.cd_clear_name),
                                        tint = SplitEaseColors.IconDefault,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )

                Spacer(modifier = Modifier.height(20.dp))
                if (uiState.mode == EditContactMode.DEVICE_CONTACT) {
                    SeTitleSmall(
                        text = stringResource(R.string.label_phone_or_email),
                        color = SplitEaseColors.NavyMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.options.forEach { option ->
                        ContactMethodRow(
                            option = option,
                            selected = option.id == uiState.selectedOptionId,
                            newPhone = uiState.newPhone,
                            newEmail = uiState.newEmail,
                            enabled = !uiState.isSubmitting,
                            showContactError = showValidation && !selectedContactReady(uiState),
                            onSelect = { viewModel.selectOption(option.id) },
                            onNewPhoneChange = viewModel::setNewPhone,
                            onNewEmailChange = viewModel::setNewEmail,
                        )
                    }
                    if (showValidation && !selectedContactReady(uiState)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SeErrorText(stringResource(R.string.msg_contact_required))
                    }
                    uiState.contactError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        SeErrorText(it)
                    }
                } else {
                    EditContactSingleField(
                        value = uiState.contactInput,
                        enabled = !uiState.isSubmitting,
                        errorText = uiState.contactError,
                        onValueChange = viewModel::setContactInput,
                    )
                }

                if (uiState.confirmOnly) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SeBodyMedium(
                        text = stringResource(R.string.add_friend_review_hint),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
                uiState.infoMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(it)
                }
            }
        },
    )

    val confirm = uiState.phoneConfirm
    if (confirm != null) {
        PhoneConfirmDialog(
            state = confirm,
            onNumberChange = viewModel::setPhoneConfirmNumber,
            onDialClick = { showDialPicker = true },
            onContinue = {
                viewModel.continuePhoneConfirm(
                    onLinked = onDone,
                    onConfirmedForReview = onConfirmedForReview,
                )
            },
            onDismiss = viewModel::dismissPhoneConfirm,
        )
    }
    if (showDialPicker && confirm != null) {
        DialCodePickerDialog(
            selectedCode = confirm.dialCode,
            selectedFlag = confirm.flag,
            onSelect = { option ->
                viewModel.setPhoneConfirmDial(option.code, option.flag)
                showDialPicker = false
            },
            onDismiss = { showDialPicker = false },
        )
    }
}

@Composable
private fun EditContactSingleField(
    value: String,
    enabled: Boolean,
    errorText: String?,
    onValueChange: (String) -> Unit,
) {
    val kind = ContactIdentifier.classify(value)
    val hint =
        when {
            errorText != null -> errorText
            kind == ContactKind.EMAIL -> stringResource(R.string.contact_hint_email)
            else -> null
        }
    SeTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.label_phone_or_email),
        enabled = enabled,
        isError = errorText != null,
        supportingText = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
        leadingIcon = {
            Icon(
                imageVector = contactLeadingIcon(kind),
                contentDescription = null,
                tint = SplitEaseColors.IconDefault,
            )
        },
        trailingIcon =
            if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.cd_clear_field),
                            tint = SplitEaseColors.IconDefault,
                        )
                    }
                }
            } else {
                null
            },
    )
}

private fun contactLeadingIcon(kind: ContactKind): ImageVector =
    when (kind) {
        ContactKind.PHONE -> Icons.Filled.Phone
        ContactKind.EMAIL -> Icons.Filled.Email
        ContactKind.INVALID -> Icons.Outlined.Person
    }

@Composable
private fun PhoneConfirmDialog(
    state: PhoneConfirmState,
    onNumberChange: (String) -> Unit,
    onDialClick: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canContinue = ContactIdentifier.isConfirmablePhoneLength(state.dialCode, state.number)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = SplitEaseColors.Navy,
        textContentColor = SplitEaseColors.Navy,
        title = {
            SeTitleLarge(
                text = stringResource(R.string.phone_confirm_title),
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .clickable(onClick = onDialClick)
                            .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeBodyLarge(
                        text = "${state.flag}  ${state.dialCode}",
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.signup_pick_country_title),
                        tint = SplitEaseColors.IconDefault,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .width(1.dp)
                            .height(24.dp)
                            .background(SplitEaseColors.OutlineStrong),
                )
                TextField(
                    value = state.number,
                    onValueChange = onNumberChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            autoCorrectEnabled = false,
                        ),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = SplitEaseColors.Primary,
                            unfocusedIndicatorColor = SplitEaseColors.OutlineStrong,
                            cursorColor = SplitEaseColors.Primary,
                            focusedTextColor = SplitEaseColors.Navy,
                            unfocusedTextColor = SplitEaseColors.Navy,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue, enabled = canContinue) {
                SeBodyMedium(
                    text = stringResource(R.string.action_continue),
                    color = if (canContinue) SplitEaseColors.Primary else SplitEaseColors.NavyMuted,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                SeBodyMedium(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ContactMethodRow(
    option: ContactMethodOption,
    selected: Boolean,
    newPhone: String,
    newEmail: String,
    enabled: Boolean,
    showContactError: Boolean,
    onSelect: () -> Unit,
    onNewPhoneChange: (String) -> Unit,
    onNewEmailChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onSelect)
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = SplitEaseColors.Primary,
                        unselectedColor = SplitEaseColors.OutlineStrong,
                    ),
            )
            Spacer(modifier = Modifier.width(4.dp))
            when (option.kind) {
                ContactMethodKind.EXISTING_PHONE -> {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = null,
                        tint = SplitEaseColors.IconDefault,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SeBodyLarge(
                        text = option.value,
                    )
                }
                ContactMethodKind.EXISTING_EMAIL -> {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = SplitEaseColors.IconDefault,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SeBodyLarge(
                        text = option.value,
                    )
                }
                ContactMethodKind.NEW_PHONE -> {
                    if (selected) {
                        SeTextField(
                            value = newPhone,
                            onValueChange = onNewPhoneChange,
                            label = stringResource(R.string.label_phone_number),
                            enabled = enabled,
                            isError = showContactError && newPhone.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SeBodyLarge(
                            text = stringResource(R.string.edit_contact_enter_phone),
                        )
                    }
                }
                ContactMethodKind.NEW_EMAIL -> {
                    if (selected) {
                        SeTextField(
                            value = newEmail,
                            onValueChange = onNewEmailChange,
                            label = stringResource(R.string.label_email),
                            enabled = enabled,
                            isError = showContactError && newEmail.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SeBodyLarge(
                            text = stringResource(R.string.edit_contact_enter_email),
                        )
                    }
                }
            }
        }
    }
}

private fun selectedContactReady(state: EditContactUiState): Boolean {
    val selected = state.options.firstOrNull { it.id == state.selectedOptionId } ?: return false
    return when (selected.kind) {
        ContactMethodKind.EXISTING_PHONE, ContactMethodKind.EXISTING_EMAIL ->
            selected.value.isNotBlank()
        ContactMethodKind.NEW_PHONE -> state.newPhone.isNotBlank()
        ContactMethodKind.NEW_EMAIL -> state.newEmail.isNotBlank()
    }
}

@Preview(showBackground = true, name = "Edit contact · phone or email")
@Composable
private fun EditContactSingleFieldPreview() {
    SePreview {
        Column(modifier = Modifier.padding(20.dp)) {
            EditContactSingleField(
                value = "70172 22030",
                enabled = true,
                errorText = null,
                onValueChange = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Confirm phone number")
@Composable
private fun PhoneConfirmDialogPreview() {
    SePreview {
        PhoneConfirmDialog(
            state =
                PhoneConfirmState(
                    dialCode = ContactIdentifier.DEFAULT_DIAL_CODE,
                    flag = "🇮🇳",
                    number = "70172 22030",
                ),
            onNumberChange = {},
            onDialClick = {},
            onContinue = {},
            onDismiss = {},
        )
    }
}
