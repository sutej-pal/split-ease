package com.splitease.app.data.activity

import com.splitease.app.data.remote.ActivityRemoteDataSource
import com.splitease.app.data.remote.dto.ActivityEventDto
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.settings.AppCurrencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
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
        private val expenseRepository: ExpenseRepository,
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
                    val relatedExists =
                        event.relatedExpenseId
                            ?.let { expenseRepository.getExpenseById(it) } != null
                    activityRemoteDataSource.upsert(event.toRemoteDto(relatedExpenseExists = relatedExists))
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
         * Unflushed local rows are not replaced. Snapshot fields and
         * [ActivityEvent.relatedExpenseId] fall back to the local row when the
         * cloud payload omits them (delete events strip the expense FK).
         */
        suspend fun refreshForUser(userId: String) =
            withContext(Dispatchers.IO) {
                val dtos = activityRemoteDataSource.fetchRecentForUser(userId)
                dtos.forEach { dto ->
                    val existing = activityEventRepository.getById(dto.id)
                    if (existing != null && existing.syncStatus != SyncStatus.SYNCED) {
                        return@forEach
                    }
                    activityEventRepository.upsert(dto.toDomain(existing))
                }
            }
    }

/**
 * Maps a local activity event for PostgREST upsert.
 *
 * [ActivityEventKind.EXPENSE_DELETED] omits [ActivityEvent.relatedExpenseId] unless
 * the linked expense still exists (after restore). Hard-deleted parents fail the
 * `related_expense_id → expenses.id` FK.
 */
internal fun ActivityEvent.toRemoteDto(relatedExpenseExists: Boolean = false): ActivityEventDto =
    ActivityEventDto(
        id = id,
        kind = kind.name,
        title = title,
        subtitle = subtitle,
        amountLabel = amountLabel,
        actorUserId = actorUserId,
        relatedExpenseId =
            relatedExpenseId.takeUnless {
                kind == ActivityEventKind.EXPENSE_DELETED && !relatedExpenseExists
            },
        involvedUserIds = involvedUserIds,
        sortEpochMs = sortEpochMs,
        snapshotDescription = snapshotDescription,
        snapshotAmount = snapshotAmount,
        snapshotCurrency = snapshotCurrency,
        snapshotGroupId = snapshotGroupId,
        snapshotGroupName = snapshotGroupName,
        snapshotCreatorUserId = snapshotCreatorUserId,
        snapshotCreatedAtEpochMs = snapshotCreatedAtEpochMs,
        snapshotParticipantUserIds = snapshotParticipantUserIds,
    )

/** Maps a cloud row, filling snapshot / related-id gaps from [existing]. */
internal fun ActivityEventDto.toDomain(existing: ActivityEvent?): ActivityEvent =
    ActivityEvent(
        id = id,
        kind =
            runCatching { ActivityEventKind.valueOf(kind) }
                .getOrDefault(ActivityEventKind.EXPENSE_ADDED),
        title = title,
        subtitle = subtitle,
        amountLabel = amountLabel,
        actorUserId = actorUserId,
        relatedExpenseId = relatedExpenseId ?: existing?.relatedExpenseId,
        involvedUserIds = involvedUserIds,
        sortEpochMs = sortEpochMs,
        remoteId = id,
        syncStatus = SyncStatus.SYNCED,
        isSeen = existing?.isSeen ?: false,
        snapshotDescription = snapshotDescription ?: existing?.snapshotDescription,
        snapshotAmount = snapshotAmount ?: existing?.snapshotAmount,
        snapshotCurrency = snapshotCurrency ?: existing?.snapshotCurrency,
        snapshotGroupId = snapshotGroupId ?: existing?.snapshotGroupId,
        snapshotGroupName = snapshotGroupName ?: existing?.snapshotGroupName,
        snapshotCreatorUserId = snapshotCreatorUserId ?: existing?.snapshotCreatorUserId,
        snapshotCreatedAtEpochMs = snapshotCreatedAtEpochMs ?: existing?.snapshotCreatedAtEpochMs,
        snapshotParticipantUserIds = snapshotParticipantUserIds ?: existing?.snapshotParticipantUserIds,
    )

/** Amount from snapshot, then `amountLabel` (`INR 12.00`). */
internal fun ActivityEvent.restoreAmount(): BigDecimal? =
    snapshotAmount?.let { runCatching { BigDecimal(it) }.getOrNull() }
        ?: amountLabel.trim().split(Regex("\\s+")).lastOrNull()?.let {
            runCatching { BigDecimal(it) }.getOrNull()
        }

/** Currency from snapshot, then `amountLabel`, then app default. */
internal fun ActivityEvent.restoreCurrency(): String =
    snapshotCurrency?.takeIf { it.isNotBlank() }
        ?: amountLabel.trim().split(Regex("\\s+")).firstOrNull()?.takeIf {
            it.any { ch -> ch.isLetter() }
        }
        ?: AppCurrencies.DEFAULT
