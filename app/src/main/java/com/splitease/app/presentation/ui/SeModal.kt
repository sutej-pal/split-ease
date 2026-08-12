package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.splitease.app.presentation.theme.SplitEaseColors

/** Shared flat dialog card radius (matches [SeConfirmDialog]). */
val SeDialogCornerRadius = 20.dp

/**
 * App-themed modal dialog shell — same flat elevated-surface card as
 * [SeConfirmDialog], with a free-form content slot for pickers and forms.
 *
 * Back / outside tap call [onDismissRequest]. Prefer [SeConfirmDialog] for
 * title + body + text-action confirms; use this for custom content.
 */
@Composable
fun SeModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    maxWidth: Dp = 400.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    SeDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        maxWidth = maxWidth,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * Flat surface card on the system scrim. Shared by [SeModal] and [SeConfirmDialog].
 */
@Composable
fun SeDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    maxWidth: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier =
                    modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                shape = RoundedCornerShape(SeDialogCornerRadius),
                colors =
                    CardDefaults.cardColors(
                        containerColor = SplitEaseColors.Surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}

/**
 * Modal title using primary text color.
 */
@Composable
fun SeModalTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
        textAlign = TextAlign.Center,
    )
}

/**
 * Modal body using muted text color.
 */
@Composable
fun SeModalBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = SplitEaseColors.NavyMuted,
        textAlign = TextAlign.Center,
    )
}

@Preview(name = "SeModal", showBackground = true)
@Composable
private fun SeModalPreview() {
    SePreview {
        SeModal(onDismissRequest = {}) {
            SeModalTitle("Modal title")
            SeModalBody("Supporting copy for the dialog.")
            SePrimaryButton(text = "Continue", onClick = {})
            SeTextButton(text = "Secondary action", onClick = {})
        }
    }
}
