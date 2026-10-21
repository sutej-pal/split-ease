package com.splitease.app.presentation.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Brand-styled switch colors.
 * - Checked: [SplitEaseColors.Primary] track with pure white thumb and no border.
 * - Unchecked: Soft gray track with [SplitEaseColors.NavyMuted] thumb and subtle border outline.
 */
@Composable
fun seSwitchColors(): SwitchColors {
    val uncheckedTrack = SplitEaseColors.SwitchUncheckedTrack
    val uncheckedBorder = SplitEaseColors.SwitchUncheckedBorder

    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = SplitEaseColors.Primary,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = Color.Transparent,
        uncheckedThumbColor = SplitEaseColors.NavyMuted,
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = uncheckedBorder,
        uncheckedIconColor = Color.Transparent,
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
        disabledCheckedTrackColor = SplitEaseColors.Primary.copy(alpha = 0.4f),
        disabledCheckedBorderColor = Color.Transparent,
        disabledCheckedIconColor = Color.Transparent,
        disabledUncheckedThumbColor = SplitEaseColors.NavyMuted.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = uncheckedTrack.copy(alpha = 0.5f),
        disabledUncheckedBorderColor = uncheckedBorder.copy(alpha = 0.5f),
        disabledUncheckedIconColor = Color.Transparent,
    )
}

/**
 * SplitEase design-system switch component.
 */
@Composable
fun SeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = seSwitchColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Preview(name = "SeSwitch · Light", showBackground = true)
@Composable
private fun SeSwitchLightPreview() {
    SePreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeSwitch(checked = false, onCheckedChange = {})
                SeSwitch(checked = true, onCheckedChange = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeSwitch(checked = false, onCheckedChange = {}, enabled = false)
                SeSwitch(checked = true, onCheckedChange = {}, enabled = false)
            }
        }
    }
}

@Preview(name = "SeSwitch · Dark", showBackground = true)
@Composable
private fun SeSwitchDarkPreview() {
    SePreview(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeSwitch(checked = false, onCheckedChange = {})
                SeSwitch(checked = true, onCheckedChange = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeSwitch(checked = false, onCheckedChange = {}, enabled = false)
                SeSwitch(checked = true, onCheckedChange = {}, enabled = false)
            }
        }
    }
}
