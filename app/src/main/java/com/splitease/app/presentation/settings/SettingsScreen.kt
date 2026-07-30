package com.splitease.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.domain.settings.AuthTimeout
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.security.authenticateWithBiometrics
import com.splitease.app.presentation.security.biometricAvailability
import com.splitease.app.presentation.security.BiometricAvailability
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeModalTitle
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSecurity: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val biometricLock by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
            ) {
                SeSectionHeader(text = stringResource(R.string.settings_preferences_section))
                SeListRow(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = themeModeLabel(themeMode),
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.DarkMode,
                            tint = SplitEaseColors.IconOther,
                            size = 40,
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SplitEaseColors.NavyMuted,
                        )
                    },
                    onClick = onOpenAppearance,
                    showDivider = true,
                )
                SeListRow(
                    title = stringResource(R.string.settings_security),
                    subtitle =
                        if (biometricLock) {
                            stringResource(R.string.settings_security_on)
                        } else {
                            stringResource(R.string.settings_security_off)
                        },
                    leading = {
                        SeIconTile(
                            icon = Icons.Filled.Security,
                            tint = SplitEaseColors.IconHome,
                            size = 40,
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SplitEaseColors.NavyMuted,
                        )
                    },
                    onClick = onOpenSecurity,
                    showDivider = false,
                )
            }
        },
    )
}

@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.appLocale.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp),
            ) {
                SeSectionHeader(text = stringResource(R.string.settings_language_section))
                AppLocale.entries.forEachIndexed { index, locale ->
                    LanguageRow(
                        locale = locale,
                        selected = selected == locale,
                        onSelect = { viewModel.setAppLocale(locale) },
                        showDivider = index < AppLocale.entries.lastIndex,
                    )
                }
            }
        },
    )
}

@Composable
private fun LanguageRow(
    locale: AppLocale,
    selected: Boolean,
    onSelect: () -> Unit,
    showDivider: Boolean,
) {
    SeListRow(
        title = appLocaleLabel(locale),
        leading = {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = SplitEaseColors.Primary,
                    ),
            )
        },
        onClick = onSelect,
        showDivider = showDivider,
    )
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


@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.themeMode.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp),
            ) {
                SeSectionHeader(text = stringResource(R.string.settings_appearance_section))
                ThemeMode.entries.forEachIndexed { index, mode ->
                    ThemeModeRow(
                        mode = mode,
                        selected = selected == mode,
                        onSelect = { viewModel.setThemeMode(mode) },
                        showDivider = index < ThemeMode.entries.lastIndex,
                    )
                }
            }
        },
    )
}

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val biometricEnabled by viewModel.biometricLockEnabled.collectAsStateWithLifecycle()
    val timeout by viewModel.authTimeout.collectAsStateWithLifecycle()
    var showTimeoutPicker by rememberSaveable { mutableStateOf(false) }
    var enableError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val unlockTitle = stringResource(R.string.security_unlock_title)
    val unlockSubtitle = stringResource(R.string.settings_biometrics_body)
    val unavailable = stringResource(R.string.security_biometrics_unavailable)

    SeScreen(
        title = stringResource(R.string.settings_security),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_biometrics_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SplitEaseColors.Navy,
                    )
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { checked ->
                            enableError = null
                            if (!checked) {
                                viewModel.setBiometricLockEnabled(false)
                                return@Switch
                            }
                            if (activity == null) {
                                enableError = unavailable
                                return@Switch
                            }
                            when (val availability = biometricAvailability(activity, unavailable)) {
                                is BiometricAvailability.Unavailable -> {
                                    enableError = availability.message
                                }
                                BiometricAvailability.Ready -> {
                                    authenticateWithBiometrics(
                                        activity = activity,
                                        title = unlockTitle,
                                        subtitle = unlockSubtitle,
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = { message -> enableError = message },
                                        onUnavailable = { enableError = unavailable },
                                    )
                                }
                            }
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SplitEaseColors.Primary,
                            ),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_biometrics_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (enableError != null) {
                    Text(
                        text = enableError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                HorizontalDivider(color = SplitEaseColors.Outline)

                SeListRow(
                    title = stringResource(R.string.settings_timeout_title),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = authTimeoutLabel(timeout),
                                style = MaterialTheme.typography.bodyLarge,
                                color = SplitEaseColors.NavyMuted,
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = SplitEaseColors.NavyMuted,
                            )
                        }
                    },
                    onClick = { showTimeoutPicker = true },
                    showDivider = false,
                )
                Text(
                    text = stringResource(R.string.settings_timeout_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        },
    )

    if (showTimeoutPicker) {
        SeModal(onDismissRequest = { showTimeoutPicker = false }) {
            SeModalTitle(text = stringResource(R.string.settings_timeout_title))
            Spacer(modifier = Modifier.height(12.dp))
            AuthTimeout.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAuthTimeout(option)
                                showTimeoutPicker = false
                            }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = timeout == option,
                        onClick = {
                            viewModel.setAuthTimeout(option)
                            showTimeoutPicker = false
                        },
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = SplitEaseColors.Primary,
                            ),
                    )
                    Text(
                        text = authTimeoutLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SeTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { showTimeoutPicker = false },
            )
        }
    }
}

@Composable
fun CurrencySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.currencyCode.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val options = AppCurrencies.filter(filter)

    SeScreen(
        title = stringResource(R.string.settings_currency_item),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_currency_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SeTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = stringResource(R.string.settings_currency_search),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(options, key = { it.first }) { (code, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setCurrency(code) }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == code,
                                onClick = { viewModel.setCurrency(code) },
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = SplitEaseColors.Primary,
                                    ),
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
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
            }
        },
    )
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
    showDivider: Boolean,
) {
    SeListRow(
        title = themeModeLabel(mode),
        trailing = {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = SplitEaseColors.Primary,
                    ),
            )
        },
        onClick = onSelect,
        showDivider = showDivider,
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }

@Composable
private fun authTimeoutLabel(timeout: AuthTimeout): String =
    when (timeout) {
        AuthTimeout.IMMEDIATE -> stringResource(R.string.settings_timeout_immediate)
        AuthTimeout.FIVE_SECONDS -> stringResource(R.string.settings_timeout_5_seconds)
        AuthTimeout.FIFTEEN_SECONDS -> stringResource(R.string.settings_timeout_15_seconds)
        AuthTimeout.ONE_MINUTE -> stringResource(R.string.settings_timeout_1_minute)
        AuthTimeout.FIVE_MINUTES -> stringResource(R.string.settings_timeout_5_minutes)
        AuthTimeout.FIFTEEN_MINUTES -> stringResource(R.string.settings_timeout_15_minutes)
        AuthTimeout.ONE_HOUR -> stringResource(R.string.settings_timeout_1_hour)
    }

@Preview(name = "Settings hub", showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SePreview {
        Column(modifier = Modifier.padding(20.dp)) {
            SeSectionHeader(text = "Preferences")
            SeListRow(title = "Appearance", subtitle = "System default", onClick = {})
            SeListRow(title = "Security", subtitle = "Off", onClick = {})
        }
    }
}
