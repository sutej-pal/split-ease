package com.splitease.app.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the main tab bar (includes system nav inset).
 * Provided by [SignedInNavHost]. [com.splitease.app.presentation.ui.SePreview]
 * supplies `0.dp` so isolated previews do not crash.
 */
val LocalBottomBarInset =
    compositionLocalOf<Dp> {
        error("LocalBottomBarInset is not provided")
    }

/** Extra list padding so rows clear the extended Add-expense FAB above the tab bar. */
val BottomBarFabClearance = 88.dp

/** Inner tab scaffolds must not re-apply system nav insets; the tab bar already includes them. */
fun bottomBarContentWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
fun bottomBarScrollPadding(includeFab: Boolean): Dp =
    LocalBottomBarInset.current + if (includeFab) BottomBarFabClearance else 16.dp

@Composable
fun Modifier.paddingAboveBottomBar(): Modifier = this.padding(bottom = LocalBottomBarInset.current)
