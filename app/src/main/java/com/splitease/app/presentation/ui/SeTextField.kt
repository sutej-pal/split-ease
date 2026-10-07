package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightBrush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.*

private val SeTextFieldShape = RoundedCornerShape(14.dp)

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
) {
    CompositionLocalProvider(LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // Clip so password-manager autofill highlight respects rounded corners.
            modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).clip(SeTextFieldShape),
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
                ),
        )
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
