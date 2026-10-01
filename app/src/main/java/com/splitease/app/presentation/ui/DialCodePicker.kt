package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.core.DialCodeOption
import com.splitease.app.core.DialCodes
import com.splitease.app.presentation.theme.SplitEaseColors

private val FieldShape = RoundedCornerShape(10.dp)

/**
 * Signup-style phone field: flag and dial code as the leading control, national number beside it.
 */
@Composable
fun PhoneNumberRow(
    dialFlag: String,
    dialCode: String,
    phoneNumber: String,
    enabled: Boolean,
    onDialClick: () -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneChange,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FieldShape),
        label = { Text(stringResource(R.string.label_phone_number)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
        leadingIcon = {
            Row(
                modifier =
                    Modifier
                        .clickable(enabled = enabled, onClick = onDialClick)
                        .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$dialFlag $dialCode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) SplitEaseColors.Navy else SplitEaseColors.NavyMuted,
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.signup_pick_country_title),
                    tint = SplitEaseColors.NavyMuted,
                    modifier = Modifier.size(20.dp),
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
        shape = FieldShape,
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

/** Same country list used on the signup screen. */
@Composable
fun DialCodePickerDialog(
    selectedCode: String,
    selectedFlag: String,
    onSelect: (DialCodeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    SeModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.signup_pick_country_title),
        icon = Icons.Filled.Public,
        dismissLabel = stringResource(R.string.action_cancel),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            DialCodes.options.forEach { option ->
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
        }
    }
}
