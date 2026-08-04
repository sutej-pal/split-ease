package com.splitease.app.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SePreview

enum class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    GROUPS(Routes.TAB_GROUPS, R.string.nav_groups, Icons.Filled.Group),
    FRIENDS(Routes.TAB_FRIENDS, R.string.nav_friends, Icons.Filled.Person),
    ACTIVITY(Routes.TAB_ACTIVITY, R.string.nav_activity, Icons.AutoMirrored.Filled.ShowChart),
    ACCOUNT(Routes.TAB_ACCOUNT, R.string.nav_account, Icons.Filled.AccountCircle),
}

@Composable
fun SplitEaseBottomBar(
    currentRoute: String?,
    onTabSelected: (MainTab) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0x14000000),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                BottomBarTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) SplitEaseColors.Primary else SplitEaseColors.NavyMuted,
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarContent",
    )
    val bubbleColor by animateColorAsState(
        targetValue = if (selected) SplitEaseColors.PrimarySoft else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarBubble",
    )
    val label = stringResource(tab.labelRes)

    Box(
        modifier =
            modifier
                .semantics {
                    this.selected = selected
                }.clip(RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 64.dp, height = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "Bottom bar", showBackground = true)
@Composable
private fun SplitEaseBottomBarPreview() {
    SePreview {
        SplitEaseBottomBar(currentRoute = Routes.TAB_FRIENDS, onTabSelected = {})
    }
}
