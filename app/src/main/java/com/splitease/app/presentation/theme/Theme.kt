package com.splitease.app.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette: teal/emerald — distinct from purple-on-white AI defaults and Splitwise green clone.
private val BrandPrimary = Color(0xFF0F766E)
private val BrandOnPrimary = Color(0xFFFFFFFF)
private val BrandPrimaryContainer = Color(0xFFCCFBF1)
private val BrandOnPrimaryContainer = Color(0xFF134E4A)
private val BrandSecondary = Color(0xFF0E7490)
private val BrandOnSecondary = Color(0xFFFFFFFF)
private val BrandTertiary = Color(0xFF047857)
private val BrandBackgroundLight = Color(0xFFF0FDFA)
private val BrandSurfaceLight = Color(0xFFFAFFFE)
private val BrandBackgroundDark = Color(0xFF042F2E)
private val BrandSurfaceDark = Color(0xFF0A3D3B)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    tertiary = BrandTertiary,
    background = BrandBackgroundLight,
    surface = BrandSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF134E4A),
    primaryContainer = Color(0xFF0F766E),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF083344),
    tertiary = Color(0xFF6EE7B7),
    background = BrandBackgroundDark,
    surface = BrandSurfaceDark,
)

/**
 * SplitEase Material 3 theme with dynamic color on API 31+ and a teal brand fallback.
 */
@Composable
fun SplitEaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SplitEaseTypography,
        content = content,
    )
}
