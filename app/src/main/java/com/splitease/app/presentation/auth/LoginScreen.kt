package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField

@Composable
fun LoginScreen(
    formState: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onNavigateSignUp: () -> Unit,
    onNavigateForgot: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    val isBusy = formState.isLoading
    val focusManager = LocalFocusManager.current
    val emailError = showValidation && email.isBlank()
    val passwordError = showValidation && password.isBlank()

    LaunchedEffect(isBusy) {
        if (isBusy) focusManager.clearFocus()
    }

    fun onCredentialChange(update: () -> Unit) {
        if (isBusy) return
        if (formState.errorMessage != null) onClearError()
        update()
    }

    AuthScaffold(
        title = stringResource(R.string.login_title),
        formState = formState,
        modifier = modifier,
        showErrorInSnackbar = false,
        showLoadingIndicator = false,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (isBusy) 0.5f else 1f),
        ) {
            SeTextField(
                value = email,
                onValueChange = { value -> onCredentialChange { email = value } },
                label = stringResource(R.string.label_email),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isBusy,
                isError = emailError,
                supportingText =
                    if (emailError) stringResource(R.string.msg_email_required) else null,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordSeTextField(
                value = password,
                onValueChange = { value -> onCredentialChange { password = value } },
                enabled = !isBusy,
                isError = passwordError,
                supportingText =
                    if (passwordError) stringResource(R.string.msg_password_required) else null,
            )
            SeTextButton(
                text = stringResource(R.string.action_forgot_password),
                onClick = onNavigateForgot,
                enabled = !isBusy,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(modifier = Modifier.height(8.dp))
            formState.errorMessage?.let { message ->
                SeErrorText(text = message)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SePrimaryButton(
            text = stringResource(R.string.action_log_in),
            onClick = {
                showValidation = true
                if (email.isBlank() || password.isBlank()) return@SePrimaryButton
                onSignIn(email.trim(), password)
            },
            enabled = !isBusy,
            isLoading = isBusy,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeOutlinedButton(
            text = stringResource(R.string.action_continue_google),
            onClick = onContinueWithGoogle,
            enabled = !isBusy,
            modifier = Modifier.alpha(if (isBusy) 0.5f else 1f),
        )
        // Full-width center slot so the link stays aligned with Log in / Google
        // whether a press ripple is visible.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (isBusy) 0.5f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            SeTextButton(
                text = stringResource(R.string.action_sign_up),
                onClick = onNavigateSignUp,
                enabled = !isBusy,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun LoginScreenPreview() {
    SePreview {
        LoginScreen(
            formState = AuthFormState(),
            onSignIn = { _, _ -> },
            onNavigateSignUp = {},
            onNavigateForgot = {},
            onContinueWithGoogle = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 640, name = "Login loading")
@Composable
private fun LoginScreenLoadingPreview() {
    SePreview {
        LoginScreen(
            formState = AuthFormState(isLoading = true),
            onSignIn = { _, _ -> },
            onNavigateSignUp = {},
            onNavigateForgot = {},
            onContinueWithGoogle = {},
        )
    }
}
