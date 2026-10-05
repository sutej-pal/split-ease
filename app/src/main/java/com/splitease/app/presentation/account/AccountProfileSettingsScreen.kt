package com.splitease.app.presentation.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeBodySmall
import com.splitease.app.presentation.theme.SeLabelSmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.DialCodePickerDialog
import com.splitease.app.presentation.ui.PhoneNumberRow
import com.splitease.app.presentation.ui.SeAccordionRow
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeListRowLabelStyle
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.seEntityHeaderStyle

@Composable
fun AccountProfileSettingsScreen(
    onBack: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenCloseAccount: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currency by viewModel.currencyCode.collectAsStateWithLifecycle()
    val locale by viewModel.appLocale.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currencyLabel = AppCurrencies.labelOf(currency)
    var draftHydrated by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    var showSignOutAllConfirm by remember { mutableStateOf(false) }
    var emailToRemoveId by remember { mutableStateOf<String?>(null) }
    var showDialCodePicker by remember { mutableStateOf(false) }

    val nameError = showValidation && settings.displayNameDraft.isBlank()
    val canSaveName =
        settings.displayNameDraft != profile.displayName && settings.displayNameDraft.isNotBlank()
    val photoPicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.account_photo_source_title),
            sourceBody = stringResource(R.string.account_photo_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.Avatar,
            onCropped = viewModel::updatePhoto,
        )

    LaunchedEffect(profile.displayName, profile.email) {
        if (!draftHydrated && (profile.displayName.isNotBlank() || profile.email.isNotBlank())) {
            viewModel.syncSettingsDraftFromProfile()
            draftHydrated = true
        }
    }

    LaunchedEffect(settings.infoMessage, settings.errorMessage) {
        val message = settings.infoMessage ?: settings.errorMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    SeScreen(
        title = stringResource(R.string.account_profile_settings_title),
        onBack = onBack,
        actions = {
            if (canSaveName) {
                SeTextButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        showValidation = true
                        if (settings.displayNameDraft.isBlank()) return@SeTextButton
                        viewModel.saveDisplayName()
                    },
                    enabled = !settings.isSaving,
                    isLoading = settings.isSaving,
                    emphasized = true,
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding.values)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            AccountProfileHero(
                displayName = settings.displayNameDraft,
                photoUrl = profile.photoUrl,
                isBusy = settings.isSaving,
                enabled = !settings.isSaving,
                isError = nameError,
                onDisplayNameChange = viewModel::onDisplayNameDraftChange,
                onChangePhoto = photoPicker::launch,
            )

            Spacer(modifier = Modifier.height(28.dp))
            SeSectionHeader(text = stringResource(R.string.account_sign_in_contact_section))
            AccountSettingsCard {
                // Email Row
                val emailValue = profile.email
                val hasSecondary = settings.secondaryEmails.isNotEmpty()
                SeAccordionRow(
                    label = stringResource(R.string.account_email_label),
                    value = emailValue,
                    expanded = settings.expandedRow == AccountRow.EMAIL,
                    onToggle = { viewModel.toggleRow(AccountRow.EMAIL) },
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Email,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                    trailingChip = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!profile.emailConfirmed) {
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SplitEaseColors.YouOwe.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    SeLabelSmall(
                                        text = stringResource(R.string.account_unconfirmed_chip),
                                        color = SplitEaseColors.YouOwe,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            if (hasSecondary) {
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SplitEaseColors.PrimarySoft)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    SeLabelSmall(
                                        text = "+${settings.secondaryEmails.size}",
                                        color = SplitEaseColors.Primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Primary email item
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    SeBodyMedium(
                                        text = profile.email,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SplitEaseColors.Navy,
                                    )
                                    SeBodySmall(
                                        text = stringResource(R.string.account_primary_label),
                                        color = SplitEaseColors.NavyMuted,
                                    )
                                }
                                if (!profile.emailConfirmed) {
                                    SeTextButton(
                                        text = stringResource(R.string.account_resend_action),
                                        onClick = viewModel::resendPrimaryVerification,
                                    )
                                }
                            }
                            if (!profile.emailConfirmed && settings.showPrimaryOtpInput) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SeTextField(
                                        value = settings.primaryOtpDraft,
                                        onValueChange = viewModel::onPrimaryOtpDraftChange,
                                        placeholder = "6-digit code",
                                        modifier = Modifier.weight(1f),
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = viewModel::verifyPrimaryEmail,
                                        isLoading = settings.isEmailSaving,
                                    )
                                }
                            }
                        }

                        // Secondary emails list
                        settings.secondaryEmails.forEach { sec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    SeBodyMedium(
                                        text = sec.email,
                                        fontWeight = FontWeight.Medium,
                                        color = SplitEaseColors.Navy,
                                    )
                                    SeBodySmall(
                                        text = if (sec.isVerified) {
                                            stringResource(R.string.account_secondary_label)
                                        } else {
                                            stringResource(R.string.account_unconfirmed_chip)
                                        },
                                        color = if (sec.isVerified) SplitEaseColors.NavyMuted else SplitEaseColors.YouOwe,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!sec.isVerified) {
                                        SeTextButton(
                                            text = stringResource(R.string.account_resend_action),
                                            onClick = { viewModel.resendSecondaryEmail(sec.id) },
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.account_remove_action),
                                        tint = SplitEaseColors.YouOwe,
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clickable { emailToRemoveId = sec.id },
                                    )
                                }
                            }
                            if (!sec.isVerified) {
                                val otp = settings.secondaryOtpDrafts[sec.id].orEmpty()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SeTextField(
                                        value = otp,
                                        onValueChange = { viewModel.onSecondaryOtpDraftChange(sec.id, it) },
                                        placeholder = "6-digit code",
                                        modifier = Modifier.weight(1f),
                                    )
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_verify_action),
                                        onClick = { viewModel.verifySecondaryEmail(sec.id) },
                                        isLoading = settings.isEmailSaving,
                                    )
                                }
                            }
                        }

                        if (settings.emailError != null) {
                            SeErrorText(text = settings.emailError!!)
                        }

                        // Add new email form toggle / panel
                        if (!settings.showAddEmailForm) {
                            SeTextButton(
                                text = "+ " + stringResource(R.string.account_add_email_action),
                                onClick = viewModel::toggleAddEmailForm,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeTextField(
                                    value = settings.newEmailDraft,
                                    onValueChange = viewModel::onNewEmailDraftChange,
                                    label = stringResource(R.string.account_email_label),
                                )
                                if (!profile.isGoogleOnly) {
                                    SeTextField(
                                        value = settings.emailPasswordDraft,
                                        onValueChange = viewModel::onEmailPasswordDraftChange,
                                        label = stringResource(R.string.account_current_password_label),
                                        visualTransformation = PasswordVisualTransformation(),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    SeTextButton(
                                        text = stringResource(R.string.action_cancel),
                                        onClick = viewModel::toggleAddEmailForm,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    SePrimaryButton(
                                        text = stringResource(R.string.account_send_verification_action),
                                        onClick = viewModel::addSecondaryEmail,
                                        isLoading = settings.isEmailSaving,
                                    )
                                }
                            }
                        }
                    }
                }

                // Phone Row
                val phoneDisplay =
                    if (profile.phoneNumber.isNotBlank()) {
                        "${profile.phoneCountryCode} ${profile.phoneNumber}"
                    } else {
                        stringResource(R.string.account_add_phone_action)
                    }
                SeAccordionRow(
                    label = stringResource(R.string.account_phone_label),
                    value = phoneDisplay,
                    expanded = settings.expandedRow == AccountRow.PHONE,
                    onToggle = { viewModel.toggleRow(AccountRow.PHONE) },
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Phone,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhoneNumberRow(
                            dialFlag = flagEmojiForDialCode(settings.phoneCountryCodeDraft),
                            dialCode = settings.phoneCountryCodeDraft,
                            phoneNumber = settings.phoneNumberDraft,
                            enabled = !settings.isPhoneSaving,
                            onDialClick = { showDialCodePicker = true },
                            onPhoneChange = viewModel::onPhoneNumberDraftChange,
                        )
                        if (settings.phoneError != null) {
                            SeErrorText(text = settings.phoneError!!)
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SeTextButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { viewModel.toggleRow(AccountRow.PHONE) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SePrimaryButton(
                                text = stringResource(R.string.action_save),
                                onClick = viewModel::savePhone,
                                isLoading = settings.isPhoneSaving,
                            )
                        }
                    }
                }

                // Password Row (hidden for Google-only)
                if (!profile.isGoogleOnly) {
                    SeAccordionRow(
                        label = stringResource(R.string.account_password_label),
                        value = "••••••••",
                        expanded = settings.expandedRow == AccountRow.PASSWORD,
                        onToggle = { viewModel.toggleRow(AccountRow.PASSWORD) },
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Lock,
                                tint = SplitEaseColors.Primary,
                                size = 40,
                            )
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SeTextField(
                                value = settings.currentPasswordDraft,
                                onValueChange = viewModel::onCurrentPasswordDraftChange,
                                label = stringResource(R.string.account_current_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            SeTextField(
                                value = settings.newPasswordDraft,
                                onValueChange = viewModel::onNewPasswordDraftChange,
                                label = stringResource(R.string.account_new_password_label),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            if (settings.passwordError != null) {
                                SeErrorText(text = settings.passwordError!!)
                            }
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SeTextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { viewModel.toggleRow(AccountRow.PASSWORD) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SePrimaryButton(
                                    text = stringResource(R.string.account_update_password_action),
                                    onClick = viewModel::updatePassword,
                                    isLoading = settings.isPasswordSaving,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SeSectionHeader(text = stringResource(R.string.account_preferences_section))
            AccountSettingsCard {
                SeListRow(
                    title = stringResource(R.string.settings_currency_item),
                    subtitle = "$currency · $currencyLabel",
                    leading = { CurrencyLeading(code = currency) },
                    trailing = { AccountSettingsChevron() },
                    onClick = onOpenCurrency,
                    showDivider = false,
                    labelStyle = SeListRowLabelStyle.Field,
                )
                SeListRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = accountLocaleLabel(locale),
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Translate,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                    trailing = { AccountSettingsChevron() },
                    onClick = onOpenLanguage,
                    showDivider = false,
                    labelStyle = SeListRowLabelStyle.Field,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            SeSectionHeader(text = stringResource(R.string.account_advanced_section))
            AccountSettingsCard {
                SeListRow(
                    title = stringResource(R.string.account_logout_all_title),
                    subtitle = stringResource(R.string.account_logout_all_subtitle),
                    leading = {
                        SeIconTile(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            tint = SplitEaseColors.Primary,
                            size = 40,
                        )
                    },
                    trailing = { AccountSettingsChevron() },
                    onClick = { showSignOutAllConfirm = true },
                    showDivider = false,
                    labelStyle = SeListRowLabelStyle.Field,
                )
                SeListRow(
                    title = stringResource(R.string.account_close_title),
                    subtitle = stringResource(R.string.account_close_subtitle),
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Delete,
                            tint = SplitEaseColors.YouOwe,
                            size = 40,
                        )
                    },
                    trailing = { AccountSettingsChevron() },
                    onClick = onOpenCloseAccount,
                    showDivider = false,
                    labelStyle = SeListRowLabelStyle.Field,
                )
            }
        }
    }

    if (showDialCodePicker) {
        DialCodePickerDialog(
            selectedCode = settings.phoneCountryCodeDraft,
            selectedFlag = flagEmojiForDialCode(settings.phoneCountryCodeDraft),
            onSelect = { option ->
                viewModel.onPhoneCountryCodeDraftChange(option.code)
                showDialCodePicker = false
            },
            onDismiss = { showDialCodePicker = false },
        )
    }

    if (showSignOutAllConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.account_logout_all_title),
            body = stringResource(R.string.account_logout_all_confirm_body),
            confirmLabel = stringResource(R.string.account_logout_all_confirm_action),
            onDismissRequest = {
                if (!settings.isSaving) showSignOutAllConfirm = false
            },
            onConfirm = { viewModel.signOutAllDevices() },
            icon = Icons.AutoMirrored.Filled.Logout,
            tone = SeConfirmTone.Primary,
            confirmBusy = settings.isSaving,
        )
    }

    emailToRemoveId?.let { id ->
        val sec = settings.secondaryEmails.firstOrNull { it.id == id }
        SeConfirmDialog(
            title = stringResource(R.string.account_remove_email_title),
            body = stringResource(R.string.account_remove_email_body, sec?.email.orEmpty()),
            confirmLabel = stringResource(R.string.account_remove_action),
            onDismissRequest = { emailToRemoveId = null },
            onConfirm = {
                viewModel.removeSecondaryEmail(id)
                emailToRemoveId = null
            },
            tone = SeConfirmTone.Danger,
        )
    }
}

