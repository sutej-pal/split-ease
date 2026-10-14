package com.splitease.app.presentation.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.navigation.bottomBarScrollPadding
import com.splitease.app.presentation.theme.SeBodyLarge
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeListSection
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.seEntityHeaderStyle

@Composable
fun AccountProfileSettingsScreen(
    onBack: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenSignInAndContact: () -> Unit,
    onOpenCloseAccount: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currency by viewModel.currencyCode.collectAsStateWithLifecycle()
    val locale by viewModel.appLocale.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draftHydrated by remember { mutableStateOf(false) }

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

    AccountProfileSettingsContent(
        settings = settings,
        profile = profile,
        currency = currency,
        locale = locale,
        onBack = onBack,
        onOpenCurrency = onOpenCurrency,
        onOpenLanguage = onOpenLanguage,
        onOpenSignInAndContact = onOpenSignInAndContact,
        onOpenCloseAccount = onOpenCloseAccount,
        onDisplayNameChange = viewModel::onDisplayNameDraftChange,
        onChangePhoto = photoPicker::launch,
        onSaveDisplayName = viewModel::saveDisplayName,
        onSignOutAllDevices = viewModel::signOutAllDevices,
    )
}

@Composable
fun AccountProfileSettingsContent(
    settings: AccountSettingsUiState,
    profile: AccountProfileUi,
    currency: String,
    locale: AppLocale,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenCurrency: () -> Unit = {},
    onOpenLanguage: () -> Unit = {},
    onOpenSignInAndContact: () -> Unit = {},
    onOpenCloseAccount: () -> Unit = {},
    onDisplayNameChange: (String) -> Unit = {},
    onChangePhoto: () -> Unit = {},
    onSaveDisplayName: () -> Unit = {},
    onSignOutAllDevices: () -> Unit = {},
) {
    val currencyLabel = AppCurrencies.labelOf(currency)
    var showValidation by remember { mutableStateOf(false) }
    var showSignOutAllConfirm by remember { mutableStateOf(false) }

    val nameError = showValidation && settings.displayNameDraft.isBlank()
    val canSaveName =
        settings.displayNameDraft != profile.displayName && settings.displayNameDraft.isNotBlank()

    SeScreen(
        title = stringResource(R.string.account_profile_settings_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (canSaveName) {
                SeTextButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        showValidation = true
                        if (settings.displayNameDraft.isBlank()) return@SeTextButton
                        onSaveDisplayName()
                    },
                    enabled = !settings.isSaving,
                    isLoading = settings.isSaving,
                    emphasized = true,
                )
            }
        },
        content = { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.values.calculateTopPadding(),
                            start = padding.values.calculateStartPadding(layoutDirection),
                            end = padding.values.calculateEndPadding(layoutDirection),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomBarScrollPadding(includeFab = false)),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                AccountProfileHero(
                    displayName = settings.displayNameDraft,
                    photoUrl = profile.photoUrl,
                    isBusy = settings.isSaving,
                    enabled = !settings.isSaving,
                    isError = nameError,
                    onDisplayNameChange = onDisplayNameChange,
                    onChangePhoto = onChangePhoto,
                )

                Spacer(modifier = Modifier.height(28.dp))
                AccountSettingsCard {
                    SeListRow(
                        title = stringResource(R.string.account_sign_in_contact_section),
                        subtitle = profile.email.takeIf { it.isNotBlank() },
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Email,
                                tint = SplitEaseColors.Primary,
                                size = 40,
                            )
                        },
                        trailing = { AccountSettingsChevron() },
                        onClick = onOpenSignInAndContact,
                        showDivider = false,
                        horizontalPadding = 16.dp,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                SeListSection(title = stringResource(R.string.settings_help_section)) {
                    SeListRow(
                        title = stringResource(R.string.settings_help_center),
                        subtitle = stringResource(R.string.settings_help_center_subtitle),
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = onOpenCurrency,
                    )
                }
                SeSectionHeader(text = stringResource(R.string.account_preferences_section))
                AccountSettingsCard {
                    SeListRow(
                        title = stringResource(R.string.settings_currency_item),
                        subtitle = "$currency · $currencyLabel",
                        leading = { CurrencyLeading(code = currency) },
                        trailing = { AccountSettingsChevron() },
                        onClick = onOpenCurrency,
                        showDivider = false,
                        horizontalPadding = 16.dp,
                    )
                    SeListRow(
                        title = stringResource(R.string.settings_language),
                        subtitle = appLocaleLabel(locale),
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
                        horizontalPadding = 16.dp,
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
                        horizontalPadding = 16.dp,
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
                        horizontalPadding = 16.dp,
                    )
                }
            }
        },
    )

    if (showSignOutAllConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.account_logout_all_title),
            body = stringResource(R.string.account_logout_all_confirm_body),
            confirmLabel = stringResource(R.string.account_logout_all_confirm_action),
            onDismissRequest = {
                if (!settings.isSaving) showSignOutAllConfirm = false
            },
            onConfirm = onSignOutAllDevices,
            icon = Icons.AutoMirrored.Filled.Logout,
            tone = SeConfirmTone.Primary,
            confirmBusy = settings.isSaving,
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
    modifier: Modifier = Modifier,
) {
    val avatarName =
        displayName.ifBlank { stringResource(R.string.account_name_fallback) }
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val nameStyle =
        seEntityHeaderStyle().copy(
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .clickable(enabled = enabled, onClick = { focusRequester.requestFocus() }),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                textStyle = nameStyle,
                cursorBrush = SolidColor(SplitEaseColors.Primary),
                decorationBox = { innerTextField ->
                    if (isFocused) {
                        innerTextField()
                    } else {
                        // Unfocused: show an ellipsised read-only label (BasicTextField can't ellipsise).
                        Text(
                            text = avatarName,
                            style =
                                if (displayName.isBlank()) {
                                    nameStyle.copy(color = SplitEaseColors.NavyMuted)
                                } else {
                                    nameStyle
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
        if (isError) {
            Spacer(modifier = Modifier.height(6.dp))
            SeErrorText(text = stringResource(R.string.msg_display_name_required))
        }
    }
}

@Composable
internal fun AccountSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SplitEaseColors.Surface),
        content = content,
    )
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
        SeBodyLarge(text = flag)
    }
}

