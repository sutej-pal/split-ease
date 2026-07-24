package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
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
    onGoogleStub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.login_title),
        formState = formState,
        modifier = modifier,
    ) {
        SeTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordSeTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_forgot_password),
            onClick = onNavigateForgot,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_log_in),
            onClick = { onSignIn(email.trim(), password) },
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeOutlinedButton(
            text = stringResource(R.string.action_continue_google),
            onClick = onGoogleStub,
            enabled = !formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_sign_up),
            onClick = onNavigateSignUp,
            enabled = !formState.isLoading,
        )
    }
}

@Composable
fun SignUpScreen(
    formState: AuthFormState,
    onSignUp: (email: String, password: String, displayName: String) -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.signup_title),
        formState = formState,
        modifier = modifier,
    ) {
        SeTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = stringResource(R.string.label_display_name),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SeTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordSeTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_sign_up),
            onClick = { onSignUp(email.trim(), password, displayName.trim()) },
            enabled = !formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_log_in),
            onClick = onNavigateLogin,
            enabled = !formState.isLoading,
        )
    }
}

@Composable
fun ForgotPasswordScreen(
    formState: AuthFormState,
    onSendReset: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.forgot_title),
        subtitle = stringResource(R.string.forgot_subtitle),
        formState = formState,
        modifier = modifier,
    ) {
        SeTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_send_reset),
            onClick = { onSendReset(email.trim()) },
            enabled = !formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_back_to_login),
            onClick = onNavigateBack,
            enabled = !formState.isLoading,
        )
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    formState: AuthFormState,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.verify_email_title),
        subtitle = stringResource(R.string.verify_email_subtitle, email),
        formState = formState,
        modifier = modifier,
    ) {
        SeTextField(
            value = code,
            onValueChange = { incoming ->
                code = incoming.filter { it.isDigit() }.take(4)
            },
            label = stringResource(R.string.label_verification_code),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_verify_code),
            onClick = { onVerify(code) },
            enabled = !formState.isLoading && code.length == 4,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeTextButton(
            text = stringResource(R.string.action_resend_confirmation),
            onClick = onResend,
            enabled = !formState.isLoading,
        )
        SeTextButton(
            text = stringResource(R.string.action_back_to_login),
            onClick = onBackToLogin,
            enabled = !formState.isLoading,
        )
    }
}

@Composable
private fun PasswordSeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    SeTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.label_password),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        trailingIcon = {
            val icon =
                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
            val description =
                if (passwordVisible) {
                    stringResource(R.string.cd_hide_password)
                } else {
                    stringResource(R.string.cd_show_password)
                }
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled,
            ) {
                Icon(imageVector = icon, contentDescription = description)
            }
        },
    )
}

@Composable
private fun AuthScaffold(
    title: String,
    formState: AuthFormState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        content()
        if (formState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        formState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
        formState.infoMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.primary)
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
            onGoogleStub = {},
        )
    }
}
