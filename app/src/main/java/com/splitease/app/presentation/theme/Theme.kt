package com.splitease.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Hand-authored light scheme from brand tokens in [Color.kt].
 * Dynamic / tonal Material You generation is intentionally not used for brand roles.
 */
private val LightColorScheme =
    lightColorScheme(
        primary = IndigoLight,
        onPrimary = Color.White, // WCAG vs IndigoLight ≈ 6.29:1
        primaryContainer = BackgroundLight,
        onPrimaryContainer = TextPrimaryLight,
        secondary = IndigoLight,
        onSecondary = Color.White,
        secondaryContainer = BackgroundLight,
        onSecondaryContainer = TextPrimaryLight,
        tertiary = AmberLight,
        onTertiary = TextPrimaryLight, // amber is light; dark indigo text reads better than white
        tertiaryContainer = Color(0xFFFFE8C2),
        onTertiaryContainer = TextPrimaryLight,
        background = BackgroundLight,
        onBackground = TextPrimaryLight,
        surface = SurfaceLight,
        onSurface = TextPrimaryLight,
        // Light muted text not in brand table — soft indigo-gray pending confirmation.
        surfaceVariant = BackgroundLight,
        onSurfaceVariant = Color(0xFF5C5878),
        outline = IndigoLight,
        outlineVariant = Color(0xFFC5C7E8),
        // TODO(design): error* are PLACEHOLDERs for "you owe" — confirm before shipping.
        error = ErrorPlaceholder,
        onError = Color.White,
        errorContainer = ErrorContainerPlaceholder,
        onErrorContainer = ErrorPlaceholder,
    )

/**
 * Hand-authored dark scheme from brand tokens in [Color.kt].
 */
private val DarkColorScheme =
    darkColorScheme(
        primary = IndigoDark,
        onPrimary = BackgroundDark, // WCAG vs IndigoDark ≈ 6.20:1
        primaryContainer = SurfaceDark,
        onPrimaryContainer = TextPrimaryDark,
        secondary = IndigoDark,
        onSecondary = BackgroundDark,
        secondaryContainer = SurfaceDark,
        onSecondaryContainer = TextPrimaryDark,
        tertiary = AmberDark,
        onTertiary = BackgroundDark,
        tertiaryContainer = Color(0xFF4A3400),
        onTertiaryContainer = AmberDark,
        background = BackgroundDark,
        onBackground = TextPrimaryDark,
        surface = SurfaceDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = SurfaceDark,
        onSurfaceVariant = TextSecondaryDark,
        outline = IndigoDark,
        outlineVariant = Color(0xFF3A3552),
        // TODO(design): error* are PLACEHOLDERs for "you owe" — confirm before shipping.
        error = ErrorPlaceholder,
        onError = Color.White,
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = ErrorContainerPlaceholder,
    )

/**
 * SplitEase Material 3 theme using the icon-derived indigo/amber brand palette.
 *
 * Dynamic color is **off by default** so brand identity stays consistent; pass
 * [dynamicColor] = true only if an explicit settings opt-in is added later.
 *
 * @param darkTheme When true, uses the dark brand scheme.
 * @param dynamicColor Android 12+ Material You (opt-in only; not the default).
 */
@Composable
fun SplitEaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Match system bars to the theme background. With edge-to-edge
            // (MainActivity.enableEdgeToEdge), these may be translucent; icon
            // contrast is still controlled via WindowInsetsController.
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SplitEaseTypography,
        content = content,
    )
}
