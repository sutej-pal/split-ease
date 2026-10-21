package com.splitease.app.data.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.splitease.app.MainActivity
import com.splitease.app.R
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a group-update tray notification that opens the group on tap.
 *
 * Used by FCM delivery and by the activity pull when a new remote event
 * arrives while this device is already syncing.
 */
@Singleton
class GroupUpdateNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        /**
         * Shows one notification for [groupId], replacing any previous one for that group.
         *
         * No-ops when notifications are muted, the group is muted, or the OS
         * has not granted permission.
         *
         * @param groupId Group to open. Blank still shows a generic notification.
         * @param title Tray title. Blank falls back to the app name.
         * @param body Tray body. Blank falls back to the generic group-update line.
         */
        suspend fun show(
            groupId: String,
            title: String,
            body: String,
        ) {
            val trimmedGroup = groupId.trim()
            if (appSettingsRepository.getNotificationsMutedAll()) return
            if (trimmedGroup.isNotBlank() &&
                appSettingsRepository.getGroupNotificationsMuted(trimmedGroup)
            ) {
                return
            }
            if (!canPostNotifications()) {
                android.util.Log.w(TAG, "Skip group notification: POST_NOTIFICATIONS not granted")
                return
            }
            SplitEaseNotificationChannels.ensure(context)
            val resolvedTitle = title.trim().ifBlank { context.getString(R.string.app_name) }
            val resolvedBody =
                body.trim().ifBlank { context.getString(R.string.notification_group_update_body) }
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (trimmedGroup.isNotBlank()) {
                        putExtra(EXTRA_OPEN_GROUP_ID, trimmedGroup)
                        putExtra(DATA_GROUP_ID, trimmedGroup)
                    }
                }
            val requestCode = if (trimmedGroup.isBlank()) GENERIC_REQUEST else trimmedGroup.hashCode()
            val pending =
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, SplitEaseNotificationChannels.GROUP_UPDATES)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setContentTitle(resolvedTitle)
                    .setContentText(resolvedBody)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(resolvedBody))
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .build()
            @SuppressLint("MissingPermission")
            runCatching {
                NotificationManagerCompat.from(context).notify(requestCode, notification)
            }.onFailure { err ->
                android.util.Log.w(TAG, "Failed to post group notification", err)
            }
        }

        private fun canPostNotifications(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        companion object {
            const val EXTRA_OPEN_GROUP_ID = "open_group_id"

            /** FCM data key and intent extra used when the tray opens the group. */
            const val DATA_GROUP_ID = "groupId"

            private const val GENERIC_REQUEST = 0x51E
            private const val TAG = "GroupUpdateNotifier"
        }
    }
