package com.splitease.app.data.activity

/**
 * One cloud activity row considered for a local group-update notification.
 *
 * @property id Event id.
 * @property actorUserId User who caused the event.
 * @property sortEpochMs Feed sort time.
 * @property groupId Group to open on tap, or null when the event is not a group change.
 * @property title Notification title (usually the group name).
 * @property body Notification body.
 */
internal data class ActivityNotice(
    val id: String,
    val actorUserId: String,
    val sortEpochMs: Long,
    val groupId: String?,
    val title: String,
    val body: String,
)

/**
 * Result of [planActivityNotifications].
 *
 * @property watermarkEpochMs Cursor to persist. `0` means the device has not primed yet.
 * @property notifications At most one notice per group for this pull.
 */
internal data class ActivityNotifyPlan(
    val watermarkEpochMs: Long,
    val notifications: List<ActivityNotice>,
)

/**
 * Decides which newly pulled activity rows should raise a tray notification.
 *
 * The first successful pull only primes [watermarkEpochMs] so historical rows
 * do not spam the tray. Later pulls notify other people's new group events.
 * The signed-in user's own rows are skipped.
 *
 * @param incoming Rows applied from this pull.
 * @param existingIds Ids that were already in Room before this pull.
 * @param currentUserId Signed-in user. Own events are not notified.
 * @param watermarkEpochMs Stored cursor. `0` when this install/user has never pulled.
 * @param nowEpochMs Clock used to prime the cursor.
 */
internal fun planActivityNotifications(
    incoming: List<ActivityNotice>,
    existingIds: Set<String>,
    currentUserId: String,
    watermarkEpochMs: Long,
    nowEpochMs: Long,
): ActivityNotifyPlan {
    if (watermarkEpochMs <= 0L) {
        return ActivityNotifyPlan(
            watermarkEpochMs = nowEpochMs.coerceAtLeast(1L),
            notifications = emptyList(),
        )
    }
    val fresh =
        incoming.filter { notice ->
            notice.id !in existingIds &&
                notice.actorUserId != currentUserId &&
                !notice.groupId.isNullOrBlank()
        }
    val perGroup =
        fresh
            .groupBy { it.groupId }
            .map { (_, rows) -> rows.maxBy { it.sortEpochMs } }
    return ActivityNotifyPlan(
        watermarkEpochMs = watermarkEpochMs,
        notifications = perGroup,
    )
}
