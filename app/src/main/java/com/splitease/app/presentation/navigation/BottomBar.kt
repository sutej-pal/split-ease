package com.splitease.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
    ) {
        MainTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = SplitEaseColors.Primary,
                        selectedTextColor = SplitEaseColors.Primary,
                        indicatorColor = SplitEaseColors.PrimarySoft,
                        unselectedIconColor = SplitEaseColors.NavyMuted,
                        unselectedTextColor = SplitEaseColors.NavyMuted,
                    ),
            )
        }
    }
}

@Preview(name = "Bottom bar", showBackground = true)
@Composable
private fun SplitEaseBottomBarPreview() {
    SePreview {
        SplitEaseBottomBar(currentRoute = Routes.TAB_GROUPS, onTabSelected = {})
    }
}
