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

private val LightColorScheme =
    lightColorScheme(
        primary = SplitEaseColors.Primary,
        onPrimary = Color.White,
        primaryContainer = SplitEaseColors.PrimarySoft,
        onPrimaryContainer = SplitEaseColors.PrimaryDark,
        secondary = SplitEaseColors.Secondary,
        onSecondary = Color.White,
        secondaryContainer = SplitEaseColors.PrimarySoft,
        onSecondaryContainer = SplitEaseColors.Navy,
        tertiary = SplitEaseColors.Accent,
        onTertiary = Color.White,
        tertiaryContainer = SplitEaseColors.AccentSoft,
        onTertiaryContainer = Color(0xFF5C1A12),
        background = SplitEaseColors.Background,
        onBackground = SplitEaseColors.Navy,
        surface = SplitEaseColors.Surface,
        onSurface = SplitEaseColors.Navy,
        surfaceVariant = SplitEaseColors.SurfaceMuted,
        onSurfaceVariant = SplitEaseColors.NavyMuted,
        outline = SplitEaseColors.OutlineStrong,
        outlineVariant = SplitEaseColors.Outline,
        error = SplitEaseColors.YouOwe,
        onError = Color.White,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF8FA3FF),
        onPrimary = Color(0xFF0A1A5C),
        primaryContainer = Color(0xFF2F57EF),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFAAB4FF),
        onSecondary = Color(0xFF121833),
        tertiary = Color(0xFFFF8F84),
        onTertiary = Color(0xFF3E0E08),
        background = SplitEaseColors.ShellBackground,
        onBackground = Color(0xFFF4F6FC),
        surface = SplitEaseColors.ShellSurface,
        onSurface = Color(0xFFF4F6FC),
        surfaceVariant = Color(0xFF222842),
        onSurfaceVariant = Color(0xFFB4BAD0),
        outline = Color(0xFF8FA3FF),
        outlineVariant = Color(0xFF2E3550),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3B0906),
    )

/**
 * SplitEase Material 3 theme based on the Apzo SaaS light palette.
 *
 * @param darkTheme When true, uses the dark counterpart of the same brand.
 * @param dynamicColor Android 12+ dynamic color (off by default to keep brand consistent).
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SplitEaseTypography,
        content = content,
    )
}
