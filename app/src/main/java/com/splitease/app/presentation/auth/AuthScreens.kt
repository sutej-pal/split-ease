package com.splitease.app.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.components.SegmentedOtpInput
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeBackTitleRow
import com.splitease.app.presentation.ui.SeMessageHost
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import java.util.Currency
import java.util.Locale

private data class DialCodeOption(
    val flag: String,
    val code: String,
    val label: String,
)

private val DialCodeOptions =
    listOf(
        DialCodeOption("🇮🇳", "+91", "India"),
        DialCodeOption("🇺🇸", "+1", "United States"),
        DialCodeOption("🇬🇧", "+44", "United Kingdom"),
        DialCodeOption("🇨🇦", "+1", "Canada"),
        DialCodeOption("🇦🇺", "+61", "Australia"),
        DialCodeOption("🇦🇪", "+971", "United Arab Emirates"),
        DialCodeOption("🇸🇬", "+65", "Singapore"),
        DialCodeOption("🇩🇪", "+49", "Germany"),
        DialCodeOption("🇫🇷", "+33", "France"),
        DialCodeOption("🇯🇵", "+81", "Japan"),
    )

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
    onSignUp: (
        email: String,
        password: String,
        displayName: String,
        phoneCountryCode: String,
        phoneNumber: String,
        currencyCode: String,
        photoUri: String?,
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var dialCode by rememberSaveable { mutableStateOf("+91") }
    var dialFlag by rememberSaveable { mutableStateOf("🇮🇳") }
    var currencyCode by rememberSaveable { mutableStateOf(AppCurrencies.DEFAULT) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    var showDialPicker by rememberSaveable { mutableStateOf(false) }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            photoUri = uri?.toString()
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
        snackbarHost = {
            SeMessageHost(
                errorMessage = formState.errorMessage,
                infoMessage = formState.infoMessage,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            SeBackTitleRow(
                onBack = onBack,
                enabled = !formState.isLoading,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.signup_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.Navy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.signup_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = stringResource(R.string.label_full_name),
                    enabled = !formState.isLoading,
                    modifier = Modifier.weight(1f),
                    trailingIcon =
                        if (displayName.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { displayName = "" },
                                    enabled = !formState.isLoading,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.cd_clear_field),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
                Spacer(modifier = Modifier.width(12.dp))
                ProfilePhotoButton(
                    photoUri = photoUri,
                    enabled = !formState.isLoading,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            SeTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.label_email_address),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !formState.isLoading,
            )
            Spacer(modifier = Modifier.height(14.dp))
            PasswordSeTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !formState.isLoading,
                supportingText = stringResource(R.string.signup_password_hint),
            )
            Spacer(modifier = Modifier.height(14.dp))
            PhoneNumberRow(
                dialFlag = dialFlag,
                dialCode = dialCode,
                phoneNumber = phoneNumber,
                enabled = !formState.isLoading,
                onDialClick = { showDialPicker = true },
                onPhoneChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == ' ' } },
            )

            Spacer(modifier = Modifier.height(20.dp))
            CurrencyPreferenceLine(
                currencyCode = currencyCode,
                enabled = !formState.isLoading,
                onChangeClick = { showCurrencyPicker = true },
            )

            Spacer(modifier = Modifier.height(28.dp))
            SignupTermsText()
            Spacer(modifier = Modifier.height(16.dp))
            SePrimaryButton(
                text = stringResource(R.string.action_signup),
                onClick = {
                    onSignUp(
                        email.trim(),
                        password,
                        displayName.trim(),
                        dialCode,
                        phoneNumber.trim(),
                        currencyCode,
                        photoUri,
                    )
                },
                enabled = !formState.isLoading,
            )
            if (formState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selected = currencyCode,
            onSelect = {
                currencyCode = it
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
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

@Composable
fun ForgotPasswordScreen(
    formState: AuthFormState,
    onSendReset: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    val emailError = formState.errorMessage

    AuthScaffold(
        title = stringResource(R.string.forgot_title),
        subtitle = stringResource(R.string.forgot_subtitle),
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Upper,
        onNavigateBack = onNavigateBack,
        showLoadingIndicator = false,
    ) {
        SeTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !formState.isLoading,
            isError = emailError != null,
            supportingText = emailError,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_send_reset),
            onClick = { onSendReset(email.trim()) },
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

/**
 * After a reset OTP is emailed: enter the code and choose a new password.
 */
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
            it == AuthMessages.RESET_PASSWORD_REQUIREMENTS ||
                it == AuthMessages.PASSWORD_SHORT
        }
    val confirmPasswordError =
        formState.errorMessage.takeIf { it == AuthMessages.RESET_PASSWORD_MISMATCH }
    val passwordRules = remember(newPassword) { PasswordRules.evaluate(newPassword) }
    val canSubmit =
        !formState.isLoading &&
            passwordRules.allMet &&
            confirmPassword.isNotEmpty() &&
            newPassword == confirmPassword &&
            (otpReady || code.length == AuthViewModel.SIGNUP_OTP_LENGTH)
    val subtitleBefore = stringResource(R.string.reset_password_subtitle_before)
    val subtitleAfter = stringResource(R.string.reset_password_subtitle_after)
    val navy = SplitEaseColors.Navy
    val displayEmail = remember(email) { truncateEmailForSubtitle(email) }
    val subtitleAnnotated =
        remember(displayEmail, subtitleBefore, subtitleAfter, navy) {
            buildAnnotatedString {
                // Space is added in code — AAPT strips trailing whitespace from XML string resources.
                append(subtitleBefore.trimEnd())
                append(' ')
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = navy)) {
                    append(displayEmail)
                }
                append(subtitleAfter)
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
        SeTextButton(
            text = stringResource(R.string.action_wrong_email),
            onClick = onBackToLogin,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
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
        PasswordRequirementsChecklist(rules = passwordRules)
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
            modifier = Modifier.align(Alignment.Start)
        )
        SeTextButton(
            text = stringResource(R.string.action_back_to_login),
            onClick = onBackToLogin,
            enabled = !formState.isLoading,
            modifier = Modifier.align(Alignment.Start)
        )
    }
}

private data class PasswordRules(
    val hasMinLength: Boolean,
    val hasUpperAndLower: Boolean,
    val hasDigit: Boolean,
) {
    val allMet: Boolean get() = hasMinLength && hasUpperAndLower && hasDigit

    companion object {
        fun evaluate(password: String): PasswordRules =
            PasswordRules(
                hasMinLength = password.length >= AuthViewModel.MIN_SIGNUP_PASSWORD_LENGTH,
                hasUpperAndLower =
                    password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
                hasDigit = password.any { it.isDigit() },
            )
    }
}

@Composable
private fun PasswordRequirementsChecklist(
    rules: PasswordRules,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PasswordRequirementRow(
            text = stringResource(R.string.reset_password_rule_length),
            met = rules.hasMinLength,
        )
        PasswordRequirementRow(
            text = stringResource(R.string.reset_password_rule_case),
            met = rules.hasUpperAndLower,
        )
        PasswordRequirementRow(
            text = stringResource(R.string.reset_password_rule_number),
            met = rules.hasDigit,
        )
    }
}

