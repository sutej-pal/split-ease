package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.components.SegmentedOtpInput
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton

@Composable
fun ResetPasswordOtpScreen(
    email: String,
    formState: AuthFormState,
    onSubmit: (code: String, newPassword: String, confirmPassword: String) -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val otpReady = formState.recoveryOtpVerified
    val otpIsError =
        formState.errorMessage == AuthMessages.RESET_OTP_INVALID_OR_EXPIRED ||
            formState.errorMessage == AuthMessages.VERIFY_EMAIL_INVALID_CODE
    val newPasswordError =
        formState.errorMessage.takeIf {
            it == AuthMessages.RESET_PASSWORD_REQUIREMENTS || it == AuthMessages.PASSWORD_SHORT
        }
    val confirmPasswordError =
        formState.errorMessage.takeIf { it == AuthMessages.RESET_PASSWORD_MISMATCH }
    val passwordRules =
        remember(newPassword) {
            val hasMinLength = newPassword.length >= AuthViewModel.MIN_SIGNUP_PASSWORD_LENGTH
            val hasUpperAndLower = newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() }
            val hasDigit = newPassword.any { it.isDigit() }
            hasMinLength && hasUpperAndLower && hasDigit
        }
    val canSubmit =
        !formState.isLoading &&
            passwordRules &&
            confirmPassword.isNotEmpty() &&
            newPassword == confirmPassword &&
            (otpReady || code.length == AuthViewModel.SIGNUP_OTP_LENGTH)
    val subtitleBefore = stringResource(R.string.reset_password_subtitle_before)
    val subtitleAfter = stringResource(R.string.reset_password_subtitle_after)
    val wrongEmailLabel = stringResource(R.string.action_wrong_email)
    val navy = SplitEaseColors.Navy
    val primary = SplitEaseColors.Primary
    val displayEmail = remember(email) { truncateEmailForSubtitle(email) }
    val wrongEmailLinkStyles =
        remember(primary) {
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
    val subtitleAnnotated =
        buildAnnotatedString {
            append(subtitleBefore.trimEnd())
            append(' ')
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = navy)) {
                append(displayEmail)
            }
            append(subtitleAfter.trimEnd())
            append(' ')
            withLink(
                LinkAnnotation.Clickable(
                    tag = "wrong_email",
                    styles = wrongEmailLinkStyles,
                    linkInteractionListener = {
                        if (!formState.isLoading) {
                            onBackToLogin()
                        }
                    },
                ),
            ) {
                append(wrongEmailLabel)
            }
        }

    AuthScaffold(
        title = stringResource(R.string.reset_password_title),
        subtitleAnnotated = subtitleAnnotated,
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Top,
        onNavigateBack = onBackToLogin,
        showLoadingIndicator = false,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        SegmentedOtpInput(
            value = code,
            onValueChange = { incoming ->
                code = incoming.filter { it.isDigit() }.take(AuthViewModel.SIGNUP_OTP_LENGTH)
            },
            onComplete = { completed -> code = completed },
            enabled = !formState.isLoading && !otpReady,
            isError = otpIsError,
            length = AuthViewModel.SIGNUP_OTP_LENGTH,
            horizontalAlignment = Alignment.Start,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.reset_otp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = SplitEaseColors.NavyMuted,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.label_new_password),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PasswordSeTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            enabled = !formState.isLoading,
            placeholder = stringResource(R.string.reset_password_placeholder),
            supportingText = newPasswordError,
            isError = newPasswordError != null,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordRequirementsChecklist(password = newPassword)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.label_confirm_password),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PasswordSeTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            enabled = !formState.isLoading,
            placeholder = stringResource(R.string.reset_confirm_password_placeholder),
            supportingText = confirmPasswordError,
            isError = confirmPasswordError != null,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_set_new_password),
            onClick = { onSubmit(code, newPassword, confirmPassword) },
            enabled = canSubmit,
            isLoading = formState.isLoading,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SeTextButton(
            text = stringResource(R.string.action_resend_confirmation),
            onClick = onResend,
            enabled = !formState.isLoading && !otpReady,
            modifier = Modifier.align(Alignment.Start),
        )
        SeTextButton(
            text = stringResource(R.string.action_back_to_login),
            onClick = onBackToLogin,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResetPasswordOtpScreenPreview() {
    SePreview {
        ResetPasswordOtpScreen(
            email = "john.c.calhoun@example.com",
            formState = AuthFormState(),
            onSubmit = { _, _, _ -> },
            onResend = {},
            onBackToLogin = {},
        )
    }
}
