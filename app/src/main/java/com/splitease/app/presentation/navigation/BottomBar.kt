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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 14.dp,
                        shape = shape,
                        ambientColor = Color(0x241A1840),
                        spotColor = Color(0x331A1840),
                    )
                    .clip(shape)
                    .background(SplitEaseColors.Surface)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
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
    val selectedColor = SplitEaseColors.Primary
    val unselectedColor = SplitEaseColors.NavyMuted
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarContent",
    )
    val iconWell by animateColorAsState(
        targetValue = if (selected) SplitEaseColors.PrimarySoft else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarWell",
    )
    val label = stringResource(tab.labelRes)

    Column(
        modifier =
            modifier
                .semantics { this.selected = selected }
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconWell),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Preview(name = "Bottom bar", showBackground = true)
@Composable
private fun SplitEaseBottomBarPreview() {
    SePreview {
        SplitEaseBottomBar(currentRoute = Routes.TAB_FRIENDS, onTabSelected = {})
    }
}
