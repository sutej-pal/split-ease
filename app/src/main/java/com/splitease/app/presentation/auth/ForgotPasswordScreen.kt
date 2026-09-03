package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField

@Composable
fun ForgotPasswordScreen(
    formState: AuthFormState,
    onSendReset: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    val localEmailError = showValidation && email.isBlank()
    val emailError = formState.errorMessage.takeUnless { localEmailError }

    AuthScaffold(
        title = stringResource(R.string.forgot_title),
        subtitle = stringResource(R.string.forgot_subtitle),
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Center,
        onNavigateBack = onNavigateBack,
        showLoadingIndicator = false,
    ) {
        SeTextField(
            value = email,
            onValueChange = {
                email = it
                if (showValidation && it.isNotBlank()) showValidation = false
            },
            label = stringResource(R.string.label_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
            isError = localEmailError || emailError != null,
            supportingText =
                if (localEmailError) {
                    stringResource(R.string.msg_email_required)
                } else {
                    emailError
                },
        )
        Spacer(modifier = Modifier.height(8.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_send_reset),
            onClick = {
                showValidation = true
                if (email.isBlank()) return@SePrimaryButton
                onSendReset(email.trim())
            },
            enabled = !formState.isLoading,
            isLoading = formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_back_to_login),
            onClick = onNavigateBack,
            enabled = !formState.isLoading,
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ForgotPasswordScreenPreview() {
    SePreview {
        ForgotPasswordScreen(
            formState = AuthFormState(),
            onSendReset = {},
            onNavigateBack = {},
        )
    }
}
