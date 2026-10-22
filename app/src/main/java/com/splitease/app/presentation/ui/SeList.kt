package com.splitease.app.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeLabelSmall
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Leading icon well (pastel fill) with **16.dp rounded corners**.
 * Do not remove the [RoundedCornerShape] clip or make this square.
 */
@Composable
fun SeIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 56,
) {
    val fillAlpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.08f else 0.16f
    Box(
        modifier =
            modifier
                .size(size.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tint.copy(alpha = fillAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.48f).dp),
        )
    }
}

/**
 * Group icon tile that prefers a custom [photoUrl], otherwise falls back to the type icon.
 * Photo well uses **14.dp rounded corners**; the type fallback uses [SeIconTile] (16.dp).
 * Do not remove these clips or make the tiles square.
 */
@Composable
fun SeGroupIconTile(
    photoUrl: String?,
    fallbackIcon: ImageVector,
    fallbackTint: Color,
    modifier: Modifier = Modifier,
    size: Int = 56,
) {
    val context = LocalContext.current
    val contentStamp = localAvatarContentStamp(photoUrl)
    val bitmap by produceState<ImageBitmap?>(null, photoUrl, contentStamp) {
        if (photoUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val loaded =
            withContext(Dispatchers.IO) {
                loadLocalAvatarBitmap(context, photoUrl)
            }
        if (loaded != null) {
            value = loaded
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Box(
            modifier =
                modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(14.dp)),
        ) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        SeIconTile(
            icon = fallbackIcon,
            tint = fallbackTint,
            modifier = modifier,
            size = size,
        )
    }
}

/**
 * Circular avatar showing a local photo when available, otherwise initials.
 */
@Composable
fun SeAvatarBadge(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    borderWidth: Dp = 1.5.dp,
    borderColor: Color = SplitEaseColors.Surface,
) {
    val context = LocalContext.current
    // Include file mtime so overwriting the same path still reloads the bitmap.
    val contentStamp = localAvatarContentStamp(photoUrl)
    val bitmap by produceState<ImageBitmap?>(null, photoUrl, contentStamp) {
        if (photoUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val loaded =
            withContext(Dispatchers.IO) {
                loadLocalAvatarBitmap(context, photoUrl)
            }
        if (loaded != null) {
            value = loaded
        }
    }
    Box(
        modifier =
            modifier
                .size(size)
                .then(
                    if (borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor, CircleShape)
                    } else {
                        Modifier
                    },
                ).clip(CircleShape)
                .background(SplitEaseColors.PrimaryDark),
        contentAlignment = Alignment.Center,
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SeLabelSmall(
                text = initialsOf(name),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> "${parts[0].first()}${parts.last().first()}".uppercase()
    }
}

private fun loadLocalAvatarBitmap(
    context: android.content.Context,
    photoUrl: String?,
): ImageBitmap? {
    if (photoUrl.isNullOrBlank()) return null
    return runCatching {
        AvatarImageIO
            .decodeScaled(
            context = context,
            photoUrl = photoUrl,
            maxSidePx = AvatarImageIO.PREVIEW_MAX_SIDE_PX,
        )?.asImageBitmap()
    }.getOrNull()
}

/** Local file last-modified (0 for remote/content URIs) used to bust avatar bitmap cache. */
private fun localAvatarContentStamp(photoUrl: String?): Long {
    if (photoUrl.isNullOrBlank()) return 0L
    if (
        photoUrl.startsWith("http://", ignoreCase = true) ||
        photoUrl.startsWith("https://", ignoreCase = true) ||
        photoUrl.startsWith("content:", ignoreCase = true)
    ) {
        return 0L
    }
    val path =
        if (photoUrl.startsWith("file:", ignoreCase = true)) {
            photoUrl.toUri().path
        } else {
            photoUrl
        } ?: return 0L
    return File(path).takeIf { it.isFile }?.lastModified() ?: 0L
}

/**
 * White/surface card used on tinted canvases (home, friends, activity).
 */
@Composable
fun SeInsetCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SeLayout.cardRadius)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SplitEaseColors.Surface)
                .border(1.dp, SplitEaseColors.Outline, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/**
 * Soft indigo wash for overall-balance headlines.
 */
@Composable
fun SeSummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SeLayout.cardRadius)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SplitEaseColors.PrimarySoft)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Amount-first list row: leading tile, name + caption, large trailing balance.
 * Used on Groups / Friends instead of bordered cards.
 */
