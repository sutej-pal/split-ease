package com.splitease.app.presentation.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors

@Composable
fun SeSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = navigationBarColor.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = statusBarDarkIcons
            isAppearanceLightNavigationBars = navigationBarDarkIcons
        }
    }
}

/**
 * Back chevron centered horizontally and vertically in a circular hit target.
 */
@Composable
fun SeChevronBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(R.string.cd_navigate_back),
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 20.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = contentDescription,
            tint = SplitEaseColors.Navy,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Single title text used by [SeTopBar], [SeBackTitleRow], and auth headers.
 */
@Composable
fun SeScreenTitleText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = SeScreenTitleStyle(),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Canonical top app bar for secondary screens (back / close + title + actions).
 *
 * Prefer [SeScreen] for full pages. Do not invent alternate title sizes at call sites.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = SplitEaseColors.Navy,
            actionIconContentColor = SplitEaseColors.Navy,
            navigationIconContentColor = SplitEaseColors.Navy,
        )
    val navigationIcon: @Composable () -> Unit = {
        when {
            onClose != null ->
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            onBack != null ->
                SeChevronBackButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.cd_navigate_back),
                )
        }
    }
    val titleContent: @Composable () -> Unit = {
        SeScreenTitleText(
            text = title,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }

    if (centered) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = titleContent,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    }
}

/**
 * Inline back control with an optional title beside it.
 *
 * Prefer [SeScreen] / [SeTopBar] for full secondary screens. Use this only when a
 * Material top app bar is not a fit (e.g. custom scroll scaffolds). Title style
 * matches [SeTopBar] via [SeScreenTitleText].
 */
@Composable
fun SeBackTitleRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = SeLayout.screenTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeChevronBackButton(
            onClick = onBack,
            enabled = enabled,
        )
        if (title != null) {
            SeScreenTitleText(
                text = title,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = SeLayout.itemGap),
            )
        }
    }
}

/**
 * Standard secondary screen scaffold: system bars + [SeTopBar] + content.
 *
 * This is the **one** navigation chrome for back + title across the app.
 * Body horizontal rhythm: apply [SeLayout.screenHorizontal] inside [content]
 * (do not invent per-screen title sizes — [SeTopBar] owns that).
 */
@Composable
fun SeScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    centeredTitle: Boolean = false,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (padding: PaddingValuesAware) -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val lightIconsOnBars = bg.luminance() > 0.5f
    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = lightIconsOnBars,
        navigationBarDarkIcons = lightIconsOnBars,
    )
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = title,
                onBack = onBack,
                onClose = onClose,
                centered = centeredTitle,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
    ) { padding ->
        if (subtitle != null) {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .padding(horizontal = SeLayout.screenHorizontal)
                        .padding(top = SeLayout.screenTop),
            ) {
                Text(
                    text = subtitle,
                    style = SeScreenSubtitleStyle(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SeLayout.headerToContent))
                content(PaddingValuesAware(PaddingValues(0.dp)))
            }
        } else {
            content(PaddingValuesAware(padding))
        }
    }
}

/** Thin wrapper so call sites can `padding.values` without importing Scaffold padding types awkwardly. */
data class PaddingValuesAware(
    val values: PaddingValues,
)

@Preview(name = "Top bar", showBackground = true)
@Composable
private fun SeTopBarPreview() {
    SePreview {
        Column {
            SeTopBar(title = "Friends", onBack = {})
            SeTopBar(
                title = "Create a group",
                onClose = {},
                centered = true,
            )
        }
    }
}

@Preview(name = "Back title row", showBackground = true)
@Composable
private fun SeBackTitleRowPreview() {
    SePreview {
        Column {
            SeBackTitleRow(title = "Forgot password", onBack = {})
            SeBackTitleRow(onBack = {})
        }
    }
}

@Preview(name = "Screen scaffold", showBackground = true, heightDp = 320)
@Composable
private fun SeScreenPreview() {
    SePreview {
        SeScreen(
            title = "Account",
            onBack = {},
            subtitle = "Manage your profile and preferences.",
            content = {
                Text(text = "Account body")
            },
        )
    }
}
