package com.splitease.app.presentation.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.theme.SplitEaseColors
import java.io.File

@Composable
fun SeIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 56,
) {
    Box(
        modifier =
            modifier
                .size(size.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/**
 * Group icon tile that prefers a custom [photoUrl], otherwise falls back to the type icon.
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
    val bitmap =
        remember(photoUrl, contentStamp) {
            loadLocalAvatarBitmap(context, photoUrl)
        }
    if (bitmap != null) {
        Box(
            modifier =
                modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(14.dp)),
        ) {
            Image(
                bitmap = bitmap,
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
 * Expense-style icon tile with a small actor avatar badge at the bottom-end corner.
 */
@Composable
fun SeIconTileWithAvatar(
    icon: ImageVector,
    tint: Color,
    actorName: String,
    actorPhotoUrl: String?,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    Box(modifier = modifier.size(size.dp)) {
        SeIconTile(icon = icon, tint = tint, size = size)
        SeAvatarBadge(
            name = actorName,
            photoUrl = actorPhotoUrl,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
            size = 18.dp,
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
    borderColor: Color = Color.White,
) {
    val context = LocalContext.current
    // Include file mtime so overwriting the same path still reloads the bitmap.
    val contentStamp = localAvatarContentStamp(photoUrl)
    val bitmap =
        remember(photoUrl, contentStamp) {
            loadLocalAvatarBitmap(context, photoUrl)
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
                )
                .clip(CircleShape)
                .background(SplitEaseColors.PrimaryDark),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsOf(name),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
                maxLines = 1,
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
        AvatarImageIO.decodeScaled(
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
            Uri.parse(photoUrl).path
        } else {
            photoUrl
        } ?: return 0L
    return File(path).takeIf { it.isFile }?.lastModified() ?: 0L
}

@Composable
fun SeListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}

@Composable
fun SeSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SeEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SeOutlinedButton(text = actionLabel, onClick = onAction)
        }
    }
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
    val bg = if (selected) SplitEaseColors.PrimarySoft else SplitEaseColors.Surface
    val content =
        when {
            !enabled -> SplitEaseColors.OutlineStrong
            selected -> SplitEaseColors.PrimaryDark
            else -> SplitEaseColors.Navy
        }
    val border =
        when {
            !enabled -> SplitEaseColors.Outline
            selected -> SplitEaseColors.Primary
            else -> SplitEaseColors.Outline
        }
    Row(
        modifier =
            modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold,
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
) {
    val bg = if (selected) SplitEaseColors.Primary else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else SplitEaseColors.Navy
    val border = if (selected) Color.Transparent else SplitEaseColors.OutlineStrong
    Column(
        modifier =
            modifier
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = content)
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
