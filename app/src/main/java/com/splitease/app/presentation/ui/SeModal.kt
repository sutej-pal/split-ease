package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors

/** Shared flat dialog card radius (matches [SeConfirmDialog]). */
val SeDialogCornerRadius = 20.dp

private val SeDialogHorizontalPadding = 22.dp

/**
 * Accent palette for dialog badges and primary actions.
 *
 * Shared by [SeModal] and [SeConfirmDialog].
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
fun SeConfirmTone.accent(): Color =
    when (this) {
        SeConfirmTone.Danger -> MaterialTheme.colorScheme.error
        SeConfirmTone.Warning -> MaterialTheme.colorScheme.tertiary
        SeConfirmTone.Primary -> SplitEaseColors.Primary
    }

@Composable
fun SeConfirmTone.mutedBackground(): Color =
    when (this) {
        SeConfirmTone.Danger ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        SeConfirmTone.Warning ->
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        SeConfirmTone.Primary ->
            SplitEaseColors.Primary.copy(alpha = 0.14f)
    }

/**
 * App-themed modal dialog — same chrome as [SeConfirmDialog]: icon badge + title,
 * hairline divider, optional muted body, custom content, and flat text actions.
 */
@Composable
fun SeModal(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SeConfirmTone = SeConfirmTone.Primary,
    body: String? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    maxWidth: Dp = 400.dp,
    dismissLabel: String? = null,
    onDismissClick: (() -> Unit)? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmTone: SeConfirmTone? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val showConfirm = !confirmLabel.isNullOrBlank() && onConfirm != null
    val resolvedDismissLabel =
        dismissLabel
            ?: when {
                showConfirm -> stringResource(R.string.action_cancel)
                else -> stringResource(R.string.action_close)
            }
    val showActions = resolvedDismissLabel.isNotBlank() || showConfirm
    val dismissClick = onDismissClick ?: onDismissRequest
    val confirmColor = (confirmTone ?: tone).accent()

    SeDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        maxWidth = maxWidth,
    ) {
        SeDialogHeader(
            title = title,
            icon = icon,
            tone = tone,
        )

        SeDialogDivider()

        if (!body.isNullOrBlank()) {
            SeDialogBody(text = body)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SeDialogHorizontalPadding,
                        end = SeDialogHorizontalPadding,
                        top = if (body.isNullOrBlank()) 14.dp else 0.dp,
                        bottom = if (showActions) 8.dp else 16.dp,
                    ),
            content = content,
        )

        if (showActions) {
            SeDialogActions(
                dismissLabel = resolvedDismissLabel,
                onDismissClick = dismissClick,
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                confirmEnabled = confirmEnabled,
                confirmColor = confirmColor,
            )
        }
    }
}

/**
 * Icon badge + left-aligned title row used by [SeModal] and [SeConfirmDialog].
 */
@Composable
fun SeDialogHeader(
    title: String,
    icon: ImageVector?,
    tone: SeConfirmTone,
    modifier: Modifier = Modifier,
) {
    val accent = tone.accent()
    val accentMuted = tone.mutedBackground()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SeDialogHorizontalPadding,
                    end = SeDialogHorizontalPadding,
                    top = SeDialogHorizontalPadding,
                    bottom = 14.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
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
}

@Composable
fun SeDialogDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = SeDialogHorizontalPadding),
        thickness = 1.dp,
        color = SplitEaseColors.Outline,
    )
}

@Composable
fun SeDialogBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SeDialogHorizontalPadding,
                    end = SeDialogHorizontalPadding,
                    top = 14.dp,
                    bottom = 8.dp,
                ),
        style = MaterialTheme.typography.bodyMedium,
        color = SplitEaseColors.NavyMuted,
    )
}

@Composable
fun SeDialogActions(
    dismissLabel: String,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmColor: Color = SplitEaseColors.Primary,
) {
    val showConfirm = !confirmLabel.isNullOrBlank() && onConfirm != null

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dismissLabel.isNotBlank()) {
            SeTextButton(
                text = dismissLabel,
                onClick = onDismissClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (showConfirm) {
            SeTextButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                color = confirmColor,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            )
        }
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
    maxWidth: Dp = 400.dp,
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

/** @deprecated Use [SeModal] title parameter or [SeDialogHeader] instead. */
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
        textAlign = TextAlign.Start,
    )
}

/** @deprecated Use [SeModal] body parameter or [SeDialogBody] instead. */
@Composable
fun SeModalBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    SeDialogBody(text = text, modifier = modifier.padding(top = 0.dp, bottom = 0.dp))
}

@Preview(name = "SeModal · picker", showBackground = true)
@Composable
private fun SeModalPreview() {
    SePreview {
        SeModal(
            onDismissRequest = {},
            title = "Choose category",
            icon = Icons.Filled.Category,
            body = "Pick a category for this expense.",
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = "Content slot",
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
        }
    }
}
