package com.splitease.app.presentation.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeMessageHost
import com.splitease.app.presentation.ui.seScreenSubtitleStyle
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBar

internal enum class AuthContentPlacement {
    Center,
    Top,
}

internal fun truncateEmailForSubtitle(
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
    val fixed = 1 + 1 + tld.length
    val localKeep = local.length.coerceAtMost((maxLen - fixed - 4).coerceAtLeast(4))
    val bodyBudget = (maxLen - localKeep - fixed).coerceAtLeast(3)
    val localShown = if (local.length <= localKeep) local else local.take(localKeep - 1) + "…"
    val bodyShown =
        if (domainBody.length <= bodyBudget) {
            domainBody
        } else {
            domainBody.take(bodyBudget) + "…"
        }
    return "$localShown@$bodyShown$tld"
}

@Composable
internal fun AuthScaffold(
    title: String,
    formState: AuthFormState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleAnnotated: AnnotatedString? = null,
    contentPlacement: AuthContentPlacement = AuthContentPlacement.Center,
    onNavigateBack: (() -> Unit)? = null,
    showLoadingIndicator: Boolean = true,
    showErrorInSnackbar: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasBack = onNavigateBack != null
    val headerAlign = if (hasBack) TextAlign.Start else TextAlign.Center
    val contentHAlign = if (hasBack) Alignment.Start else Alignment.CenterHorizontally
    // Top+back: title lives in the body as a page heading (not chrome beside the
    // chevron) so it doesn't read like another bordered field row.
    val titleInBody =
        !hasBack || contentPlacement == AuthContentPlacement.Top
    val topBarTitle =
        if (hasBack && contentPlacement == AuthContentPlacement.Center) title else ""
    val bg = MaterialTheme.colorScheme.background
    val lightGlyphs = bg.luminance() > 0.5f

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = lightGlyphs,
        navigationBarDarkIcons = lightGlyphs,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = bg,
        snackbarHost = {
            SeMessageHost(
                errorMessage = formState.errorMessage.takeIf { showErrorInSnackbar },
                infoMessage = formState.infoMessage,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = SeLayout.screenBottom),
            horizontalAlignment = contentHAlign,
        ) {
            // Single chrome: SeTopBar (Scaffold already applied status-bar padding).
            if (onNavigateBack != null) {
                SeTopBar(
                    title = topBarTitle,
                    onBack = {
                        if (!formState.isLoading) onNavigateBack()
                    },
                    enabled = !formState.isLoading,
                    consumeWindowInsets = false,
                    horizontalPadding = SeLayout.screenHorizontal,
                )
            }

            when (contentPlacement) {
                AuthContentPlacement.Center -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = SeLayout.screenHorizontal),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = contentHAlign,
                    ) {
                        AuthScaffoldHeader(
                            title = title.takeIf { titleInBody },
                            subtitle = subtitle,
                            subtitleAnnotated = subtitleAnnotated,
                            textAlign = headerAlign,
                        )
                        content()
                        if (showLoadingIndicator && formState.isLoading) {
                            Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                            CircularProgressIndicator()
                        }
                    }
                }
                AuthContentPlacement.Top -> {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = SeLayout.screenHorizontal)
                                .verticalScroll(rememberScrollState()),
                        horizontalAlignment = contentHAlign,
                    ) {
                        if (onNavigateBack == null) {
                            Spacer(modifier = Modifier.height(SeLayout.screenTop))
                        }
                        AuthScaffoldHeader(
                            title = title.takeIf { titleInBody },
                            subtitle = subtitle,
                            subtitleAnnotated = subtitleAnnotated,
                            textAlign = headerAlign,
                        )
                        content()
                        if (showLoadingIndicator && formState.isLoading) {
                            Spacer(modifier = Modifier.height(SeLayout.sectionGap))
                            CircularProgressIndicator()
                        }
                        Spacer(modifier = Modifier.height(SeLayout.itemGap))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScaffoldHeader(
    title: String?,
    subtitle: String?,
    subtitleAnnotated: AnnotatedString? = null,
    textAlign: TextAlign = TextAlign.Center,
) {
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = SplitEaseColors.Navy,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (subtitleAnnotated != null) {
        Spacer(modifier = Modifier.height(SeLayout.titleToSubtitle))
        Text(
            text = subtitleAnnotated,
            style = seScreenSubtitleStyle(),
            textAlign = textAlign,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SeLayout.headerToContent))
    } else if (subtitle != null) {
        Spacer(modifier = Modifier.height(SeLayout.titleToSubtitle))
        Text(
            text = subtitle,
            style = seScreenSubtitleStyle(),
            textAlign = textAlign,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SeLayout.headerToContent))
    } else {
        Spacer(modifier = Modifier.height(SeLayout.headerToContent))
    }
}

private data class PasswordRules(
    val hasMinLength: Boolean,
    val hasUpperAndLower: Boolean,
    val hasDigit: Boolean,
) {
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
internal fun PasswordRequirementsChecklist(
    password: String,
    modifier: Modifier = Modifier,
) {
    val rules = PasswordRules.evaluate(password)
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
internal fun PasswordSeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val resolvedLabel = label ?: if (placeholder == null) stringResource(R.string.label_password) else null
    SeTextField(
        value = value,
        onValueChange = onValueChange,
        label = resolvedLabel,
        placeholder = placeholder,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        supportingText = supportingText,
        trailingIcon = {
            val icon =
                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
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
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        },
    )
}