@Composable
private fun AccountProfileHero(
    displayName: String,
    photoUrl: String?,
    isBusy: Boolean,
    enabled: Boolean,
    isError: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onChangePhoto: () -> Unit,
) {
    val avatarName =
        displayName.ifBlank { stringResource(R.string.account_name_fallback) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(76.dp)
                    .clickable(onClick = onChangePhoto),
        ) {
            SeAvatarBadge(
                name = avatarName,
                photoUrl = photoUrl,
                size = 76.dp,
                borderWidth = 0.dp,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SplitEaseColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = SplitEaseColors.Primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.cd_change_profile_photo),
                        tint = SplitEaseColors.IconDefault,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier.width(IntrinsicSize.Min),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Min)
                            .widthIn(min = 48.dp)
                            .focusRequester(focusRequester),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    textStyle =
                        seEntityHeaderStyle().copy(
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_display_name),
                    tint = SplitEaseColors.Primary,
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clickable(enabled = enabled, onClick = { focusRequester.requestFocus() }),
                )
            }
        }
        if (isError) {
            Spacer(modifier = Modifier.height(6.dp))
            SeErrorText(text = stringResource(R.string.msg_display_name_required))
        }
    }
}

@Composable
private fun AccountSettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SplitEaseColors.Surface)
                .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun AccountSettingsChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = SplitEaseColors.IconDefault,
    )
}

