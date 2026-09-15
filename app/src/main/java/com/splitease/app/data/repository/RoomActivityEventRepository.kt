package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.ActivityEventDao
import com.splitease.app.data.local.entity.ActivityEventEntity
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ActivityEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [ActivityEventRepository].
 */
@Singleton
class RoomActivityEventRepository
    @Inject
    constructor(
        private val activityEventDao: ActivityEventDao,
    ) : ActivityEventRepository {
        override suspend fun upsert(event: ActivityEvent) {
            activityEventDao.upsert(event.toEntity())
        }

        override fun observeForUser(userId: String): Flow<List<ActivityEvent>> =
            activityEventDao
                .observeForUser(userId = userId, userIdToken = ",$userId,")
                .map { rows -> rows.map { it.toDomain() } }

        override fun observeRecentForUser(
            userId: String,
            limit: Int,
        ): Flow<List<ActivityEvent>> =
            activityEventDao
                .observeRecentForUser(
                    userId = userId,
                    userIdToken = ",$userId,",
                    limit = limit,
                ).map { rows -> rows.map { it.toDomain() } }

        override suspend fun markAllAsSeen(userId: String) {
            activityEventDao.markAllAsSeen(userId, ",$userId,")
        }

        override fun observeUnseenCount(userId: String): Flow<Int> =
            activityEventDao.observeUnseenCount(userId, ",$userId,")

        override suspend fun getPendingSync(): List<ActivityEvent> =
            activityEventDao.getPendingSync().map { it.toDomain() }

        override suspend fun getById(id: String): ActivityEvent? =
            activityEventDao.getById(id)?.toDomain()

        private fun ActivityEvent.toEntity(): ActivityEventEntity =
            ActivityEventEntity(
                id = id,
                kind = kind.name,
                title = title,
                subtitle = subtitle,
                amountLabel = amountLabel,
                actorUserId = actorUserId,
                relatedExpenseId = relatedExpenseId,
                involvedUserIds = involvedUserIds,
                sortEpochMs = sortEpochMs,
                remoteId = remoteId,
                syncStatus = syncStatus.name,
                isSeen = isSeen,
            )

        private fun ActivityEventEntity.toDomain(): ActivityEvent =
            ActivityEvent(
                id = id,
                kind =
                    runCatching { ActivityEventKind.valueOf(kind) }
                        .getOrDefault(ActivityEventKind.EXPENSE_ADDED),
                title = title,
                subtitle = subtitle,
                amountLabel = amountLabel,
                actorUserId = actorUserId,
                relatedExpenseId = relatedExpenseId,
                involvedUserIds = involvedUserIds,
                sortEpochMs = sortEpochMs,
                remoteId = remoteId,
                syncStatus =
                    runCatching { SyncStatus.valueOf(syncStatus) }
                        .getOrDefault(SyncStatus.LOCAL_ONLY),
                isSeen = isSeen,
            )
    }
