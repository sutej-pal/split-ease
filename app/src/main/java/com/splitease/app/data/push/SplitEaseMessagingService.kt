package com.splitease.app.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.splitease.app.MainActivity
import com.splitease.app.R
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives FCM messages and opens the related group on tap.
 *
 * Does not navigate on receive — only the notification tap sets
 * [AppSettingsRepository.setPendingNotificationGroupId] via [MainActivity].
 */
@AndroidEntryPoint
class SplitEaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var notificationPrefsCoordinator: NotificationPrefsCoordinator

    override fun onNewToken(token: String) {
        pushTokenRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val groupId = message.data[DATA_GROUP_ID]?.trim().orEmpty()
        val title =
            message.notification?.title
                ?: message.data["title"]
                ?: getString(R.string.app_name)
        val body =
            message.notification?.body
                ?: message.data["body"]
                ?: getString(R.string.notification_group_update_body)

        // onMessageReceived already runs off the main thread. Block until the
        // tray notification is posted so FCM does not tear down this service.
        runBlocking {
            runCatching {
                withTimeoutOrNull(PREFS_REFRESH_TIMEOUT_MS) {
                    notificationPrefsCoordinator.refreshFromRemote()
                }
            }
            if (appSettingsRepository.getNotificationsMutedAll()) return@runBlocking
            if (groupId.isNotBlank() &&
                appSettingsRepository.getGroupNotificationsMuted(groupId)
            ) {
                return@runBlocking
            }
            showNotification(groupId, title, body)
        }
    }

    private fun showNotification(
        groupId: String,
        title: String,
        body: String,
    ) {
        SplitEaseNotificationChannels.ensure(this)
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (groupId.isNotBlank()) {
                    putExtra(EXTRA_OPEN_GROUP_ID, groupId)
                    putExtra(DATA_GROUP_ID, groupId)
                }
            }
        val pending =
            PendingIntent.getActivity(
                this,
                groupId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, SplitEaseNotificationChannels.GROUP_UPDATES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

        if (!canPostNotifications()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(groupId.hashCode(), notification)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_OPEN_GROUP_ID = "open_group_id"

        /** FCM data key used when the system tray (not this service) displays the push. */
        const val DATA_GROUP_ID = "groupId"

        private const val PREFS_REFRESH_TIMEOUT_MS = 5_000L
    }
}
