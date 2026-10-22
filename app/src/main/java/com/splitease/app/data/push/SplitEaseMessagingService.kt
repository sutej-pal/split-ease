package com.splitease.app.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.splitease.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives FCM data messages and posts a tray notification.
 *
 * Does not navigate on receive. The notification tap carries the group id
 * into [com.splitease.app.MainActivity].
 */
@AndroidEntryPoint
class SplitEaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    @Inject
    lateinit var notificationPrefsCoordinator: NotificationPrefsCoordinator

    @Inject
    lateinit var groupUpdateNotifier: GroupUpdateNotifier

    override fun onNewToken(token: String) {
        pushTokenRegistrar.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val groupId = message.data[GroupUpdateNotifier.DATA_GROUP_ID]?.trim().orEmpty()
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
            groupUpdateNotifier.show(groupId, title, body)
        }
    }

    companion object {
        const val EXTRA_OPEN_GROUP_ID = GroupUpdateNotifier.EXTRA_OPEN_GROUP_ID

        /** FCM data key used when the system tray (not this service) displays the push. */
        const val DATA_GROUP_ID = GroupUpdateNotifier.DATA_GROUP_ID

        private const val PREFS_REFRESH_TIMEOUT_MS = 5_000L
    }
}
