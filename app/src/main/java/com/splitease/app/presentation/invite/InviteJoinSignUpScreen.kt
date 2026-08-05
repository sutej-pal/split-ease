package com.splitease.app.presentation.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.auth.AuthFormState
import com.splitease.app.presentation.auth.AuthViewModel
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
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
    onSignUp: (email: String, password: String, displayName: String) -> Unit,
    onNavigateLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preview = uiState.preview
    val prefillEmail = preview?.email.orEmpty()
    val groupName = preview?.groupName

    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(prefillEmail) {
        if (email.isBlank() && prefillEmail.isNotBlank()) {
            email = prefillEmail
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
            Text(
                text =
                    if (!groupName.isNullOrBlank()) {
                        stringResource(R.string.invite_join_group_title, groupName)
                    } else {
                        stringResource(R.string.invite_join_title)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = SplitEaseColors.NavyMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextButton(
                text = stringResource(R.string.invite_already_have_account),
                onClick = onNavigateLogin,
                enabled = !formState.isLoading,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.invite_signup_name_label),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_display_name),
                enabled = !formState.isLoading,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.invite_signup_email_label),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.label_email),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !formState.isLoading,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.invite_signup_password_label),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SeTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.label_password),
                enabled = !formState.isLoading,
                supportingText = stringResource(R.string.signup_password_hint),
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
                        enabled = !formState.isLoading,
                    ) {
                        Icon(imageVector = icon, contentDescription = description)
                    }
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            SePrimaryButton(
                text = stringResource(R.string.invite_signup_cta),
                onClick = { onSignUp(email.trim(), password, displayName.trim()) },
                enabled =
                    !formState.isLoading &&
                        displayName.isNotBlank() &&
                        email.isNotBlank() &&
                        password.length >= AuthViewModel.MIN_SIGNUP_PASSWORD_LENGTH,
                isLoading = formState.isLoading,
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
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun InviteJoinSignUpPreview() {
    SePreview {
        InviteJoinSignUpScreen(
            formState = AuthFormState(),
            onSignUp = { _, _, _ -> },
            onNavigateLogin = {},
            onBack = {},
        )
    }
}