@Composable
private fun PasswordRequirementRow(
    text: String,
    met: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(18.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (met) SplitEaseColors.Primary else SplitEaseColors.OutlineStrong,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (met) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SplitEaseColors.Primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (met) SplitEaseColors.Navy else SplitEaseColors.NavyMuted,
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
    val otpIsError = formState.errorMessage != null

    AuthScaffold(
        title = stringResource(R.string.verify_email_title),
        subtitle = stringResource(R.string.verify_email_subtitle, truncateEmailForSubtitle(email)),
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Upper,
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
        )
        Spacer(modifier = Modifier.height(16.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_verify_code),
            onClick = { onVerify(code) },
            enabled =
                !formState.isLoading && code.length == AuthViewModel.SIGNUP_OTP_LENGTH,
            isLoading = formState.isLoading,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // No resendCooldown on AuthFormState — keep resend enabled when not loading.
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
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val resolvedLabel =
        label ?: if (placeholder == null) stringResource(R.string.label_password) else null
    SeTextField(
        value = value,
        onValueChange = onValueChange,
        label = resolvedLabel,
        placeholder = placeholder,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        supportingText = supportingText,
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
private fun ProfilePhotoButton(
    photoUri: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap =
        remember(photoUri) {
            photoUri?.let { raw ->
                runCatching {
                    AvatarImageIO
                        .decodeScaled(
                            context = context,
                            photoUrl = raw,
                            maxSidePx = AvatarImageIO.PREVIEW_MAX_SIDE_PX,
                        )
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(1.dp, SplitEaseColors.OutlineStrong, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.cd_add_profile_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = stringResource(R.string.cd_add_profile_photo),
                tint = SplitEaseColors.NavyMuted,
            )
        }
    }
}

@Composable
private fun PhoneNumberRow(
    dialFlag: String,
    dialCode: String,
    phoneNumber: String,
    enabled: Boolean,
    onDialClick: () -> Unit,
    onPhoneChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        label = { Text(stringResource(R.string.label_phone_number)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        leadingIcon = {
            Row(
                modifier =
                    Modifier
                        .clickable(enabled = enabled, onClick = onDialClick)
                        .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$dialFlag $dialCode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) SplitEaseColors.Navy else SplitEaseColors.NavyMuted,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.signup_pick_country_title),
                    tint = SplitEaseColors.NavyMuted,
                )
                Box(
                    modifier =
                        Modifier
                            .padding(start = 8.dp, end = 4.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(SplitEaseColors.OutlineStrong),
                )
            }
        },
        shape = shape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitEaseColors.Primary,
                unfocusedBorderColor = SplitEaseColors.OutlineStrong,
                disabledBorderColor = SplitEaseColors.OutlineStrong.copy(alpha = 0.5f),
                focusedLabelColor = SplitEaseColors.Primary,
                unfocusedLabelColor = SplitEaseColors.NavyMuted,
                cursorColor = SplitEaseColors.Primary,
                focusedTextColor = SplitEaseColors.Navy,
                unfocusedTextColor = SplitEaseColors.Navy,
                focusedContainerColor = SplitEaseColors.Surface,
                unfocusedContainerColor = SplitEaseColors.Surface,
                disabledContainerColor = SplitEaseColors.Surface,
                errorContainerColor = SplitEaseColors.Surface,
            ),
    )
}

@Composable
private fun CurrencyPreferenceLine(
    currencyCode: String,
    enabled: Boolean,
    onChangeClick: () -> Unit,
) {
    val symbol =
        remember(currencyCode) {
            runCatching {
                Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
            }.getOrElse { currencyCode }
        }
    val currencyLabel = "$currencyCode ($symbol)"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.signup_currency_line, currencyLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.Navy,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.signup_currency_change),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Primary,
            modifier = Modifier.clickable(enabled = enabled, onClick = onChangeClick),
        )
    }
}

