package com.splitease.app.presentation.ui

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

/**
 * Applies window status / navigation bar colors and icon contrast.
 *
 * [statusBarDarkIcons] / [navigationBarDarkIcons] map to
 * `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` (true = dark glyphs).
 *
 * On dispose, restores theme-default contrast so status-bar notification icons
 * (wifi, signal, battery) cannot linger white on a light bar after leaving a
 * screen that forced light glyphs (e.g. group detail banner).
 */
@Composable
fun SeSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val themeBg = MaterialTheme.colorScheme.background
    val themeSurface = MaterialTheme.colorScheme.surface
    val themeDarkGlyphs = themeBg.luminance() > 0.5f

    DisposableEffect(
        statusBarColor,
        navigationBarColor,
        statusBarDarkIcons,
        navigationBarDarkIcons,
        themeBg,
        themeSurface,
        themeDarkGlyphs,
    ) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        val controller = WindowCompat.getInsetsController(window, view)

        @Suppress("DEPRECATION")
        window.statusBarColor = statusBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = navigationBarColor.toArgb()
        controller.isAppearanceLightStatusBars = statusBarDarkIcons
        controller.isAppearanceLightNavigationBars = navigationBarDarkIcons

        onDispose {
            // Restore theme defaults so light status-bar icons don't stick after pop.
            @Suppress("DEPRECATION")
            window.statusBarColor = themeBg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = themeSurface.toArgb()
            controller.isAppearanceLightStatusBars = themeDarkGlyphs
            controller.isAppearanceLightNavigationBars = themeDarkGlyphs
        }
    }
}

/**
 * Back chevron in a single 40dp circular hit target.
 * Do not wrap this in another [Box] / [IconButton] at call sites.
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
            // ChevronLeft’s glyph sits slightly right in the 24dp viewport; nudge left.
            modifier = Modifier.size(24.dp).offset(x = (-1).dp),
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

/** Toolbar content height below the status bar for leading (back + title) chrome. */
private val SeTopBarContentHeight = 56.dp

/**
 * Canonical top app bar for secondary screens (back / close + title + actions).
 *
 * Prefer [SeScreen] for full pages — that is the **one** wrapper for back + title.
 * Do not invent alternate title sizes or custom back rows at call sites.
 *
 * - [centered] = false → leading layout (back/close at [SeLayout.detailHorizontal], title beside it).
 * - [centered] = true → Material center-aligned bar (e.g. create-group close sheet).
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
    if (centered) {
        val colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = SplitEaseColors.Navy,
                actionIconContentColor = SplitEaseColors.Navy,
                navigationIconContentColor = SplitEaseColors.Navy,
            )
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = {
                SeScreenTitleText(
                    text = title,
                    textAlign = TextAlign.Center,
                )
            },
            navigationIcon = {
                when {
                    onClose != null ->
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    onBack != null ->
                        SeChevronBackButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                }
            },
            actions = actions,
            colors = colors,
        )
    } else {
        SeLeadingTopBar(
            title = title,
            modifier = modifier,
            onBack = onBack,
            onClose = onClose,
            actions = actions,
        )
    }
}

/**
 * Leading back/close + title + actions — one [Row], 20dp side margin, no nested boxes
 * around the back control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeLeadingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    enabled: Boolean = true,
    consumeWindowInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val insetsModifier =
        if (consumeWindowInsets) {
            Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .then(insetsModifier)
                .height(SeTopBarContentHeight)
                .padding(horizontal = SeLayout.detailHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onClose != null ->
                IconButton(
                    onClick = onClose,
                    enabled = enabled,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = SplitEaseColors.Navy,
                    )
                }
            onBack != null ->
                SeChevronBackButton(
                    onClick = onBack,
                    enabled = enabled,
                )
        }
        if (title.isNotEmpty()) {
            SeScreenTitleText(
                text = title,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = SeLayout.itemGap),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * Inline back + optional title for rare non-[SeScreen] scaffolds.
 *
 * Prefer [SeScreen] / [SeTopBar] for full secondary screens. Kept for embedded headers
 * that cannot host a Scaffold top bar — does not consume status-bar insets.
 */
@Composable
fun SeBackTitleRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    enabled: Boolean = true,
) {
    SeLeadingTopBar(
        title = title.orEmpty(),
        modifier = modifier,
        onBack = onBack,
        enabled = enabled,
        consumeWindowInsets = false,
    )
}

/**
 * Standard secondary screen scaffold: system bars + [SeTopBar] + content.
 *
 * This is the **one** navigation chrome for back + title across the app.
 * Body horizontal rhythm: apply [SeLayout.detailHorizontal] / [seDetailHorizontal]
 * for Activity / Balances / Totals (match group-detail 20dp), or
 * [SeLayout.screenHorizontal] for form screens. Do not invent per-screen title sizes.
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
