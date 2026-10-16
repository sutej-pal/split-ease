package com.splitease.app.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * WCAG 2.x contrast for the error roles assigned in Theme.kt.
 * Ratios are computed from the token colours; they are not hard-coded.
 */
class ErrorContrastTest {
    @Test
    fun error_roles_meet_wcag_aa() {
        // Light: error on background, surface, and surfaceVariant (SurfaceMutedLight).
        assertAtLeastAa(ErrorLight, BackgroundLight, "light error on background")
        assertAtLeastAa(ErrorLight, SurfaceLight, "light error on surface")
        assertAtLeastAa(ErrorLight, SurfaceMutedLight, "light error on surfaceVariant")

        // Dark: surface and surfaceVariant are both SurfaceDark.
        assertAtLeastAa(ErrorDark, BackgroundDark, "dark error on background")
        assertAtLeastAa(ErrorDark, SurfaceDark, "dark error on surface and surfaceVariant")

        // onError on error.
        assertAtLeastAa(Color.White, ErrorLight, "light onError on error")
        assertAtLeastAa(BackgroundDark, ErrorDark, "dark onError on error")

        // onErrorContainer on errorContainer.
        assertAtLeastAa(ErrorLight, OweContainer, "light onErrorContainer on errorContainer")
        assertAtLeastAa(OweContainer, DarkErrorContainer, "dark onErrorContainer on errorContainer")
    }

    private fun assertAtLeastAa(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5) { "$label contrast ${"%.2f".format(ratio)}:1 is below 4.5:1" }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
        val darker = minOf(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(encoded: Float): Double {
            val value = encoded.toDouble()
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        val DarkErrorContainer = Color(0xFF8C1D18)
    }
}
