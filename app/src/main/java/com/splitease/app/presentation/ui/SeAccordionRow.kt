package com.splitease.app.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.theme.SeLabelSmall
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Accordion list row for settings: label over value, optional leading icon tile, optional trailing chip,
 * rotating chevron, and an expandable filled panel below with no border lines.
 */
@Composable
fun SeAccordionRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailingChip: (@Composable () -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "accordionAngle",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(SeLayout.iconTileGap))
            }
            Column(modifier = Modifier.weight(1f)) {
                SeLabelSmall(
                    text = label,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                SeBodyMedium(
                    text = value,
                    fontWeight = FontWeight.Medium,
                    color = SplitEaseColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailingChip != null) {
                trailingChip()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SplitEaseColors.IconDefault,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        if (content != null) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SplitEaseColors.Background)
                            .padding(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}