private val CURRENCY_REGIONS =
    mapOf(
        "USD" to "US",
        "EUR" to "EU",
        "GBP" to "GB",
        "INR" to "IN",
        "AUD" to "AU",
        "CAD" to "CA",
        "JPY" to "JP",
        "CNY" to "CN",
        "CHF" to "CH",
        "NZD" to "NZ",
        "SGD" to "SG",
        "HKD" to "HK",
        "SEK" to "SE",
        "KRW" to "KR",
        "MXN" to "MX",
        "BRL" to "BR",
        "ZAR" to "ZA",
    )

private fun isoRegionToFlag(region: String): String {
    val upper = region.uppercase()
    if (upper.length != 2) return "🌐"
    val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
    val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
}

private fun currencyFlagEmoji(code: String): String? {
    val region = CURRENCY_REGIONS[code.trim().uppercase()] ?: return null
    return isoRegionToFlag(region)
}

internal fun flagEmojiForDialCode(code: String): String =
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

@Composable
private fun appLocaleLabel(locale: AppLocale): String =
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

@Preview(name = "Account Profile · Default", showBackground = true, heightDp = 720)
@Composable
private fun AccountProfileSettingsScreenPreview() {
    SePreview {
        AccountProfileSettingsContent(
            settings =
                AccountSettingsUiState(
                    displayNameDraft = "Alex Rivera",
                    currencyCode = "USD",
                    appLocale = AppLocale.ENGLISH,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex.rivera@example.com",
                    photoUrl = null,
                ),
            currency = "USD",
            locale = AppLocale.ENGLISH,
        )
    }
}

@Preview(name = "Account Profile · Long name with ellipsis", showBackground = true, heightDp = 720)
@Composable
private fun AccountProfileSettingsScreenLongNamePreview() {
    SePreview {
        AccountProfileSettingsContent(
            settings =
                AccountSettingsUiState(
                    displayNameDraft = "Alexander Maximillian Montgomery Rivera III",
                    currencyCode = "USD",
                    appLocale = AppLocale.ENGLISH,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alexander Maximillian Montgomery Rivera III",
                    email = "alexander.maximillian.montgomery.rivera.iii@example.com",
                    photoUrl = null,
                ),
            currency = "USD",
            locale = AppLocale.ENGLISH,
        )
    }
}

@Preview(name = "Account Profile · Editing Name", showBackground = true, heightDp = 720)
@Composable
private fun AccountProfileSettingsScreenEditingPreview() {
    SePreview {
        AccountProfileSettingsContent(
            settings =
                AccountSettingsUiState(
                    displayNameDraft = "Alex R.",
                    currencyCode = "EUR",
                    appLocale = AppLocale.ENGLISH,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex.rivera@example.com",
                    photoUrl = null,
                ),
            currency = "EUR",
            locale = AppLocale.ENGLISH,
        )
    }
}

@Preview(name = "Account Profile · Saving", showBackground = true, heightDp = 720)
@Composable
private fun AccountProfileSettingsScreenSavingPreview() {
    SePreview {
        AccountProfileSettingsContent(
            settings =
                AccountSettingsUiState(
                    displayNameDraft = "Alex Rivera",
                    isSaving = true,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex.rivera@example.com",
                ),
            currency = "GBP",
            locale = AppLocale.ENGLISH,
        )
    }
}

@Preview(name = "Account Profile · Dark Mode", showBackground = true, heightDp = 720)
@Composable
private fun AccountProfileSettingsScreenDarkPreview() {
    SePreview(darkTheme = true) {
        AccountProfileSettingsContent(
            settings =
                AccountSettingsUiState(
                    displayNameDraft = "Alex Rivera",
                    currencyCode = "INR",
                    appLocale = AppLocale.HINDI,
                ),
            profile =
                AccountProfileUi(
                    displayName = "Alex Rivera",
                    email = "alex.rivera@example.com",
                ),
            currency = "INR",
            locale = AppLocale.HINDI,
        )
    }
}

