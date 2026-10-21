package com.splitease.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.themeMode.collectAsStateWithLifecycle()

    AppearanceSettingsContent(
        selected = selected,
        onBack = onBack,
        onSelectThemeMode = viewModel::setThemeMode,
    )
}

@Composable
fun AppearanceSettingsContent(
    selected: ThemeMode,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    SeScreen(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
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
                    .padding(bottom = SeLayout.screenBottom),
        ) {
            HorizontalDivider(color = SplitEaseColors.Outline)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(SplitEaseColors.Surface)
                        .selectableGroup(),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeRow(
                        mode = mode,
                        selected = selected == mode,
                        onSelect = { onSelectThemeMode(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val icon =
        when (mode) {
            ThemeMode.LIGHT -> Icons.Filled.WbSunny
            ThemeMode.DARK -> Icons.Filled.DarkMode
            ThemeMode.SYSTEM -> Icons.Filled.BrightnessMedium
        }
    val subtitle =
        when (mode) {
            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system_subtitle)
            else -> null
        }

    SeListRow(
        title = themeModeLabel(mode),
        subtitle = subtitle,
        modifier =
            Modifier.selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        leading = {
            SeIconTile(
                icon = icon,
                tint = SplitEaseColors.Primary,
                size = 40,
            )
        },
        trailing = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SplitEaseColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        onClick = null,
        showDivider = false,
        horizontalPadding = SeLayout.listInset,
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }

@Preview(name = "Appearance Settings · Light", showBackground = true, heightDp = 640)
@Composable
private fun AppearanceSettingsScreenLightPreview() {
    SePreview {
        AppearanceSettingsContent(selected = ThemeMode.LIGHT)
    }
}

@Preview(name = "Appearance Settings · Dark", showBackground = true, heightDp = 640)
@Composable
private fun AppearanceSettingsScreenDarkPreview() {
    SePreview(darkTheme = true) {
        AppearanceSettingsContent(selected = ThemeMode.DARK)
    }
}

@Preview(name = "Appearance Settings · System", showBackground = true, heightDp = 640)
@Composable
private fun AppearanceSettingsScreenSystemPreview() {
    SePreview {
        AppearanceSettingsContent(selected = ThemeMode.SYSTEM)
    }
}
