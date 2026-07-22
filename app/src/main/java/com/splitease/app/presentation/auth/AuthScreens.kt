package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.splitease.app.R

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
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !formState.isLoading,
        )
        TextButton(
            onClick = onNavigateForgot,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.action_forgot_password))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onSignIn(email.trim(), password) },
            enabled = !formState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_log_in))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onGoogleStub,
            enabled = !formState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }
        TextButton(onClick = onNavigateSignUp, enabled = !formState.isLoading) {
            Text(stringResource(R.string.action_sign_up))
        }
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
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_display_name)) },
            singleLine = true,
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSignUp(email.trim(), password, displayName.trim()) },
            enabled = !formState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_sign_up))
        }
        TextButton(onClick = onNavigateLogin, enabled = !formState.isLoading) {
            Text(stringResource(R.string.action_log_in))
        }
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
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSendReset(email.trim()) },
            enabled = !formState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_send_reset))
        }
        TextButton(onClick = onNavigateBack, enabled = !formState.isLoading) {
            Text("Back to login")
        }
    }
}

@Composable
private fun PasswordOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.label_password)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            val icon =
                if (passwordVisible) {
                    Icons.Filled.VisibilityOff
                } else {
                    Icons.Filled.Visibility
                }
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
