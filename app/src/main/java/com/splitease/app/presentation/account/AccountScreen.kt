package com.splitease.app.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.splitease.app.BuildConfig
import com.splitease.app.R
import com.splitease.app.domain.settings.ThemeMode
import com.splitease.app.presentation.ads.AdConsentManager
import com.splitease.app.presentation.navigation.bottomBarContentWindowInsets
import com.splitease.app.presentation.navigation.bottomBarScrollPadding
import com.splitease.app.presentation.settings.SettingsViewModel
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeMessageHost
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun AccountScreen(
    onBack: (() -> Unit)? = null,
    onOpenAccountProfile: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSpending: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenHelpCenter: () -> Unit,
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
    val supportEmail = stringResource(R.string.support_email)
    val supportSubject = stringResource(R.string.support_email_subject)
    var showRateDialog by rememberSaveable { mutableStateOf(false) }
    var mailErrorKey by remember { mutableIntStateOf(0) }

    if (showRateDialog) {
        RateAppDialog(onDismiss = { showRateDialog = false })
    }

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
        onOpenWhatsNew = onOpenWhatsNew,
        onOpenHelpCenter = onOpenHelpCenter,
        onContactSupport = {
            val opened = openSupportGmail(context, supportEmail, supportSubject)
            if (!opened) mailErrorKey += 1
        },
        onRateApp = { showRateDialog = true },
        mailErrorKey = mailErrorKey,
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
    onOpenWhatsNew: () -> Unit,
    onOpenHelpCenter: () -> Unit,
    onContactSupport: () -> Unit,
    onRateApp: () -> Unit,
    mailErrorKey: Int,
    onOpenAdPrivacy: () -> Unit,
    onSignOut: () -> Unit,
) {
    SeScreen(
        title = stringResource(R.string.nav_account),
        onBack = onBack,
        contentWindowInsets = bottomBarContentWindowInsets(),
        snackbarHost = {
            SeMessageHost(
                errorMessage =
                    stringResource(R.string.error_no_email_app).takeIf { mailErrorKey > 0 },
                infoMessage = null,
                replayKey = mailErrorKey,
            )
        },
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
                        .padding(bottom = bottomBarScrollPadding(includeFab = false)),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                AccountSettingsRow(
                    title = stringResource(R.string.account_profile_settings_title),
                    subtitle = stringResource(R.string.settings_account_item_subtitle),
                    onClick = onOpenAccountProfile,
                    leadingSize = 36.dp,
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
                            tint = accountSettingsIconColor(),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )

                HorizontalDivider(color = accountSettingsDividerColor())
                AccountSettingsRow(
                    title = stringResource(R.string.spending_title),
                    subtitle = stringResource(R.string.spending_hub_subtitle),
                    onClick = onOpenSpending,
                    leading = { AccountSettingsIcon(icon = Icons.AutoMirrored.Outlined.ShowChart) },
                )

                HorizontalDivider(color = accountSettingsDividerColor())
                AccountSettingsSectionHeader(text = stringResource(R.string.settings_preferences_section))
                AccountSettingsRow(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = themeModeLabel(themeMode),
                    onClick = onOpenAppearance,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.DarkMode) },
                )
                AccountSettingsRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle =
                        if (notificationsOn) {
                            stringResource(R.string.settings_notifications_on)
                        } else {
                            stringResource(R.string.settings_notifications_off)
                        },
                    onClick = onOpenNotifications,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.Notifications) },
                )

                HorizontalDivider(color = accountSettingsDividerColor())
                AccountSettingsSectionHeader(text = stringResource(R.string.settings_security))
                AccountSettingsRow(
                    title = stringResource(R.string.settings_security),
                    subtitle = stringResource(R.string.settings_security_item_subtitle),
                    onClick = onOpenSecurity,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.Lock) },
                )

                HorizontalDivider(color = accountSettingsDividerColor())
                AccountSettingsSectionHeader(text = stringResource(R.string.settings_help_section))
                AccountSettingsRow(
                    title = stringResource(R.string.settings_help_center),
                    subtitle = stringResource(R.string.settings_help_center_subtitle),
                    onClick = onOpenHelpCenter,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.HelpOutline) },
                )
                AccountSettingsRow(
                    title = stringResource(R.string.settings_contact_support),
                    subtitle = stringResource(R.string.settings_contact_support_subtitle),
                    onClick = onContactSupport,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.Email) },
                )
                AccountSettingsRow(
                    title = stringResource(R.string.settings_rate_app),
                    subtitle = stringResource(R.string.settings_rate_app_subtitle),
                    onClick = onRateApp,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.StarBorder) },
                )

                HorizontalDivider(color = accountSettingsDividerColor())
                AccountSettingsSectionHeader(text = stringResource(R.string.settings_about_section))
                AccountSettingsRow(
                    title = stringResource(R.string.whats_new_title),
                    subtitle =
                        stringResource(
                            R.string.whats_new_current_build,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    onClick = onOpenWhatsNew,
                    leading = { AccountSettingsIcon(icon = Icons.Outlined.NewReleases) },
                )

                if (privacyOptionsRequired) {
                    HorizontalDivider(color = accountSettingsDividerColor())
                    AccountSettingsRow(
                        title = stringResource(R.string.settings_ad_privacy_choices),
                        onClick = onOpenAdPrivacy,
                        leading = {},
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
                    modifier = Modifier.padding(horizontal = 16.dp),
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
            onOpenWhatsNew = {},
            onOpenHelpCenter = {},
            onContactSupport = {},
            onRateApp = {},
            mailErrorKey = 0,
            onOpenAdPrivacy = {},
            onSignOut = {},
        )
    }
}
