package com.splitease.app.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.splitease.app.R

/**
 * App confirmation / info dialog.
 *
 * Flat elevated-surface card: icon badge + title, hairline divider, muted body,
 * and flat text actions (optional dismiss + confirm).
 *
 * @param title Sentence-case title next to the icon badge.
 * @param body Supporting explanation in muted text.
 * @param onDismissRequest Back / outside tap / dismiss action.
 * @param confirmLabel Confirm action label; omit for a single dismiss/Done action.
 * @param onConfirm Confirm action; required when [confirmLabel] is set.
 * @param dismissLabel Leading action label. Pass empty string to hide. When null,
 *   defaults to Cancel (if confirm is shown) or Done (info-only).
 * @param onDismissClick Dismiss-button click; defaults to [onDismissRequest].
 * @param icon Leading badge icon.
 * @param tone Accent for the badge and confirm label.
 */
@Composable
fun SeConfirmDialog(
    title: String,
    body: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismissClick: (() -> Unit)? = null,
    icon: ImageVector = Icons.Filled.Warning,
    tone: SeConfirmTone = SeConfirmTone.Danger,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    val showConfirm = !confirmLabel.isNullOrBlank() && onConfirm != null
    val resolvedDismissLabel =
        when {
            dismissLabel != null -> dismissLabel
            showConfirm -> stringResource(R.string.action_cancel)
            else -> stringResource(R.string.action_done)
        }

    SeModal(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        icon = icon,
        tone = tone,
        body = body,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        dismissLabel = resolvedDismissLabel,
        onDismissClick = onDismissClick ?: onDismissRequest,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmTone = tone,
    )
}

@Preview(name = "SeConfirmDialog · dark", showBackground = true)
@Composable
private fun SeConfirmDialogDarkPreview() {
    SePreview(darkTheme = true) {
        SeConfirmDialog(
            title = stringResource(R.string.invite_link_change_confirm_title),
            body = stringResource(R.string.invite_link_change_confirm_body),
            confirmLabel = stringResource(R.string.action_change_link),
            onDismissRequest = {},
            onConfirm = {},
            icon = Icons.Filled.Link,
            tone = SeConfirmTone.Danger,
        )
    }
}

@Preview(name = "SeConfirmDialog · light info", showBackground = true)
@Composable
private fun SeConfirmDialogInfoPreview() {
    SePreview {
        SeConfirmDialog(
            title = "Simplify group debts",
            body = "Automatically combines debts to reduce repayments.",
            onDismissRequest = {},
            icon = Icons.Filled.Warning,
            tone = SeConfirmTone.Primary,
        )
    }
}
