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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

@Composable
fun SeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitEaseColors.Primary,
                unfocusedBorderColor = SplitEaseColors.OutlineStrong,
                focusedLabelColor = SplitEaseColors.Primary,
                unfocusedLabelColor = SplitEaseColors.NavyMuted,
                cursorColor = SplitEaseColors.Primary,
                focusedTextColor = SplitEaseColors.Navy,
                unfocusedTextColor = SplitEaseColors.Navy,
                focusedContainerColor = SplitEaseColors.Surface,
                unfocusedContainerColor = SplitEaseColors.Surface,
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
