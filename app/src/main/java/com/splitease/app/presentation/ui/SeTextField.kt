package com.splitease.app.presentation.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightBrush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SplitEaseColors

private val SeTextFieldShape = RoundedCornerShape(8.dp)

/**
 * Custom text field for SplitEase screens.
 *
 * @param cornerRadius Corner radius for shape and clip. Defaults to 14.dp.
 * @param fieldHeight Optional fixed height for compact mode (e.g. 38.dp). When set, renders a compact
 * variant using [BasicTextField] and [OutlinedTextFieldDefaults.DecorationBox]. Compact mode
 * supports placeholder, trailingIcon, isError, and visualTransformation; label and supportingText
 * are not supported in compact mode and are ignored when fieldHeight is non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    cornerRadius: Dp = 14.dp,
    fieldHeight: Dp? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SplitEaseColors.Primary,
            unfocusedBorderColor = SplitEaseColors.OutlineStrong,
            disabledBorderColor = SplitEaseColors.OutlineStrong.copy(alpha = 0.5f),
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = SplitEaseColors.Primary,
            unfocusedLabelColor = SplitEaseColors.NavyMuted,
            disabledLabelColor = SplitEaseColors.NavyMuted.copy(alpha = 0.6f),
            errorLabelColor = MaterialTheme.colorScheme.error,
            cursorColor = SplitEaseColors.Primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
            focusedTextColor = SplitEaseColors.Navy,
            unfocusedTextColor = SplitEaseColors.Navy,
            disabledTextColor = SplitEaseColors.Navy.copy(alpha = 0.55f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
        )

    CompositionLocalProvider(LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)) {
        if (fieldHeight == null) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                // Clip so password-manager autofill highlight respects rounded corners.
                modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape),
                label = label?.let { text -> { SeBodyMedium(text) } },
                placeholder =
                    placeholder?.let { text ->
                        {
                            SeBodyMedium(text = text, color = SplitEaseColors.NavyMuted)
                        }
                    },
                enabled = enabled,
                singleLine = singleLine,
                isError = isError,
                keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
                visualTransformation = visualTransformation,
                supportingText =
                    supportingText?.let { hint ->
                        {
                            SeBodySmall(
                                text = hint,
                                color =
                                    if (isError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        SplitEaseColors.NavyMuted
                                    },
                            )
                        }
                    },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                shape = shape,
                colors = colors,
            )
        } else {
            val interactionSource = remember { MutableInteractionSource() }
            val textColor = if (enabled) SplitEaseColors.Navy else SplitEaseColors.Navy.copy(alpha = 0.55f)
            val mergedTextStyle = MaterialTheme.typography.bodySmall.copy(color = textColor)

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .clip(shape),
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
                textStyle = mergedTextStyle,
                cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else SplitEaseColors.Primary),
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        isError = isError,
                        label = null,
                        placeholder =
                            placeholder?.let { text ->
                                {
                                    SeBodySmall(text = text, color = SplitEaseColors.NavyMuted)
                                }
                            },
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                        supportingText = null,
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = enabled,
                                isError = isError,
                                interactionSource = interactionSource,
                                colors = colors,
                                shape = shape,
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
fun SeFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().height(52.dp).clip(SeTextFieldShape),
            label = label?.let { text -> { SeBodyMedium(text) } },
            placeholder =
                placeholder?.let { text ->
                    {
                        SeBodyMedium(text = text, color = SplitEaseColors.NavyMuted)
                    }
                },
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            keyboardOptions = keyboardOptions.copy(autoCorrectEnabled = false),
            visualTransformation = visualTransformation,
            supportingText =
                supportingText?.let { hint ->
                    {
                        SeBodySmall(
                            text = hint,
                            color =
                                if (isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    SplitEaseColors.NavyMuted
                                },
                        )
                    }
                },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = SeTextFieldShape,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    focusedLabelColor = SplitEaseColors.Primary,
                    unfocusedLabelColor = SplitEaseColors.NavyMuted,
                    disabledLabelColor = SplitEaseColors.NavyMuted.copy(alpha = 0.6f),
                    errorLabelColor = MaterialTheme.colorScheme.error,
                    cursorColor = SplitEaseColors.Primary,
                    errorCursorColor = MaterialTheme.colorScheme.error,
                    focusedTextColor = SplitEaseColors.Navy,
                    unfocusedTextColor = SplitEaseColors.Navy,
                    disabledTextColor = SplitEaseColors.Navy.copy(alpha = 0.55f),
                    focusedContainerColor = SplitEaseColors.SurfaceMuted,
                    unfocusedContainerColor = SplitEaseColors.SurfaceMuted,
                    disabledContainerColor = SplitEaseColors.SurfaceMuted,
                    errorContainerColor = SplitEaseColors.SurfaceMuted,
                    errorSupportingTextColor = MaterialTheme.colorScheme.error,
                ),
        )
    }
}

@Preview(name = "Text fields", showBackground = true)
@Composable
private fun SeTextFieldPreview() {
    SePreview {
        Column {
            SeTextField(value = "Weekend trip", onValueChange = {}, label = "Group name")
            Spacer(modifier = Modifier.height(12.dp))
            SeTextField(value = "", onValueChange = {}, label = "Email")
        }
    }
}

@Preview(name = "Compact text field · Light", showBackground = true)
@Composable
private fun CompactTextFieldLightPreview() {
    SePreview {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Empty
            SeTextField(
                value = "",
                onValueChange = {},
                placeholder = "Current password",
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Filled
            SeTextField(
                value = "MySecret123",
                onValueChange = {},
                placeholder = "New password",
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Error
            SeTextField(
                value = "123",
                onValueChange = {},
                placeholder = "New password",
                isError = true,
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Password-masked
            SeTextField(
                value = "SecretPass",
                onValueChange = {},
                placeholder = "Password",
                visualTransformation = PasswordVisualTransformation(),
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
        }
    }
}

@Preview(name = "Compact text field · Dark", showBackground = true)
@Composable
private fun CompactTextFieldDarkPreview() {
    SePreview(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Empty
            SeTextField(
                value = "",
                onValueChange = {},
                placeholder = "Current password",
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Filled
            SeTextField(
                value = "MySecret123",
                onValueChange = {},
                placeholder = "New password",
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Error
            SeTextField(
                value = "123",
                onValueChange = {},
                placeholder = "New password",
                isError = true,
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
            // Password-masked
            SeTextField(
                value = "SecretPass",
                onValueChange = {},
                placeholder = "Password",
                visualTransformation = PasswordVisualTransformation(),
                cornerRadius = 8.dp,
                fieldHeight = 38.dp,
            )
        }
    }
}
