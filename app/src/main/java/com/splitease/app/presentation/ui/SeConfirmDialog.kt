package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors

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
    val accent = tone.accent()
    val accentMuted = tone.mutedBackground()
    val showConfirm = !confirmLabel.isNullOrBlank() && onConfirm != null
    val resolvedDismissLabel =
        when {
            dismissLabel != null -> dismissLabel
            showConfirm -> stringResource(R.string.action_cancel)
            else -> stringResource(R.string.action_done)
        }
    val dismissClick = onDismissClick ?: onDismissRequest

    SeDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        maxWidth = 320.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle =
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                    ),
                color = SplitEaseColors.Navy,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            thickness = 1.dp,
            color = SplitEaseColors.Outline,
        )

        Text(
            text = body,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.NavyMuted,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (resolvedDismissLabel.isNotBlank()) {
                SeTextButton(
                    text = resolvedDismissLabel,
                    onClick = dismissClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            if (showConfirm) {
                SeTextButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    color = accent,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Accent palette for [SeConfirmDialog] badge + confirm action.
 */
enum class SeConfirmTone {
    /** Error / destructive (delete, remove, regenerate). */
    Danger,

    /** Caution / amber warning. */
    Warning,

    /** Brand primary for neutral / positive confirms and info. */
    Primary,
}

@Composable
private fun SeConfirmTone.accent(): Color =
    when (this) {
        SeConfirmTone.Danger -> MaterialTheme.colorScheme.error
        SeConfirmTone.Warning -> MaterialTheme.colorScheme.tertiary
        SeConfirmTone.Primary -> SplitEaseColors.Primary
    }

@Composable
private fun SeConfirmTone.mutedBackground(): Color =
    when (this) {
        SeConfirmTone.Danger ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        SeConfirmTone.Warning ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        SeConfirmTone.Primary ->
            SplitEaseColors.Primary.copy(alpha = 0.14f)
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
