package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.splitease.app.presentation.theme.SplitEaseColors

private val ModalShape = RoundedCornerShape(16.dp)

/**
 * App-themed modal dialog (light Apzo surface).
 *
 * Back / outside tap call [onDismissRequest]. Content should use [SePrimaryButton],
 * [SeTextButton], and other `Se*` controls so styling stays consistent.
 *
 * @param onDismissRequest Invoked on system back or (when enabled) outside tap.
 * @param dismissOnBackPress Whether the system back key dismisses the modal.
 * @param dismissOnClickOutside Whether tapping the scrim dismisses the modal.
 * @param content Modal body; typically title, body text, and action buttons.
 */
@Composable
fun SeModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
            ),
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = ModalShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = SplitEaseColors.Surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/**
 * Centered modal title using navy brand copy.
 */
@Composable
fun SeModalTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
        textAlign = TextAlign.Center,
    )
}

/**
 * Centered modal body using muted navy.
 */
@Composable
fun SeModalBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
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
