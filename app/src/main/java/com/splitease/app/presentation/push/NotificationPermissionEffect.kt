package com.splitease.app.presentation.push

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Asks for POST_NOTIFICATIONS once after sign-in (Android 13+).
 */
@Composable
fun NotificationPermissionEffect(
    viewModel: NotificationPermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PermissionChecker.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        if (!viewModel.canPrompt()) return@LaunchedEffect
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.markPrompted()
    }
}

@HiltViewModel
class NotificationPermissionViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        /**
         * Returns true if the OS prompt has not been launched yet.
         *
         * @return Whether to launch the runtime permission dialog.
         */
        suspend fun canPrompt(): Boolean =
            !appSettingsRepository.getNotificationPermissionPrompted()

        /**
         * Marks the one-time OS prompt as consumed after the dialog is launched.
         */
        suspend fun markPrompted() {
            appSettingsRepository.setNotificationPermissionPrompted(true)
        }
    }
