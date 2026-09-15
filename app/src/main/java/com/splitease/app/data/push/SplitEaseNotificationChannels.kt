package com.splitease.app.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.splitease.app.R

/**
 * Creates FCM notification channels at process start so background system
 * notifications can target [GROUP_UPDATES] even when [SplitEaseMessagingService]
 * has not run yet.
 */
object SplitEaseNotificationChannels {
    const val GROUP_UPDATES = "group_updates"

    /**
     * Ensures the group-updates channel exists.
     *
     * @param context App or service context.
     */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                GROUP_UPDATES,
                context.getString(R.string.notification_channel_group_updates),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(true)
            }
        manager.createNotificationChannel(channel)
    }
}
