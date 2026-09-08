package com.splitease.app.presentation.account

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton

@Composable
fun AccountProfileSettingsScreen(
    onBack: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenDeleteAccount: () -> Unit = {},
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
                email = profile.email,
                photoUrl = profile.photoUrl,
                isBusy = settings.isSaving,
                enabled = !settings.isSaving,
                isError = nameError,
                onDisplayNameChange = viewModel::onDisplayNameDraftChange,
                onChangePhoto = photoPicker::launch,
            )

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
                )
                HorizontalDivider(thickness = 1.dp, color = SplitEaseColors.Outline)
                SeListRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = accountLocaleLabel(locale),
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Translate,
                            tint = SplitEaseColors.IconOther,
                            size = 40,
                        )
                    },
                    trailing = { AccountSettingsChevron() },
                    onClick = onOpenLanguage,
                    showDivider = false,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            SeOutlinedButton(
                text = stringResource(R.string.account_delete_title),
                onClick = onOpenDeleteAccount,
                contentColor = SplitEaseColors.YouOwe,
            )
        }
    }
}

@Composable
private fun AccountProfileHero(
    displayName: String,
    email: String,
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
                borderWidth = 3.dp,
                borderColor = SplitEaseColors.PrimarySoft,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SplitEaseColors.Surface)
                        .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(10.dp)),
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
                        tint = SplitEaseColors.Navy,
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
                    textStyle =
                        TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = SplitEaseColors.Navy,
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
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        SplitEaseColors.Primary
                    },
            )
        }
        if (isError) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.msg_display_name_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = SplitEaseColors.NavyMuted,
            )
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun AccountSettingsChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = SplitEaseColors.NavyMuted,
    )
}

@Composable
private fun CurrencyLeading(code: String) {
    val flag = currencyFlagEmoji(code)
    if (flag == null) {
        SeIconTile(
            icon = Icons.Filled.Payments,
            tint = SplitEaseColors.IconFriends,
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
                .background(SplitEaseColors.IconFriends.copy(alpha = fillAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = flag, fontSize = 20.sp)
    }
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
