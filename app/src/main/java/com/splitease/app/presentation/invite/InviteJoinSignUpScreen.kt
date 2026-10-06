package com.splitease.app.presentation.invite

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.splitease.app.presentation.theme.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.core.DialCodes
import com.splitease.app.data.social.ContactIdentifier
import com.splitease.app.presentation.auth.AuthFormState
import com.splitease.app.presentation.auth.AuthViewModel
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.PhoneNumberRow
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField

/**
 * Invite-aware signup: create account, then root OTP gate blocks until verified.
 */
@Composable
fun InviteJoinSignUpScreen(
    formState: AuthFormState,
    onSignUp: (
        email: String,
        password: String,
        displayName: String,
        phoneCountryCode: String,
        phoneNumber: String,
    ) -> Unit,
    onBack: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Person invites may prefill a real address; group share links use a placeholder and stay blank.
    val prefillEmail =
        uiState.preview
            ?.email
            .orEmpty()
            .trim()
            .takeIf { ContactIdentifier.isRealEmail(it) }
            .orEmpty()
    val inviteeName = uiState.preview?.inviteeName?.trim().orEmpty()
    val prefillPhone = uiState.preview?.phoneNumber?.trim().orEmpty()
    val prefillDial = uiState.preview?.phoneCountryCode?.trim().orEmpty()

    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var dialCode by rememberSaveable { mutableStateOf(ContactIdentifier.DEFAULT_DIAL_CODE) }
    var dialFlag by rememberSaveable { mutableStateOf(DialCodes.flagFor(ContactIdentifier.DEFAULT_DIAL_CODE)) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showDialPicker by rememberSaveable { mutableStateOf(false) }
    val nameError = showValidation && displayName.isBlank()
    val emailError = showValidation && email.isBlank()
    val passwordError =
        showValidation && password.length < AuthViewModel.MIN_SIGNUP_PASSWORD_LENGTH
    val showPhone = prefillPhone.isNotBlank() || phoneNumber.isNotBlank()

    LaunchedEffect(inviteeName) {
        if (displayName.isBlank() && inviteeName.isNotBlank()) {
            displayName = inviteeName
        }
    }
    LaunchedEffect(prefillEmail) {
        if (
            email.isNotBlank() &&
            !ContactIdentifier.isRealEmail(email) &&
            (
                ContactIdentifier.isMobilePlaceholder(email) ||
                    email.endsWith("@splitease.invalid", ignoreCase = true)
            )
        ) {
            email = ""
        }
        if (email.isBlank() && prefillEmail.isNotBlank()) {
            email = prefillEmail
        }
    }
    LaunchedEffect(prefillPhone, prefillDial) {
        if (phoneNumber.isBlank() && prefillPhone.isNotBlank()) {
            phoneNumber = prefillPhone
            if (prefillDial.isNotBlank()) {
                dialCode = prefillDial
                dialFlag = DialCodes.flagFor(prefillDial)
            }
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    SeSystemBars(
        statusBarColor = surface,
        navigationBarColor = surface,
        statusBarDarkIcons = true,
        navigationBarDarkIcons = true,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = surface,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            SeBodyLarge(
                text = stringResource(R.string.invite_signup_name_label),
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_display_name),
                enabled = !formState.isLoading,
                isError = nameError,
                supportingText =
                    if (nameError) stringResource(R.string.msg_display_name_required) else null,
            )
            Spacer(modifier = Modifier.height(16.dp))

            SeBodyLarge(
                text = stringResource(R.string.invite_signup_email_label),
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.label_email),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !formState.isLoading,
                isError = emailError,
                supportingText =
                    if (emailError) stringResource(R.string.msg_email_required) else null,
            )
            if (showPhone) {
                Spacer(modifier = Modifier.height(16.dp))
                PhoneNumberRow(
                    dialFlag = dialFlag,
                    dialCode = dialCode,
                    phoneNumber = phoneNumber,
                    enabled = !formState.isLoading,
                    onDialClick = { showDialPicker = true },
                    onPhoneChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            SeBodyLarge(
                text = stringResource(R.string.invite_signup_password_label),
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.label_password),
                enabled = !formState.isLoading,
                isError = passwordError,
                supportingText =
                    if (passwordError) {
                        stringResource(R.string.signup_error_password_short)
                    } else {
                        stringResource(R.string.signup_password_hint)
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    val icon =
                        if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description =
                        if (passwordVisible) {
                            stringResource(R.string.cd_hide_password)
                        } else {
                            stringResource(R.string.cd_show_password)
                        }
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !formState.isLoading,
                    ) {
                        Icon(imageVector = icon, contentDescription = description)
                    }
                },
            )
            val isBusy = formState.isLoading
            val isGoogleLoading = formState.isGoogleLoading
            val isEmailLoading = isBusy && !isGoogleLoading

            Spacer(modifier = Modifier.height(24.dp))
            SePrimaryButton(
                text = stringResource(R.string.invite_signup_cta),
                onClick = {
                    showValidation = true
                    if (displayName.isBlank() ||
                        email.isBlank() ||
                        password.length < AuthViewModel.MIN_SIGNUP_PASSWORD_LENGTH
                    ) {
                        return@SePrimaryButton
                    }
                    onSignUp(
                        email.trim(),
                        password,
                        displayName.trim(),
                        if (showPhone) dialCode else ContactIdentifier.DEFAULT_DIAL_CODE,
                        if (showPhone) phoneNumber.trim() else "",
                    )
                },
                enabled = !isBusy,
                isLoading = isEmailLoading,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeOutlinedButton(
                text = stringResource(R.string.action_continue_google),
                onClick = onContinueWithGoogle,
                enabled = !isBusy,
                isLoading = isGoogleLoading,
                leadingIcon = {
                    Image(
                        painter = painterResource(R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                enabled = !formState.isLoading,
            )

            formState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                SeErrorText(message)
            }
            formState.infoMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                SeInfoText(message)
            }
        }
    }

    if (showDialPicker) {
        DialCodePickerDialog(
            selectedCode = dialCode,
            selectedFlag = dialFlag,
            onSelect = { option ->
                dialCode = option.code
                dialFlag = option.flag
                showDialPicker = false
            },
            onDismiss = { showDialPicker = false },
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun InviteJoinSignUpPreview() {
    SePreview {
        InviteJoinSignUpScreen(
            formState = AuthFormState(),
            onSignUp = { _, _, _, _, _ -> },
            onBack = {},
            onContinueWithGoogle = {},
        )
    }
}
