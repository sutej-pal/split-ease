package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

private val SeTextFieldShape = RoundedCornerShape(12.dp)

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
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // Clip so password-manager autofill highlight (e.g. Bitwarden yellow)
        // respects the same rounded corners as the outline.
        modifier = modifier.fillMaxWidth().clip(SeTextFieldShape),
        label = label?.let { text -> { Text(text) } },
        placeholder =
            placeholder?.let { text ->
                {
                    Text(text = text, color = SplitEaseColors.NavyMuted)
                }
            },
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        supportingText =
            supportingText?.let { hint ->
                {
                    Text(
                        text = hint,
                        color =
                            if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            },
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
                focusedContainerColor = SplitEaseColors.Surface,
                unfocusedContainerColor = SplitEaseColors.Surface,
                disabledContainerColor = SplitEaseColors.SurfaceMuted,
                errorContainerColor = SplitEaseColors.Surface,
                errorSupportingTextColor = MaterialTheme.colorScheme.error,
            ),
    )
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