@Composable
private fun CurrencyLeading(code: String) {
    val flag = currencyFlagEmoji(code)
    if (flag == null) {
        SeIconTile(
            icon = Icons.Filled.Payments,
            tint = SplitEaseColors.Primary,
            size = 40,
        )
        return
    }
    val fillAlpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.16f else 0.28f
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SplitEaseColors.Primary.copy(alpha = fillAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = flag, fontSize = 20.sp)
    }
}

private fun flagEmojiForDialCode(code: String): String =
    when (code.trim()) {
        "+91" -> "🇮🇳"
        "+1" -> "🇺🇸"
        "+44" -> "🇬🇧"
        "+61" -> "🇦🇺"
        "+1 CA", "+1CA" -> "🇨🇦"
        "+49" -> "🇩🇪"
        "+33" -> "🇫🇷"
        "+81" -> "🇯🇵"
        "+86" -> "🇨🇳"
        "+55" -> "🇧🇷"
        "+971" -> "🇦🇪"
        "+65" -> "🇸🇬"
        else -> "🌐"
    }

private fun currencyFlagEmoji(code: String): String? {
    val region = CURRENCY_REGIONS[code.trim().uppercase()] ?: return null
    return isoRegionToFlag(region)
}

private fun isoRegionToFlag(region: String): String? {
    val letters = region.uppercase()
    if (letters.length != 2 || letters.any { it !in 'A'..'Z' }) return null
    return buildString(4) {
        letters.forEach { appendCodePoint(0x1F1E6 + (it.code - 'A'.code)) }
    }
}

