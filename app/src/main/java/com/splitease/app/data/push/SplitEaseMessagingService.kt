package com.splitease.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives FCM messages and opens the related group on tap.
 */
@AndroidEntryPoint
class SplitEaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        pushTokenRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val groupId = message.data["groupId"]?.trim().orEmpty()
        val title =
            message.notification?.title
                ?: message.data["title"]
                ?: getString(R.string.app_name)
        val body =
            message.notification?.body
                ?: message.data["body"]
                ?: getString(R.string.notification_group_update_body)

        if (groupId.isNotBlank()) {
            scope.launch {
                appSettingsRepository.setPendingNotificationGroupId(groupId)
            }
        }

        ensureChannel()
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (groupId.isNotBlank()) {
                    putExtra(EXTRA_OPEN_GROUP_ID, groupId)
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
                .Builder(this, CHANNEL_ID)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_group_updates),
                NotificationManager.IMPORTANCE_HIGH,
            )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_GROUP_ID = "open_group_id"
        private const val CHANNEL_ID = "group_updates"
    }
}
