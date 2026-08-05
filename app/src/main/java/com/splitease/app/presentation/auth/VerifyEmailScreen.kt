package com.splitease.app.presentation.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
fun VerifyEmailScreen(
    email: String,
    formState: AuthFormState,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val otpIsError = formState.errorMessage != null
    val displayEmail = remember(email) { truncateEmailForSubtitle(email) }
    val navy = SplitEaseColors.Navy
    val subtitleTemplate = stringResource(R.string.verify_email_subtitle, displayEmail)
    val subtitleAnnotated =
        remember(subtitleTemplate, displayEmail, navy) {
            buildAnnotatedString {
                val start = subtitleTemplate.indexOf(displayEmail)
                if (start < 0) {
                    append(subtitleTemplate)
                } else {
                    append(subtitleTemplate.substring(0, start))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = navy)) {
                        append(displayEmail)
                    }
                    append(subtitleTemplate.substring(start + displayEmail.length))
                }
            }
        }

    AuthScaffold(
        title = stringResource(R.string.verify_email_title),
        subtitleAnnotated = subtitleAnnotated,
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Top,
        onNavigateBack = onBackToLogin,
        showLoadingIndicator = false,
    ) {
        SeTextButton(
            text = stringResource(R.string.action_wrong_email),
            onClick = onBackToLogin,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        SegmentedOtpInput(
            value = code,
            onValueChange = { incoming ->
                code = incoming.filter { it.isDigit() }.take(AuthViewModel.SIGNUP_OTP_LENGTH)
            },
            onComplete = { completed ->
                code = completed
                if (!formState.isLoading) {
                    onVerify(completed)
                }
            },
            enabled = !formState.isLoading,
            isError = otpIsError,
            length = AuthViewModel.SIGNUP_OTP_LENGTH,
            horizontalAlignment = Alignment.Start,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_verify_code),
            onClick = { onVerify(code) },
            enabled = !formState.isLoading && code.length == AuthViewModel.SIGNUP_OTP_LENGTH,
            isLoading = formState.isLoading,
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

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun VerifyEmailScreenPreview() {
    SePreview {
        VerifyEmailScreen(
            email = "james.a.garfield@example.com",
            formState = AuthFormState(),
            onVerify = {},
            onResend = {},
            onBackToLogin = {},
        )
    }
}
