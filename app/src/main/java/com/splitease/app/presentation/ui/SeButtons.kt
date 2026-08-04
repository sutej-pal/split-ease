package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.presentation.theme.SplitEaseColors

private val ButtonShape = RoundedCornerShape(12.dp)
private val FabShape = RoundedCornerShape(28.dp)

@Composable
fun SePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !isLoading,
        shape = ButtonShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SplitEaseColors.Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = SplitEaseColors.PrimarySoft,
                // Keep disabled label readable (NavyMuted on PrimarySoft is too faint).
                disabledContentColor = SplitEaseColors.Navy.copy(alpha = 0.55f),
            ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
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
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = SplitEaseColors.PrimarySoft,
                contentColor = SplitEaseColors.PrimaryDark,
            ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = SplitEaseColors.Primary,
            ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SeTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
    ) {
        Text(text, color = if (enabled) SplitEaseColors.Primary else SplitEaseColors.NavyMuted)
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
            SeSecondaryButton(text = "Secondary", onClick = {})
            SeOutlinedButton(text = "Outlined", onClick = {})
            Row {
                SeTextButton(text = "Done", onClick = {})
                SeTextButton(text = "Disabled", onClick = {}, enabled = false)
            }
            SeExtendedFab(text = "Add expense", onClick = {}, icon = Icons.Filled.Add)
            SeFab(onClick = {}, contentDescription = "Add")
        }
    }
}
