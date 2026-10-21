package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightBrush
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.core.DialCodeOption
import com.splitease.app.core.DialCodes
import com.splitease.app.presentation.theme.SeBodyLarge
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Signup-style phone field: flag and dial code as the leading control, national number beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberRow(
    dialFlag: String,
    dialCode: String,
    phoneNumber: String,
    enabled: Boolean,
    onDialClick: () -> Unit,
    onPhoneChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    fieldHeight: Dp? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SplitEaseColors.Primary,
            unfocusedBorderColor = SplitEaseColors.OutlineStrong,
            disabledBorderColor = SplitEaseColors.OutlineStrong.copy(alpha = 0.5f),
            focusedLabelColor = SplitEaseColors.Primary,
            unfocusedLabelColor = SplitEaseColors.NavyMuted,
            cursorColor = SplitEaseColors.Primary,
            focusedTextColor = SplitEaseColors.Navy,
            unfocusedTextColor = SplitEaseColors.Navy,
            disabledTextColor = SplitEaseColors.Navy.copy(alpha = 0.55f),
            focusedContainerColor = SplitEaseColors.Surface,
            unfocusedContainerColor = SplitEaseColors.Surface,
            disabledContainerColor = SplitEaseColors.Surface,
            errorContainerColor = SplitEaseColors.Surface,
        )

    val leadingIconContent: @Composable () -> Unit = {
        val dialColor = if (enabled) SplitEaseColors.Navy else SplitEaseColors.NavyMuted
        Row(
            modifier =
                Modifier
                    .clickable(enabled = enabled, onClick = onDialClick)
                    .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fieldHeight != null) {
                SeBodySmall(
                    text = "$dialFlag $dialCode",
                    color = dialColor,
                )
            } else {
                SeBodyLarge(
                    text = "$dialFlag $dialCode",
                    color = dialColor,
                )
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.signup_pick_country_title),
                tint = SplitEaseColors.IconDefault,
                modifier = Modifier.size(if (fieldHeight != null) 18.dp else 20.dp),
            )
            Box(
                modifier =
                    Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .width(1.dp)
                        .height(if (fieldHeight != null) 18.dp else 28.dp)
                        .background(SplitEaseColors.OutlineStrong),
            )
        }
    }

    if (fieldHeight == null) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(shape),
            label = { SeBodyMedium(stringResource(R.string.label_phone_number)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
            leadingIcon = leadingIconContent,
            shape = shape,
            colors = colors,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val textColor = if (enabled) SplitEaseColors.Navy else SplitEaseColors.Navy.copy(alpha = 0.55f)
        val mergedTextStyle = MaterialTheme.typography.bodySmall.copy(color = textColor)

        CompositionLocalProvider(LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)) {
            BasicTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .clip(shape),
                enabled = enabled,
                singleLine = true,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
                textStyle = mergedTextStyle,
                cursorBrush = SolidColor(SplitEaseColors.Primary),
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = phoneNumber,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        isError = false,
                        label = null,
                        placeholder = {
                            SeBodySmall(
                                text = stringResource(R.string.account_phone_label),
                                color = SplitEaseColors.NavyMuted,
                            )
                        },
                        leadingIcon = leadingIconContent,
                        supportingText = null,
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = enabled,
                                isError = false,
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
fun DialCodePickerDialog(
    selectedCode: String,
    selectedFlag: String,
    onSelect: (DialCodeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredOptions =
        remember(searchQuery) {
            DialCodes.options.filter { option ->
                searchQuery.isBlank() ||
                    option.label.contains(searchQuery, ignoreCase = true) ||
                    option.code.contains(searchQuery, ignoreCase = true)
            }
        }

    SeModal(
        title = stringResource(R.string.signup_pick_country_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SeLayout.screenHorizontal),
        ) {
            SeTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.settings_country_search),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                filteredOptions.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeBodyLarge(
                            text = "${option.flag} ${option.label}",
                            modifier = Modifier.weight(1f),
                        )
                        SeBodyMedium(
                            text = option.code,
                            color = SplitEaseColors.NavyMuted,
                        )
                        RadioButton(
                            selected = option.code == selectedCode,
                            onClick = { onSelect(option) },
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = SplitEaseColors.Primary,
                                ),
                        )
                    }
                }
            }
        }
    }
}
