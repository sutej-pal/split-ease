package com.splitease.app.data.activity

import com.splitease.app.data.remote.ActivityRemoteDataSource
import com.splitease.app.data.remote.dto.ActivityEventDto
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ActivityEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flushes local activity events and hydrates the feed from Supabase.
 *
 * [ActivityEvent.isSeen] is device-local and is never sent to the cloud.
 */
@Singleton
class ActivityInteractor
    @Inject
    constructor(
        private val activityEventRepository: ActivityEventRepository,
        private val activityRemoteDataSource: ActivityRemoteDataSource,
    ) {
        /**
         * Pushes [SyncStatus.PENDING] events. Historical [SyncStatus.LOCAL_ONLY] rows stay local.
         *
         * @return Count of events marked [SyncStatus.SYNCED].
         */
        suspend fun flushPending(): Int {
            var syncedCount = 0
            activityEventRepository.getPendingSync().forEach { event ->
                runCatching {
                    activityRemoteDataSource.upsert(event.toRemoteDto())
                    activityEventRepository.upsert(
                        event.copy(
                            remoteId = event.remoteId ?: event.id,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                    syncedCount++
                }
            }
            return syncedCount
        }

        /**
         * Pulls recent cloud events for [userId] into Room.
         *
         * New remote rows start unseen. Existing local [isSeen] is preserved.
         * Unflushed local rows are not replaced.
         */
        suspend fun refreshForUser(userId: String) =
            withContext(Dispatchers.IO) {
                val dtos = activityRemoteDataSource.fetchRecentForUser(userId)
                dtos.forEach { dto ->
                    val existing = activityEventRepository.getById(dto.id)
                    if (existing != null && existing.syncStatus != SyncStatus.SYNCED) {
                        return@forEach
                    }
                    activityEventRepository.upsert(
                        ActivityEvent(
                            id = dto.id,
                            kind =
                                runCatching { ActivityEventKind.valueOf(dto.kind) }
                                    .getOrDefault(ActivityEventKind.EXPENSE_ADDED),
                            title = dto.title,
                            subtitle = dto.subtitle,
                            amountLabel = dto.amountLabel,
                            actorUserId = dto.actorUserId,
                            relatedExpenseId = dto.relatedExpenseId,
                            involvedUserIds = dto.involvedUserIds,
                            sortEpochMs = dto.sortEpochMs,
                            remoteId = dto.id,
                            syncStatus = SyncStatus.SYNCED,
                            isSeen = existing?.isSeen ?: false,
                        ),
                    )
                }
            }
    }

/**
 * Maps a local activity event for PostgREST upsert.
 *
 * [ActivityEventKind.EXPENSE_DELETED] omits [ActivityEvent.relatedExpenseId]: expenses are
 * hard-deleted before activity flush, so a leftover FK fails `related_expense_id → expenses.id`.
 */
internal fun ActivityEvent.toRemoteDto(): ActivityEventDto =
    ActivityEventDto(
        id = id,
        kind = kind.name,
        title = title,
        subtitle = subtitle,
        amountLabel = amountLabel,
        actorUserId = actorUserId,
        relatedExpenseId =
            relatedExpenseId.takeUnless { kind == ActivityEventKind.EXPENSE_DELETED },
        involvedUserIds = involvedUserIds,
        sortEpochMs = sortEpochMs,
    )