@Composable
private fun SignupTermsText() {
    val termsLabel = stringResource(R.string.signup_terms_of_service)
    val privacyLabel = stringResource(R.string.signup_privacy_policy)
    val termsUrl = stringResource(R.string.signup_terms_url)
    val privacyUrl = stringResource(R.string.signup_privacy_url)
    val linkStyle =
        TextLinkStyles(
            style =
                SpanStyle(
                    color = SplitEaseColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    val annotated =
        buildAnnotatedString {
            append("By signing up, you accept the SplitEase ")
            withLink(LinkAnnotation.Url(termsUrl, linkStyle)) {
                append(termsLabel)
            }
            append(" and ")
            withLink(LinkAnnotation.Url(privacyUrl, linkStyle)) {
                append(privacyLabel)
            }
            append(".")
        }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CurrencyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val options = remember(filter) { AppCurrencies.filter(filter) }
    SeModal(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.85f)) {
        SeModalTitle(stringResource(R.string.signup_pick_currency_title))
        Spacer(modifier = Modifier.height(12.dp))
        SeTextField(
            value = filter,
            onValueChange = { filter = it },
            label = stringResource(R.string.settings_currency_search),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
        ) {
            items(options, key = { it.first }) { (code, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == code,
                        onClick = { onSelect(code) },
                        colors = RadioButtonDefaults.colors(selectedColor = SplitEaseColors.Primary),
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(text = code, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = SplitEaseColors.Outline)
            }
        }
        SeTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
    }
}

@Composable
private fun DialCodePickerDialog(
    selectedCode: String,
    selectedFlag: String,
    onSelect: (DialCodeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    SeModal(onDismissRequest = onDismiss) {
        SeModalTitle(stringResource(R.string.signup_pick_country_title))
        Spacer(modifier = Modifier.height(8.dp))
        DialCodeOptions.forEach { option ->
            val selected = option.code == selectedCode && option.flag == selectedFlag
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelect(option) },
                    colors = RadioButtonDefaults.colors(selectedColor = SplitEaseColors.Primary),
                )
                Text(
                    text = "${option.flag}  ${option.code}  ${option.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitEaseColors.Navy,
                )
            }
        }
        SeTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
    }
}

