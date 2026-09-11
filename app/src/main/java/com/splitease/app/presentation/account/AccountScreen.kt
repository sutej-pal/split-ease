package com.splitease.app.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.ads.AdConsentManager
import com.splitease.app.presentation.navigation.bottomBarContentWindowInsets
import com.splitease.app.presentation.navigation.bottomBarScrollPadding
import com.splitease.app.presentation.settings.SettingsViewModel
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun AccountScreen(
    onBack: (() -> Unit)? = null,
    onOpenAccountProfile: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSpending: () -> Unit,
    onSignOut: () -> Unit = {},
    isSigningOut: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val profile by accountViewModel.profile.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val muteAll by viewModel.notificationsMutedAll.collectAsStateWithLifecycle()
    val privacyOptionsRequired by AdConsentManager.privacyOptionsRequired
    val context = LocalContext.current
    var osNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        osNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val notificationsOn = osNotificationsEnabled && !muteAll

    AccountScreenContent(
        displayName = profile.displayName,
        photoUrl = profile.photoUrl,
        themeMode = themeMode,
        notificationsOn = notificationsOn,
        privacyOptionsRequired = privacyOptionsRequired,
        isSigningOut = isSigningOut,
        onBack = onBack,
        onOpenAccountProfile = onOpenAccountProfile,
        onOpenAppearance = onOpenAppearance,
        onOpenNotifications = onOpenNotifications,
        onOpenSecurity = onOpenSecurity,
        onOpenSpending = onOpenSpending,
        onOpenAdPrivacy = {
            (context as? FragmentActivity)?.let { activity ->
                AdConsentManager.showPrivacyOptionsForm(activity)
            }
        },
        onSignOut = onSignOut,
    )
}

@Composable
private fun AccountScreenContent(
    displayName: String,
    photoUrl: String?,
    themeMode: ThemeMode,
    notificationsOn: Boolean,
    privacyOptionsRequired: Boolean,
    isSigningOut: Boolean,
    onBack: (() -> Unit)?,
    onOpenAccountProfile: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSpending: () -> Unit,
    onOpenAdPrivacy: () -> Unit,
    onSignOut: () -> Unit,
) {
    SeScreen(
        title = stringResource(R.string.nav_account),
        onBack = onBack,
        contentWindowInsets = bottomBarContentWindowInsets(),
        content = { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.values.calculateTopPadding(),
                            start = padding.values.calculateStartPadding(layoutDirection),
                            end = padding.values.calculateEndPadding(layoutDirection),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = bottomBarScrollPadding(includeFab = false)),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                AccountGroupCard(onClick = onOpenAccountProfile) {
                    SeListRow(
                        title = stringResource(R.string.account_profile_settings_title),
                        subtitle = stringResource(R.string.settings_account_item_subtitle),
                        leading = {
                            SeAvatarBadge(
                                name = displayName.ifBlank { stringResource(R.string.account_name_fallback) },
                                photoUrl = photoUrl,
                                size = 36.dp,
                                borderWidth = 0.dp,
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = SplitEaseColors.NavyMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = null,
                        showDivider = false,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                AccountGroupCard(onClick = onOpenSpending) {
                    SeListRow(
                        title = stringResource(R.string.spending_title),
                        subtitle = stringResource(R.string.spending_hub_subtitle),
                        leading = {
                            SeIconTile(
                                icon = Icons.AutoMirrored.Filled.ShowChart,
                                tint = SplitEaseColors.OwedToYou,
                                size = 40,
                            )
                        },
                        trailing = { AccountChevron() },
                        onClick = null,
                        showDivider = false,
                    )
                }

                SeSectionHeader(text = stringResource(R.string.settings_preferences_section))
                AccountGroupCard {
                    SeListRow(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = themeModeLabel(themeMode),
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.DarkMode,
                                tint = SplitEaseColors.IconOther,
                                size = 40,
                            )
                        },
                        trailing = { AccountChevron() },
                        onClick = onOpenAppearance,
                        showDivider = true,
                    )
                    SeListRow(
                        title = stringResource(R.string.settings_notifications),
                        subtitle =
                            if (notificationsOn) {
                                stringResource(R.string.settings_notifications_on)
                            } else {
                                stringResource(R.string.settings_notifications_off)
                            },
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Notifications,
                                tint = SplitEaseColors.IconFriends,
                                size = 40,
                            )
                        },
                        trailing = { AccountChevron() },
                        onClick = onOpenNotifications,
                        showDivider = false,
                    )
                }

                SeSectionHeader(text = stringResource(R.string.settings_security))
                AccountGroupCard(onClick = onOpenSecurity) {
                    SeListRow(
                        title = stringResource(R.string.settings_security),
                        subtitle = stringResource(R.string.settings_security_item_subtitle),
                        leading = {
                            SeIconTile(
                                icon = Icons.Filled.Lock,
                                tint = SplitEaseColors.IconHome,
                                size = 40,
                            )
                        },
                        trailing = { AccountChevron() },
                        onClick = null,
                        showDivider = false,
                    )
                }

                if (privacyOptionsRequired) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeListRow(
                        title = stringResource(R.string.settings_ad_privacy_choices),
                        onClick = onOpenAdPrivacy,
                        showDivider = false,
                    )
                }

                var showSignOutConfirm by remember { mutableStateOf(false) }

                if (showSignOutConfirm) {
                    SeConfirmDialog(
                        title = stringResource(R.string.sign_out_confirm_title),
                        body = stringResource(R.string.sign_out_confirm_message),
                        onDismissRequest = {
                            if (!isSigningOut) showSignOutConfirm = false
                        },
                        confirmLabel = stringResource(R.string.action_sign_out),
                        onConfirm = onSignOut,
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        tone = SeConfirmTone.Danger,
                        confirmBusy = isSigningOut,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                SeOutlinedButton(
                    text = stringResource(R.string.action_sign_out),
                    onClick = { showSignOutConfirm = true },
                    isLoading = isSigningOut,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun AccountGroupCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SplitEaseColors.Surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

@Composable
private fun AccountChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = SplitEaseColors.NavyMuted,
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }

@Preview(name = "Account", showBackground = true, heightDp = 780)
@Composable
private fun AccountScreenPreview() {
    SePreview {
        AccountScreenContent(
            displayName = "Alex Rivera",
            photoUrl = null,
            themeMode = ThemeMode.SYSTEM,
            notificationsOn = true,
            privacyOptionsRequired = false,
            isSigningOut = false,
            onBack = null,
            onOpenAccountProfile = {},
            onOpenAppearance = {},
            onOpenNotifications = {},
            onOpenSecurity = {},
            onOpenSpending = {},
            onOpenAdPrivacy = {},
            onSignOut = {},
        )
    }
}