private val CURRENCY_REGIONS =
    mapOf(
        AppCurrencies.INR to "IN",
        AppCurrencies.USD to "US",
        "AED" to "AE",
        "AUD" to "AU",
        "BRL" to "BR",
        "CAD" to "CA",
        "CHF" to "CH",
        "CNY" to "CN",
        "DKK" to "DK",
        "EGP" to "EG",
        "EUR" to "EU",
        "GBP" to "GB",
        "HKD" to "HK",
        "IDR" to "ID",
        "ILS" to "IL",
        "JPY" to "JP",
        "KRW" to "KR",
        "MXN" to "MX",
        "MYR" to "MY",
        "NOK" to "NO",
        "NZD" to "NZ",
        "PHP" to "PH",
        "PLN" to "PL",
        "RUB" to "RU",
        "SAR" to "SA",
        "SEK" to "SE",
        "SGD" to "SG",
        "THB" to "TH",
        "TRY" to "TR",
        "TWD" to "TW",
        "VND" to "VN",
        "ZAR" to "ZA",
    )

@Composable
private fun accountLocaleLabel(locale: AppLocale): String =
    stringResource(
        when (locale) {
            AppLocale.SYSTEM -> R.string.settings_language_system
            AppLocale.ENGLISH -> R.string.settings_language_en
            AppLocale.SPANISH -> R.string.settings_language_es
            AppLocale.FRENCH -> R.string.settings_language_fr
            AppLocale.GERMAN -> R.string.settings_language_de
            AppLocale.PORTUGUESE -> R.string.settings_language_pt
            AppLocale.HINDI -> R.string.settings_language_hi
            AppLocale.JAPANESE -> R.string.settings_language_ja
        },
    )

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun AccountProfileSettingsPreview() {
    SePreview {
        Text("Preview")
    }
}
