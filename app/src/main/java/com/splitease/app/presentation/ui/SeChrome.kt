package com.splitease.app.presentation.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun SeChevronBackButton(
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
                    contentDescription = "Back",
                )
        }
    }
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
 * Inline back control with an optional title beside it (auth-style headers).
 * Prefer [SeTopBar] / [SeScreen] when you need a Material top app bar.
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
                .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeChevronBackButton(
            onClick = onBack,
            enabled = enabled,
        )
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                color = SplitEaseColors.Navy,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = 16.sp,
                        maxFontSize = 24.sp,
                    ),
            )
        }
    }
}

@Composable
fun SeScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    centeredTitle: Boolean = false,
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
        content(PaddingValuesAware(padding))
    }
}

/** Thin wrapper so call sites can `padding.values` without importing Scaffold padding types awkwardly. */
data class PaddingValuesAware(
    val values: androidx.compose.foundation.layout.PaddingValues,
)

@Preview(name = "Top bar", showBackground = true)
@Composable
private fun SeTopBarPreview() {
    SePreview {
        Column {
            SeTopBar(title = "Friends", onBack = {})
            SeTopBar(title = "Create a group", onClose = {}, centered = true, actions = {
                SeTextButton(text = "Done", onClick = {})
            })
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
            content = { padding ->
                Text(
                    text = "Account body",
                    modifier = Modifier.padding(padding.values).padding(8.dp),
                )
            },
        )
    }
}
