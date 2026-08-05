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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
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

    AuthScaffold(
        title = stringResource(R.string.signup_welcome_title),
        subtitle = stringResource(R.string.signup_welcome_subtitle),
        formState = formState,
        modifier = modifier,
        contentPlacement = AuthContentPlacement.Top,
        onNavigateBack = onBack,
        showLoadingIndicator = false,
    ) {
        var nameFieldHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_full_name),
                enabled = !formState.isLoading,
                modifier =
                    Modifier
                        .weight(1f)
                        .onSizeChanged { nameFieldHeightPx = it.height },
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
            val photoSize =
                if (nameFieldHeightPx > 0) {
                    with(density) { nameFieldHeightPx.toDp() }
                } else {
                    56.dp
                }
            ProfilePhotoButton(
                photoUri = photoUri,
                enabled = !formState.isLoading,
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.size(photoSize),
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
            isLoading = formState.isLoading,
        )
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
private fun ProfilePhotoButton(
    photoUri: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(56.dp),
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
            modifier
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
        modifier =
            Modifier
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
                        .padding(start = 12.dp),
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
