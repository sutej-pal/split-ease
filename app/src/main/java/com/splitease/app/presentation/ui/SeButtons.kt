package com.splitease.app.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.presentation.theme.SplitEaseColors

private val ButtonShape = RoundedCornerShape(16.dp)
private val FabShape = RoundedCornerShape(28.dp)

@Composable
fun SePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactive = enabled && !isLoading
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = interactive,
        shape = ButtonShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SplitEaseColors.Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // Keep brand fill while loading so the spinner stays readable.
                disabledContainerColor =
                    if (isLoading) {
                        SplitEaseColors.Primary
                    } else {
                        SplitEaseColors.PrimarySoft
                    },
                disabledContentColor =
                    if (isLoading) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        // Keep disabled label readable (NavyMuted on PrimarySoft is too faint).
                        SplitEaseColors.Navy.copy(alpha = 0.55f)
                    },
            ),
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text,
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        ),
                )
            }
        } else {
            Text(
                text,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
            )
        }
    }
}

@Composable
fun SeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactive = enabled && !isLoading
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = interactive,
        shape = ButtonShape,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = SplitEaseColors.PrimarySoft,
                contentColor = SplitEaseColors.PrimaryDark,
                disabledContainerColor = SplitEaseColors.PrimarySoft,
                disabledContentColor = SplitEaseColors.PrimaryDark,
            ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = SplitEaseColors.PrimaryDark,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactive = enabled && !isLoading
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = interactive,
        shape = ButtonShape,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = SplitEaseColors.Primary,
                disabledContentColor = SplitEaseColors.Primary,
            ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = interactive || isLoading),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = SplitEaseColors.Primary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Text link control (not a filled/chip button).
 *
 * Idle state is plain text; press shows a light ripple. Call sites own
 * alignment (center / end). Use [emphasized] for stronger app-bar actions
 * like Save.
 */
@Composable
fun SeTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    emphasized: Boolean = false,
    /** Idle label color; null uses brand primary (link accent). */
    color: Color? = null,
    // Equal insets for the press ripple. Pass horizontal 0 when the label must
    // sit flush with a form edge.
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
) {
    val interactive = enabled && !isLoading
    val labelColor = color ?: SplitEaseColors.Primary
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .wrapContentSize()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = interactive,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = labelColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                ),
            color =
                if (interactive || isLoading) {
                    labelColor
                } else {
                    SplitEaseColors.NavyMuted
                },
        )
    }
}

@Composable
fun SeExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Receipt,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = SplitEaseColors.Primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = FabShape,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SeFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = SplitEaseColors.Primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = FabShape,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Preview(name = "Buttons", showBackground = true)
@Composable
private fun SeButtonsPreview() {
    SePreview {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SePrimaryButton(text = "Continue", onClick = {})
            SePrimaryButton(text = "Loading", onClick = {}, isLoading = true)
            SeSecondaryButton(text = "Secondary", onClick = {})
            SeSecondaryButton(text = "Loading", onClick = {}, isLoading = true)
            SeOutlinedButton(text = "Outlined", onClick = {})
            SeOutlinedButton(text = "Loading", onClick = {}, isLoading = true)
            Row {
                SeTextButton(text = "Done", onClick = {})
                SeTextButton(text = "Loading", onClick = {}, isLoading = true)
                SeTextButton(text = "Disabled", onClick = {}, enabled = false)
            }
            SeExtendedFab(text = "Add expense", onClick = {}, icon = Icons.Filled.Add)
            SeFab(onClick = {}, contentDescription = "Add")
        }
    }
}