private enum class AuthContentPlacement {
    /** Vertically centered (login). */
    Center,

    /** Content block starts near ~40% from the top (forgot / OTP). */
    Upper,

    /** Top-aligned scrollable form (reset password). */
    Top,
}

/**
 * Keeps reset-password subtitle height stable for long addresses.
 * Example: `Latarcha.Rabon@VeryLongDomainName.com` → `Latarcha.Rabon@VeryLon…com`
 */
private fun truncateEmailForSubtitle(
    email: String,
    maxLen: Int = 32,
): String {
    val trimmed = email.trim()
    if (trimmed.length <= maxLen) return trimmed
    val at = trimmed.lastIndexOf('@')
    if (at <= 0) return trimmed.take(maxLen - 1) + "…"
    val local = trimmed.substring(0, at)
    val domain = trimmed.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    val tld = if (dot >= 0) domain.substring(dot) else ""
    val domainBody = if (dot > 0) domain.substring(0, dot) else domain
    // local + '@' + body + '…' + tld
    val fixed = 1 + 1 + tld.length // @ + … + tld
    val localKeep = local.length.coerceAtMost((maxLen - fixed - 4).coerceAtLeast(4))
    val bodyBudget = (maxLen - localKeep - fixed).coerceAtLeast(3)
    val localShown =
        if (local.length <= localKeep) local else local.take(localKeep - 1) + "…"
    val bodyShown =
        if (domainBody.length <= bodyBudget) {
            domainBody
        } else {
            domainBody.take(bodyBudget) + "…"
        }
    return "$localShown@$bodyShown$tld"
}

@Composable
private fun AuthScaffold(
    title: String,
    formState: AuthFormState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleAnnotated: AnnotatedString? = null,
    contentPlacement: AuthContentPlacement = AuthContentPlacement.Center,
    onNavigateBack: (() -> Unit)? = null,
    showLoadingIndicator: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasBack = onNavigateBack != null
    val headerAlign = if (hasBack) TextAlign.Start else TextAlign.Center
    val contentHAlign = if (hasBack) Alignment.Start else Alignment.CenterHorizontally

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SeMessageHost(
                errorMessage = formState.errorMessage,
                infoMessage = formState.infoMessage,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            horizontalAlignment = contentHAlign,
        ) {
            if (onNavigateBack != null) {
                // Back alone on the top row; title lives in AuthScaffoldHeader below.
                SeBackTitleRow(
                    onBack = onNavigateBack,
                    enabled = !formState.isLoading,
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            when (contentPlacement) {
                AuthContentPlacement.Center -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = contentHAlign,
                    ) {
                        AuthScaffoldHeader(
                            title = title,
                            subtitle = subtitle,
                            subtitleAnnotated = subtitleAnnotated,
                            textAlign = headerAlign,
                        )
                        content()
                        if (showLoadingIndicator && formState.isLoading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                    }
                }

                AuthContentPlacement.Upper -> {
                    // ~40% from top: lead spacer ~0.32 of remaining height, trail takes the rest.
                    Spacer(modifier = Modifier.weight(0.32f))
                    AuthScaffoldHeader(
                        title = title,
                        subtitle = subtitle,
                        subtitleAnnotated = subtitleAnnotated,
                        textAlign = headerAlign,
                    )
                    content()
                    if (showLoadingIndicator && formState.isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.weight(0.68f))
                }

                AuthContentPlacement.Top -> {
                    // Single scroll surface (avoids nested weight+scroll edge artifacts).
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        horizontalAlignment = contentHAlign,
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AuthScaffoldHeader(
                            title = title,
                            subtitle = subtitle,
                            subtitleAnnotated = subtitleAnnotated,
                            textAlign = headerAlign,
                        )
                        content()
                        if (showLoadingIndicator && formState.isLoading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScaffoldHeader(
    title: String,
    subtitle: String?,
    subtitleAnnotated: AnnotatedString? = null,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = SplitEaseColors.Navy,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
    )
    if (subtitleAnnotated != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitleAnnotated,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (subtitle != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
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

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun SignUpScreenPreview() {
    SePreview {
        SignUpScreen(
            formState = AuthFormState(),
            onSignUp = { _, _, _, _, _, _, _ -> },
            onBack = {},
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