@Composable
fun SeLedgerRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    amount: String? = null,
    amountTone: SeMoneyTone = SeMoneyTone.NEUTRAL,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = SeLayout.detailHorizontal, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(SeLayout.iconTileGap))
            }
            Column(modifier = Modifier.weight(1f)) {
                SeTitleMedium(
                    text = title,
                    color = SplitEaseColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SeBodySmall(
                        text = subtitle,
                        color = SplitEaseColors.NavyMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            } else if (!amount.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                SeTitleLarge(
                    text = amount,
                    color = amountTone.color(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        extra()
    }
}

@Composable
fun SeListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    statusLabel: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    stateDescription: String? = null,
    expanded: Boolean? = null,
    onExpandOrCollapse: (() -> Unit)? = null,
    showDivider: Boolean = true,
    horizontalPadding: Dp = 24.dp,
) {
    val rowModifier =
        if (statusLabel != null) {
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        listOfNotNull(
                            title,
                            subtitle,
                            statusLabel.lowercase(),
                        ).joinToString(", ")
                }
        } else {
            modifier.fillMaxWidth()
        }
    Column(modifier = rowModifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (stateDescription != null || (expanded != null && onExpandOrCollapse != null)) {
                            Modifier.semantics(
                                properties = {
                                    if (stateDescription != null) {
                                        this.stateDescription = stateDescription
                                    }
                                    if (expanded != null && onExpandOrCollapse != null) {
                                        if (expanded) {
                                            collapse {
                                                onExpandOrCollapse()
                                                true
                                            }
                                        } else {
                                            expand {
                                                onExpandOrCollapse()
                                                true
                                            }
                                        }
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (onClick != null) {
                            Modifier
                                .heightIn(min = 48.dp)
                                .clickable(
                                    onClick = onClick,
                                    role = Role.Button,
                                    onClickLabel = onClickLabel,
                                )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(SeLayout.iconTileGap))
            }
            Column(modifier = Modifier.weight(1f)) {
                SeTitleSmall(
                    text = title,
                    fontWeight = FontWeight.Normal,
                    color = SplitEaseColors.Navy,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    SeBodySmall(
                        text = subtitle,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
            }
            if (statusLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                SeAssistChip(
                    text = statusLabel,
                    modifier = Modifier.clearAndSetSemantics { },
                    onClick = { onClick?.invoke() },
                )
            }
            when {
                trailing != null -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailing()
                }
                trailingIcon != null -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    SeListRowTrailingIcon(imageVector = trailingIcon)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}

/**
 * Navigation variant of [SeListRow]: 40dp tinted [SeIconTile], title / muted subtitle,
 * trailing [SeChevron], no divider. Use inside [SeListCard] / [SeListSection].
 */
@Composable
fun SeListRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    statusLabel: String? = null,
    tint: Color = SplitEaseColors.Primary,
    iconSize: Int = SeLayout.listRowIconTile,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    showDivider: Boolean = false,
) {
    SeListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        statusLabel = statusLabel,
        leading = { SeIconTile(icon = icon, tint = tint, size = iconSize) },
        trailingIcon = trailingIcon,
        onClick = onClick,
        showDivider = showDivider,
    )
}

/**
 * Full-width surface card that groups [SeListRow]s on a settings-style screen.
 * Use [SeListSection] when the card needs a [SeSectionHeader] above it.
 */
@Composable
fun SeListCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(SplitEaseColors.Surface),
        content = content,
    )
}

/**
 * [SeSectionHeader] followed by a [SeListCard] of rows.
 *
 * ```
 * SeListSection(title = stringResource(R.string.settings_help_section)) {
 *     SeListRow(title = ..., icon = ..., onClick = ...)
 *     SeListRow(title = ..., icon = ..., onClick = ...)
 * }
 * ```
 */
@Composable
fun SeListSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SeLayout.screenHorizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SeSectionHeader(text = title, horizontalPadding = horizontalPadding)
        SeListCard(content = content)
    }
}

/** Compact trailing action icon size (e.g. inline delete action in a list row). */
val SeTrailingActionIconSize: Dp = 20.dp

/** Same 24dp trailing icon slot as [SeChevron]. Parent row centers it. */
@Composable
fun SeListRowTrailingIcon(
    imageVector: ImageVector,
    tint: Color = SplitEaseColors.NavyMuted,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}

/** Muted trailing chevron for navigation rows. Mirrors in RTL. */
@Composable
fun SeChevron(
    modifier: Modifier = Modifier,
    tint: Color = SplitEaseColors.NavyMuted,
) {
    SeListRowTrailingIcon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun SeSectionHeader(
    text: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SeLayout.screenHorizontal,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = SplitEaseColors.Outline,
        )
        if (!text.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeBodySmall(
                    text = text,
                    color = SplitEaseColors.Navy,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun SeEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SplitEaseColors.Primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        SeBodyLarge(
            text = message,
            color = SplitEaseColors.NavyMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            SePrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun SeAssistChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { SeLabelSmall(text = text, fontWeight = FontWeight.Medium) },
        leadingIcon = leadingIcon,
        enabled = enabled,
        modifier = modifier,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = SplitEaseColors.PrimarySoft,
                labelColor = SplitEaseColors.Primary,
                leadingIconContentColor = SplitEaseColors.Primary,
            ),
        border = null,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun SeActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val bg = if (selected) SplitEaseColors.Primary else SplitEaseColors.Surface
    val content =
        when {
            !enabled -> SplitEaseColors.OutlineStrong
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> SplitEaseColors.Navy
        }
    val border =
        when {
            !enabled -> SplitEaseColors.Outline
            selected -> Color.Transparent
            else -> SplitEaseColors.Outline
        }
    Row(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(22.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        SeLabelLarge(
            text = label,
            color = content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Horizontally scrolling action-chip strip. Use this inside a vertical
 * [androidx.compose.foundation.lazy.LazyColumn] so chips overflow instead of
 * wrapping or being squeezed to the viewport width.
 */
@Composable
fun SeActionChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(horizontal = SeLayout.detailHorizontal, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun SeTypeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg =
        when {
            !enabled && selected -> SplitEaseColors.Primary.copy(alpha = 0.5f)
            selected -> SplitEaseColors.Primary
            else -> Color.Transparent
        }
    val content =
        when {
            !enabled ->
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                } else {
                    SplitEaseColors.Navy.copy(alpha = 0.5f)
                }
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> SplitEaseColors.Navy
        }
    val border =
        when {
            selected -> Color.Transparent
            !enabled -> SplitEaseColors.OutlineStrong.copy(alpha = 0.5f)
            else -> SplitEaseColors.OutlineStrong
        }
    Column(
        modifier =
            modifier
                .height(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        SeBodyMedium(label, color = content)
    }
}

@Preview(name = "List + tiles", showBackground = true)
@Composable
private fun SeListPreview() {
    SePreview {
        Column {
            SeSectionHeader("Groups")
            SeListRow(
                title = "Roommates",
                subtitle = "you owe ₹420.00",
                leading = { SeIconTile(Icons.Filled.Home, SplitEaseColors.IconHome) },
                onClick = {},
            )
            SeListRow(
                title = "Friends",
                subtitle = "settled up",
                leading = { SeIconTile(Icons.Filled.Group, SplitEaseColors.IconFriends) },
                onClick = {},
            )
            Spacer(modifier = Modifier.height(12.dp))
            SeEmptyState(message = "No groups yet.", actionLabel = "Create group", onAction = {})
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SeTypeChip("Friends", Icons.Filled.Group, true, {}, Modifier.weight(1f))
                SeTypeChip("Home", Icons.Filled.Home, false, {}, Modifier.weight(1f))
                SeTypeChip("Other", Icons.AutoMirrored.Filled.List, false, {}, Modifier.weight(1f))
            }
        }
    }
}
