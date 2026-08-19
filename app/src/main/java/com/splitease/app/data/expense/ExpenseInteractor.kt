package com.splitease.app.data.expense

import android.content.Context
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.media.LocalMediaCleanup
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.remote.ExpenseReceiptStorage
import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseCommentDto
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpensePhotoDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.remote.mapper.toDto
import com.splitease.app.data.sync.ExpensePushPolicy
import com.splitease.app.data.sync.REMOTE_FETCH_ROW_CAP
import com.splitease.app.data.sync.SyncConflictPolicy
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.data.sync.SyncWorker
import com.splitease.app.data.sync.isCompleteRemoteFetch
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseComment
import com.splitease.app.domain.model.ExpenseCommentKind
import com.splitease.app.domain.model.ExpensePhoto
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.recurrence.RecurrenceScheduler
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseCommentRepository
import com.splitease.app.domain.repository.ExpensePhotoRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.split.SplitCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Input for creating an expense with splits.
 *
 * @property description Title.
 * @property amount Total amount.
 * @property currencyCode ISO currency.
 * @property paidByUserId Primary payer (largest paid amount when multi-payer).
 * @property participantIds People who owe a share. The payer may be omitted when they owe nothing.
 * @property splitType Split mode.
 * @property groupId Optional group.
 * @property unequalAmounts For [SplitType.UNEQUAL].
 * @property percentages For [SplitType.PERCENTAGE].
 * @property shares For [SplitType.SHARES].
 * @property adjustments For [SplitType.ADJUSTMENT].
 * @property paidAmounts Optional multi-payer map (userId → paid). Empty = single payer.
 *   Payers need not be in [participantIds]; they receive a zero-owed split row.
 * @property notes Optional notes.
 * @property categoryId Optional category.
 * @property recurrenceFrequency Recurrence cadence; [RecurrenceFrequency.NONE] for one-off.
 * @property expenseDateEpochMs Optional business date (defaults to now).
 * @property recurringTemplateId When generating an instance, the parent template id.
 */
data class CreateExpenseInput(
    val description: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val paidByUserId: String,
    val participantIds: List<String>,
    val splitType: SplitType,
    val groupId: String? = null,
    val unequalAmounts: Map<String, BigDecimal> = emptyMap(),
    val percentages: Map<String, BigDecimal> = emptyMap(),
    val shares: Map<String, Int> = emptyMap(),
    val adjustments: Map<String, BigDecimal> = emptyMap(),
    val paidAmounts: Map<String, BigDecimal> = emptyMap(),
    val notes: String? = null,
    val categoryId: String? = null,
    val recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val expenseDateEpochMs: Long? = null,
    val recurringTemplateId: String? = null,
)

/** Outcome of a batch attachment save. */
data class AddAttachmentsResult(
    val addedCount: Int,
    val failedCount: Int,
)

/**
 * Creates, updates, deletes, and syncs expenses (Room first, then PostgREST).
 */
@Singleton
class ExpenseInteractor
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val expenseRepository: ExpenseRepository,
        private val expenseCommentRepository: ExpenseCommentRepository,
        private val expensePhotoRepository: ExpensePhotoRepository,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val groupRepository: GroupRepository,
        private val activityEventRepository: ActivityEventRepository,
        private val remote: ExpenseRemoteDataSource,
        private val receiptStorage: ExpenseReceiptStorage,
        private val mediaStorageCleanup: MediaStorageCleanup,
        private val socialRemote: SocialRemoteDataSource,
        private val syncInteractor: Provider<SyncInteractor>,
    ) {
        private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cloudPushMutex = Mutex()
        private val pushingExpenseIds = ConcurrentHashMap.newKeySet<String>()

        /**
         * Pushes one PENDING expense using the latest Room snapshot.
         *
         * Safe to call from [SyncInteractor.flushPending]: does not take [cloudPushMutex]
         * and does not call [SyncInteractor.flushPending], so it cannot deadlock with
         * [scheduleCloudPush]. No-ops if another push of the same id is already in flight.
         *
         * @return true when the local row is [SyncStatus.SYNCED] after this attempt.
         */
        suspend fun flushPendingExpense(expenseId: String): Boolean {
            if (!pushingExpenseIds.add(expenseId)) return false
            try {
                val expense = expenseRepository.getExpenseById(expenseId) ?: return false
                if (expense.syncStatus == SyncStatus.SYNCED) return true
                runCatching { ensureGroupOnCloud(expense.groupId) }
                val splits = expenseRepository.getSplits(expenseId)
                val after = pushLatestSnapshot(expense.id, expense, splits)
                return after.syncStatus == SyncStatus.SYNCED
            } finally {
                pushingExpenseIds.remove(expenseId)
            }
        }

        /**
         * Creates an expense locally (Room `PENDING`) and schedules a background cloud push.
         *
         * @param input Creation payload.
         * @param actorUserId User who added the expense (activity feed); defaults to payer.
         * @return Locally persisted [Expense] (does not wait for Supabase).
         */
        suspend fun createExpense(
            input: CreateExpenseInput,
            actorUserId: String? = null,
        ): Result<Expense> =
            runCatching {
                val built = buildExpenseAndSplits(input = input, existing = null)
                expenseRepository.upsertExpenseWithSplits(built.expense, built.splits)
                val actor = actorUserId?.takeIf { it.isNotBlank() } ?: built.expense.paidByUserId
                recordExpenseActivity(
                    kind = ActivityEventKind.EXPENSE_ADDED,
                    expense = built.expense,
                    participantIds = built.splits.map { it.userId },
                    actorUserId = actor,
                )
                scheduleCloudPush(built.expense.id)
                built.expense
            }

        /**
         * Creates an expense on a background scope so the call survives navigation pop.
         */
        fun enqueueCreateExpense(
            input: CreateExpenseInput,
            actorUserId: String? = null,
        ) {
            cloudScope.launch {
                runCatching { categoryRepository.ensureDefaults() }
                createExpense(input, actorUserId)
            }
        }

        /**
         * Updates an existing expense locally (Room `PENDING`) and schedules a background cloud push.
         *
         * @param expenseId Existing expense id.
         * @param input Updated fields (same shape as create).
         * @param actorUserId User who performed the update (activity feed); defaults to payer.
         * @return Locally persisted [Expense] (does not wait for Supabase).
         */
        suspend fun updateExpense(
            expenseId: String,
            input: CreateExpenseInput,
            actorUserId: String? = null,
        ): Result<Expense> =
            runCatching {
                val existing =
                    expenseRepository.getExpenseById(expenseId)
                        ?: error("Expense not found.")
                val existingSplits = expenseRepository.getSplits(expenseId)
                val built = buildExpenseAndSplits(input = input, existing = existing)
                expenseRepository.upsertExpenseWithSplits(built.expense, built.splits)
                val actor = actorUserId?.takeIf { it.isNotBlank() } ?: built.expense.paidByUserId
                recordExpenseActivity(
                    kind = ActivityEventKind.EXPENSE_UPDATED,
                    expense = built.expense,
                    participantIds = built.splits.map { it.userId },
                    actorUserId = actor,
                )
                recordExpenseUpdateComment(
                    before = existing,
                    beforeSplits = existingSplits,
                    after = built.expense,
                    afterSplits = built.splits,
                    actorUserId = actor,
                )
                scheduleCloudPush(built.expense.id)
                built.expense
            }

        /**
         * Adds a free-form user comment on an expense (best-effort cloud sync).
         */
        suspend fun addComment(
            expenseId: String,
            body: String,
            actorUserId: String,
        ): Result<ExpenseComment> =
            runCatching {
                val trimmed = body.trim()
                require(trimmed.isNotEmpty()) { "Comment cannot be empty." }
                expenseRepository.getExpenseById(expenseId) ?: error("Expense not found.")
                ensureLocalUserExists(actorUserId)
                val comment =
                    ExpenseComment(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        authorUserId = actorUserId,
                        body = trimmed,
                        kind = ExpenseCommentKind.USER,
                        createdAtEpochMs = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING,
                    )
                expenseCommentRepository.upsert(comment)
                pushCommentBestEffort(comment)
            }

        /**
         * Attaches one or more receipt images from gallery/camera URIs (no crop).
         * Writes a single SplitEase system comment for the batch.
         */
        suspend fun addExpenseAttachments(
            expenseId: String,
            photoUris: List<String>,
            actorUserId: String,
        ): Result<AddAttachmentsResult> =
            runCatching {
                if (photoUris.isEmpty()) return@runCatching AddAttachmentsResult(0, 0)
                expenseRepository.getExpenseById(expenseId) ?: error("Expense not found.")
                ensureLocalUserExists(actorUserId)
                val dir = File(appContext.filesDir, "expense_photos/$expenseId").apply { mkdirs() }
                var failedCount = 0
                val added =
                    photoUris.mapNotNull { sourceUri ->
                        val photoId = UUID.randomUUID().toString()
                        val dest = File(dir, "$photoId.jpg")
                        val copied =
                            runCatching {
                                AvatarImageIO.copyScaledJpeg(
                                    context = appContext,
                                    photoUri = sourceUri,
                                    destFile = dest,
                                    maxSidePx = AvatarImageIO.ATTACHMENT_STORED_MAX_SIDE_PX,
                                    quality = AvatarImageIO.ATTACHMENT_STORED_JPEG_QUALITY,
                                )
                            }.isSuccess
                        LocalMediaCleanup.deleteCachedCapture(appContext, sourceUri)
                        if (!copied) {
                            runCatching { dest.delete() }
                            failedCount++
                            return@mapNotNull null
                        }
                        val photo =
                            ExpensePhoto(
                                id = photoId,
                                expenseId = expenseId,
                                createdByUserId = actorUserId,
                                localPath = dest.absolutePath,
                                remoteUrl = null,
                                createdAtEpochMs = System.currentTimeMillis(),
                                syncStatus = SyncStatus.PENDING,
                            )
                        expensePhotoRepository.upsert(photo)
                        pushPhotoBestEffort(photo)
                        photo
                    }
                if (added.isEmpty()) error(appContext.getString(R.string.msg_photo_failed))
                val actorName = displayNameOf(actorUserId)
                val count = added.size
                val body =
                    if (count == 1) {
                        "This expense was updated by $actorName.\nAdded an attachment."
                    } else {
                        "This expense was updated by $actorName.\nAdded $count attachments."
                    }
                persistSystemComment(
                    expenseId = expenseId,
                    actorUserId = actorUserId,
                    body = body,
                )
                // Bump the parent expense so group Realtime / refresh pulls pick up new photos.
                touchExpenseForSideData(expenseId)
                AddAttachmentsResult(addedCount = count, failedCount = failedCount)
            }

        /**
         * Pulls comments and receipt photos for [expenseId] from Supabase into Room.
         * Safe to call when opening expense detail so other members' attachments appear.
         */
        suspend fun refreshExpenseSideData(expenseId: String) {
            val id = expenseId.trim()
            if (id.isEmpty()) return
            pullCommentsAndPhotos(id)
        }

        /** Pushes pending comments / photos that failed earlier (called from sync flush). */
        suspend fun flushPendingCommentsAndPhotos() {
            expenseCommentRepository.getPendingSync().forEach { comment ->
                runCatching { pushCommentBestEffort(comment) }
            }
            expensePhotoRepository.getPendingSync().forEach { photo ->
                runCatching { pushPhotoBestEffort(photo) }
            }
        }

        /**
         * Deletes an expense locally and best-effort remotely; writes a deleted activity event.
         *
         * @param expenseId Expense id.
         * @param actorUserId User performing the deletion (for the activity feed).
         */
        suspend fun deleteExpense(
            expenseId: String,
            actorUserId: String,
        ): Result<Unit> =
            runCatching {
                val existing =
                    expenseRepository.getExpenseById(expenseId)
                        ?: error("Expense not found.")
                val photos = expensePhotoRepository.getForExpense(expenseId)
                val splits = expenseRepository.getSplits(expenseId)
                recordExpenseActivity(
                    kind = ActivityEventKind.EXPENSE_DELETED,
                    expense = existing,
                    participantIds = splits.map { it.userId },
                    actorUserId = actorUserId.ifBlank { existing.paidByUserId },
                )
                runCatching {
                    remote.deleteSplitsForExpense(expenseId)
                    remote.deleteExpense(expenseId)
                }
                mediaStorageCleanup.purgeExpenseAttachments(expenseId, photos)
                expenseRepository.deleteExpenseById(expenseId)
            }

        /**
         * Deletes cloud + local attachment files for an expense without touching the DB row.
         * Used before bulk deletes (group / friend removal) where Room cascades later.
         */
        suspend fun purgeExpenseMedia(expenseId: String) {
            val localPhotos = expensePhotoRepository.getForExpense(expenseId)
            val photos =
                if (localPhotos.isNotEmpty()) {
                    localPhotos
                } else {
                    // Local metadata missing — still need Storage keys from PostgREST when possible.
                    runCatching {
                        remote.fetchPhotos(expenseId).map { dto ->
                            ExpensePhoto(
                                id = dto.id,
                                expenseId = dto.expenseId,
                                createdByUserId = dto.createdByUserId,
                                localPath = null,
                                remoteUrl = dto.remoteUrl,
                                createdAtEpochMs = dto.createdAtEpochMs,
                                syncStatus = SyncStatus.SYNCED,
                            )
                        }
                    }.getOrDefault(emptyList())
                }
            mediaStorageCleanup.purgeExpenseAttachments(expenseId, photos)
        }

        /**
         * Materializes due recurring templates into one-off expense instances and advances schedules.
         *
         * @param nowEpochMs Current time (injectable for tests).
         * @return Number of instances created.
         */
        suspend fun generateDueRecurringExpenses(nowEpochMs: Long = System.currentTimeMillis()): Int {
            val due = expenseRepository.getDueRecurringTemplates(nowEpochMs)
            var created = 0
            due.forEach { template ->
                val occurrenceAt = template.nextOccurrenceEpochMs ?: return@forEach
                val templateSplits = expenseRepository.getSplits(template.id)
                if (templateSplits.isEmpty()) return@forEach
                val result =
                    createExpense(
                        CreateExpenseInput(
                            description = template.description,
                            amount = template.amount,
                            currencyCode = template.currencyCode,
                            paidByUserId = template.paidByUserId,
                            participantIds = templateSplits.map { it.userId },
                            splitType = template.splitType,
                            groupId = template.groupId,
                            unequalAmounts =
                                if (template.splitType == SplitType.UNEQUAL) {
                                    templateSplits.associate { it.userId to it.owedAmount }
                                } else {
                                    emptyMap()
                                },
                            percentages =
                                if (template.splitType == SplitType.PERCENTAGE) {
                                    templateSplits
                                        .mapNotNull { split ->
                                        split.percentage?.let { split.userId to it }
                                    }.toMap()
                                } else {
                                    emptyMap()
                                },
                            shares =
                                if (template.splitType == SplitType.SHARES) {
                                    templateSplits
                                        .mapNotNull { split ->
                                        split.shares?.let { split.userId to it }
                                    }.toMap()
                                } else {
                                    emptyMap()
                                },
                            adjustments =
                                if (template.splitType == SplitType.ADJUSTMENT) {
                                    templateSplits.associate { split ->
                                        split.userId to
                                            (split.adjustmentAmount ?: BigDecimal.ZERO.setScale(2))
                                    }
                                } else {
                                    emptyMap()
                                },
                            notes = template.notes,
                            categoryId = template.categoryId,
                            recurrenceFrequency = RecurrenceFrequency.NONE,
                            expenseDateEpochMs = occurrenceAt,
                            recurringTemplateId = template.id,
                        ),
                    )
                if (result.isSuccess) {
                    created++
                    val advanced =
                        RecurrenceScheduler.catchUpNextOccurrence(
                            fromEpochMs = occurrenceAt,
                            frequency = template.recurrenceFrequency,
                            nowEpochMs = nowEpochMs,
                        )
                    expenseRepository.upsertExpenseWithSplits(
                        template.copy(
                            nextOccurrenceEpochMs = advanced,
                            updatedAtEpochMs = nowEpochMs,
                        ),
                        templateSplits,
                    )
                }
            }
            return created
        }

        /**
         * Pulls remote expenses for a group into Room, then removes local SYNCED rows
         * that are no longer present remotely (hard-deleted on another device).
         *
         * PENDING / LOCAL_ONLY rows are never pruned.
         *
         * @param groupId Group id.
         */
        suspend fun refreshGroupExpenses(groupId: String) {
            val remoteRows = remote.fetchByGroup(groupId)
            remoteRows.forEach { dto ->
                runCatching { persistRemoteExpense(dto) }
                    .onFailure { err ->
                        android.util.Log.w(
                            "ExpenseSync",
                            "Failed to persist remote expense ${dto.id}: ${dto.description}",
                            err,
                        )
                    }
            }
            pruneSyncedMissingRemote(
                localSyncedIds = expenseRepository.getSyncedIdsByGroup(groupId),
                remoteIds = remoteRows.map { it.id }.toSet(),
                remoteRowCount = remoteRows.size,
            )
        }

        /**
         * Pulls expenses the signed-in user can see into Room.
         *
         * Includes:
         * - expenses where [userId] is payer or split participant
         * - all expenses in groups [userId] belongs to (needed after invite join —
         *   membership alone does not put the user on historical split rows)
         *
         * After hydrate, prunes SYNCED 1:1 rows missing remotely; group rows are
         * pruned inside [refreshGroupExpenses].
         *
         * @param userId Current user id.
         */
        suspend fun refreshExpensesForUser(userId: String) {
            val ids =
                (
                    remote.fetchPaidBy(userId).map { it.id } +
                        remote.fetchSplitExpenseIdsForUser(userId)
                ).distinct()

            ids.forEach { expenseId ->
                val dto = remote.fetchExpense(expenseId) ?: return@forEach
                runCatching { persistRemoteExpense(dto) }
                    .onFailure { err ->
                        android.util.Log.w(
                            "ExpenseSync",
                            "Failed to persist remote expense $expenseId",
                            err,
                        )
                    }
            }

            // Group membership grants SELECT via RLS; pull those rows even when the
            // joiner is not (yet) on any split.
            groupRepository.observeGroupsForUser(userId).first().forEach { group ->
                refreshGroupExpenses(group.id)
            }

            // 1:1 (non-group) SYNCED rows: prune when absent from the involving-user set.
            pruneSyncedMissingRemote(
                localSyncedIds = expenseRepository.getSyncedNonGroupIdsInvolvingUser(userId),
                remoteIds = ids.toSet(),
                remoteRowCount = ids.size,
            )
        }

        /**
         * Deletes local SYNCED expenses that disappeared from a complete remote id set.
         * Also removes on-disk photo folders. Never touches PENDING / LOCAL_ONLY.
         */
        private suspend fun pruneSyncedMissingRemote(
            localSyncedIds: List<String>,
            remoteIds: Set<String>,
            remoteRowCount: Int,
        ) {
            if (!isCompleteRemoteFetch(remoteRowCount)) {
                android.util.Log.w(
                    "ExpenseSync",
                    "Skip remote-delete prune: fetch returned $remoteRowCount rows (cap=$REMOTE_FETCH_ROW_CAP)",
                )
                return
            }
            localSyncedIds.forEach { id ->
                if (id in remoteIds) return@forEach
                purgeExpenseMedia(id)
                expenseRepository.deleteExpenseById(id)
                android.util.Log.d("ExpenseSync", "Pruned remote-deleted expense $id")
            }
        }

        /**
         * Re-pushes local expenses that involve [userId] so remote splits/payer match Room
         * after an invite placeholder → real-user remap.
         *
         * @param userId Participant / payer user id (usually the newly joined friend).
         */
        suspend fun republishExpensesInvolving(userId: String) {
            if (userId.isBlank()) return
            val expenses = expenseRepository.observeInvolvingUser(userId).first()
            expenses.forEach { expense ->
                val splits = expenseRepository.getSplits(expense.id)
                val involves =
                    expense.paidByUserId == userId || splits.any { it.userId == userId }
                if (!involves) return@forEach
                runCatching {
                    pushAndPersistSynced(
                        expense = expense.copy(syncStatus = SyncStatus.PENDING),
                        splits = splits.map { it.copy(syncStatus = SyncStatus.PENDING) },
                    )
                }.onFailure { err ->
                    android.util.Log.w(
                        "ExpenseSync",
                        "Failed to republish expense ${expense.id} after user remap",
                        err,
                    )
                }
            }
        }

        private data class BuiltExpense(
            val expense: Expense,
            val splits: List<ExpenseSplit>,
        )

        private suspend fun buildExpenseAndSplits(
            input: CreateExpenseInput,
            existing: Expense?,
        ): BuiltExpense {
            require(input.description.isNotBlank()) { "Description is required." }
            require(input.paidByUserId.isNotBlank()) { "Payer is required." }
            if (input.paidAmounts.isNotEmpty()) {
                val paidSum =
                    input.paidAmounts.values
                        .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                require(paidSum.compareTo(input.amount.setScale(2, java.math.RoundingMode.HALF_UP)) == 0) {
                    "Paid amounts must add up to the expense total."
                }
            }

            val owed =
                SplitCalculator.calculate(
                    total = input.amount,
                    splitType = input.splitType,
                    participantIds = input.participantIds,
                    unequalAmounts = input.unequalAmounts,
                    percentages = input.percentages,
                    shares = input.shares,
                    adjustments = input.adjustments,
                )

            (input.participantIds + input.paidByUserId + input.paidAmounts.keys).distinct().forEach { id ->
                ensureLocalUserExists(id)
            }

            val now = System.currentTimeMillis()
            val expenseDate = input.expenseDateEpochMs ?: existing?.expenseDateEpochMs ?: now
            val isRecurring =
                if (existing != null) {
                    existing.isRecurring &&
                        input.recurrenceFrequency != RecurrenceFrequency.NONE &&
                        existing.recurringTemplateId == null
                } else {
                    input.recurrenceFrequency != RecurrenceFrequency.NONE &&
                        input.recurringTemplateId == null
                }
            val recurrenceFrequency =
                when {
                    existing != null && !isRecurring -> existing.recurrenceFrequency
                    isRecurring -> input.recurrenceFrequency
                    else -> RecurrenceFrequency.NONE
                }
            val nextOccurrence =
                if (isRecurring) {
                    RecurrenceScheduler.nextOccurrenceAfter(expenseDate, recurrenceFrequency)
                } else {
                    existing?.nextOccurrenceEpochMs.takeIf { existing?.isRecurring == true }
                }
            val expenseId = existing?.id ?: UUID.randomUUID().toString()
            val expense =
                Expense(
                    id = expenseId,
                    description = input.description.trim(),
                    amount = input.amount,
                    currencyCode = AppCurrencies.normalizeOrDefault(input.currencyCode),
                    categoryId = input.categoryId,
                    paidByUserId = input.paidByUserId,
                    groupId = input.groupId ?: existing?.groupId,
                    expenseDateEpochMs = expenseDate,
                    splitType = input.splitType,
                    isRecurring = isRecurring || (existing?.isRecurring == true && input.recurringTemplateId == null),
                    recurrenceFrequency = recurrenceFrequency,
                    nextOccurrenceEpochMs = nextOccurrence,
                    recurringTemplateId = input.recurringTemplateId ?: existing?.recurringTemplateId,
                    notes = input.notes?.trim()?.ifBlank { null },
                    remoteId = existing?.remoteId,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                )

            val existingSplits =
                if (existing != null) {
                    expenseRepository.getSplits(existing.id).associateBy { it.userId }
                } else {
                    emptyMap()
                }
            val isMultiPayer = input.paidAmounts.isNotEmpty()
            val zero = BigDecimal.ZERO.setScale(2)

            fun splitRow(
                userId: String,
                owedAmount: BigDecimal,
            ): ExpenseSplit =
                ExpenseSplit(
                    id = existingSplits[userId]?.id ?: UUID.randomUUID().toString(),
                    expenseId = expenseId,
                    userId = userId,
                    owedAmount = owedAmount,
                    percentage = input.percentages[userId],
                    shares = input.shares[userId],
                    paidAmount =
                        if (isMultiPayer) {
                            input.paidAmounts[userId] ?: zero
                        } else {
                            null
                        },
                    adjustmentAmount =
                        if (input.adjustments.isNotEmpty()) {
                            input.adjustments[userId] ?: zero
                        } else {
                            null
                        },
                    syncStatus = SyncStatus.PENDING,
                )
            val extraPayerSplits =
                if (isMultiPayer) {
                    input.paidAmounts.mapNotNull { (userId, paid) ->
                        if (userId in owed) return@mapNotNull null
                        val normalized = paid.setScale(2, java.math.RoundingMode.HALF_UP)
                        if (normalized <= zero) return@mapNotNull null
                        splitRow(userId, zero)
                    }
                } else {
                    emptyList()
                }
            val splits = owed.map { (userId, amount) -> splitRow(userId, amount) } + extraPayerSplits
            return BuiltExpense(expense, splits)
        }

        /**
         * Pushes [expenseId] to Supabase without blocking the UI.
         * Survives Add-expense screen teardown. [SyncWorker] is enqueued only if
         * the row is still PENDING after this attempt (process-death net).
         */
        private fun scheduleCloudPush(expenseId: String) {
            cloudScope.launch {
                if (pushingExpenseIds.add(expenseId)) {
                    try {
                        cloudPushMutex.withLock {
                            val expense = expenseRepository.getExpenseById(expenseId) ?: return@withLock
                            if (expense.syncStatus == SyncStatus.SYNCED) return@withLock
                            val splits = expenseRepository.getSplits(expenseId)
                            runCatching { pushAndPersistSynced(expense, splits) }
                        }
                    } finally {
                        pushingExpenseIds.remove(expenseId)
                    }
                }
                val stillPending =
                    expenseRepository.getExpenseById(expenseId)?.syncStatus == SyncStatus.PENDING
                if (stillPending) {
                    SyncWorker.enqueueFollowUp(appContext)
                }
            }
        }

        private suspend fun pushAndPersistSynced(
            expense: Expense,
            splits: List<ExpenseSplit>,
        ): Expense {
            // Ensure group/member rows exist remotely before expense FK upsert.
            runCatching { syncInteractor.get().flushPending() }
            runCatching { ensureGroupOnCloud(expense.groupId) }

            val pushError =
                runCatching {
                    pushLatestSnapshot(expense.id, expense, splits)
                }.exceptionOrNull()

            if (pushError == null) {
                return expenseRepository.getExpenseById(expense.id) ?: expense
            }

            // Retry once after another social flush (group may have been PENDING / stale SYNCED).
            runCatching { syncInteractor.get().flushPending() }
            runCatching { ensureGroupOnCloud(expense.groupId) }
            val retryError =
                runCatching {
                    pushLatestSnapshot(expense.id, expense, splits)
                }.exceptionOrNull()

            if (retryError == null) {
                return expenseRepository.getExpenseById(expense.id)
                    ?: error("Cloud save succeeded but local read failed.")
            }

            // Cloud push failed — keep the local PENDING row so the expense is not lost.
            android.util.Log.w(
                "ExpenseSync",
                "Cloud save failed for expense: ${expense.description}; keeping local PENDING",
                retryError,
            )
            return expenseRepository.getExpenseById(expense.id) ?: expense
        }

        /**
         * Upserts the latest Room snapshot. If the user edits during the network call,
         * pushes again (up to [PUSH_LATEST_ATTEMPTS]) instead of marking a stale row SYNCED.
         */
        private suspend fun pushLatestSnapshot(
            expenseId: String,
            fallbackExpense: Expense,
            fallbackSplits: List<ExpenseSplit>,
        ): Expense {
            var snapshot = latestLocalExpense(expenseId, fallbackExpense, fallbackSplits)
            repeat(PUSH_LATEST_ATTEMPTS) {
                if (snapshot.expense.syncStatus == SyncStatus.SYNCED) {
                    return snapshot.expense
                }
                pushExpense(snapshot.expense, snapshot.splits)
                val latest = latestLocalExpense(expenseId, snapshot.expense, snapshot.splits)
                if (latest.expense.updatedAtEpochMs > snapshot.expense.updatedAtEpochMs) {
                    snapshot = latest
                } else {
                    return persistSyncedIfUnchanged(snapshot.expense, snapshot.splits)
                }
            }
            return persistSyncedIfUnchanged(snapshot.expense, snapshot.splits)
        }

        private suspend fun latestLocalExpense(
            expenseId: String,
            fallbackExpense: Expense,
            fallbackSplits: List<ExpenseSplit>,
        ): BuiltExpense {
            val current = expenseRepository.getExpenseById(expenseId) ?: fallbackExpense
            val currentSplits = expenseRepository.getSplits(expenseId)
            return BuiltExpense(
                expense = current,
                splits = currentSplits.ifEmpty { fallbackSplits },
            )
        }

        /**
         * Marks SYNCED only when Room still has the snapshot we just pushed.
         * A newer local edit stays PENDING for the next flush.
         */
        private suspend fun persistSyncedIfUnchanged(
            pushed: Expense,
            splits: List<ExpenseSplit>,
        ): Expense {
            val current = expenseRepository.getExpenseById(pushed.id)
            if (!ExpensePushPolicy.shouldMarkSynced(current?.updatedAtEpochMs, pushed.updatedAtEpochMs)) {
                return current ?: pushed
            }
            return persistSyncedExpense(pushed, splits)
        }

        /**
         * Re-upserts the group (and owner membership) even when local status is SYNCED.
         * Local SYNCED can be stale if a prior cloud write was rolled back.
         */
        private suspend fun ensureGroupOnCloud(groupId: String?) {
            if (groupId.isNullOrBlank()) return
            val group = groupRepository.getGroupById(groupId) ?: return
            val now = System.currentTimeMillis()
            socialRemote.upsertGroup(group.toDto(updatedAtEpochMs = now))
            groupRepository.upsertGroup(
                group.copy(
                    remoteId = group.id,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAtEpochMs = now,
                ),
            )
            val owner = groupRepository.getMember(groupId, group.createdByUserId)
            if (owner != null) {
                socialRemote.upsertGroupMember(owner.toDto())
                groupRepository.upsertMember(owner.copy(syncStatus = SyncStatus.SYNCED))
            }
        }

        private suspend fun persistSyncedExpense(
            expense: Expense,
            splits: List<ExpenseSplit>,
        ): Expense {
            val synced = expense.copy(remoteId = expense.id, syncStatus = SyncStatus.SYNCED)
            expenseRepository.upsertExpenseWithSplits(
                synced,
                splits.map { it.copy(syncStatus = SyncStatus.SYNCED) },
            )
            return synced
        }

        private suspend fun recordExpenseActivity(
            kind: ActivityEventKind,
            expense: Expense,
            participantIds: List<String>,
            actorUserId: String,
        ) {
            val groupName =
                expense.groupId?.let { id -> groupRepository.getGroupById(id)?.name }
            val context = groupName ?: "Non-group"
            val date =
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expense.expenseDateEpochMs))
            val titlePrefix =
                when (kind) {
                    ActivityEventKind.EXPENSE_ADDED -> ""
                    ActivityEventKind.EXPENSE_UPDATED -> "Updated: "
                    ActivityEventKind.EXPENSE_DELETED -> "Deleted: "
                }
            val involved =
                (participantIds + expense.paidByUserId + actorUserId)
                    .distinct()
                    .sorted()
                    .joinToString(prefix = ",", postfix = ",", separator = ",")
            activityEventRepository.upsert(
                ActivityEvent(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    title = "$titlePrefix${expense.description}",
                    subtitle = "$context · $date",
                    amountLabel = "${expense.currencyCode} ${expense.amount.toPlainString()}",
                    actorUserId = actorUserId,
                    relatedExpenseId = expense.id,
                    involvedUserIds = involved,
                    sortEpochMs = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun persistRemoteExpense(dto: ExpenseDto) {
            val existing = expenseRepository.getExpenseById(dto.id)
            val shouldApply =
                SyncConflictPolicy.shouldApplyRemote(
                    localUpdatedAtEpochMs = existing?.updatedAtEpochMs,
                    localSyncStatus = existing?.syncStatus,
                    remoteUpdatedAtEpochMs = dto.updatedAtEpochMs,
                )
            if (!shouldApply) {
                android.util.Log.d(
                    "ExpenseSync",
                    "Skip remote expense ${dto.id}: local ${existing?.syncStatus} " +
                        "updatedAt=${existing?.updatedAtEpochMs} >= remote ${dto.updatedAtEpochMs}",
                )
                // Attachments/comments are child tables — still pull them even when the
                // expense row itself is not re-applied (LWW skip / local PENDING edit).
                pullCommentsAndPhotos(dto.id)
                return
            }
            val splits = remote.fetchSplits(dto.id)
            // Room FKs require local user rows for payer + participants (other members).
            ensureLocalUserExists(dto.paidByUserId)
            splits.forEach { ensureLocalUserExists(it.userId) }
            // Default category ids are device-local UUIDs on older installs; stable `cat_*`
            // ids are auto-seeded on pull. Custom categories remain local-only.
            val categoryId = categoryRepository.resolveCategoryForRemotePull(dto.categoryId)
            // Cloud expenses have no created_at column; never clobber local creation
            // time with updated_at (that would move the expense after every edit sync).
            val createdAt =
                existing?.createdAtEpochMs
                    ?: dto.expenseDateEpochMs.takeIf { it > 0L }
                    ?: dto.updatedAtEpochMs
            val expense =
                Expense(
                    id = dto.id,
                    description = dto.description,
                    amount = BigDecimal(dto.amount),
                    currencyCode = dto.currencyCode,
                    categoryId = categoryId,
                    paidByUserId = dto.paidByUserId,
                    groupId = dto.groupId,
                    expenseDateEpochMs = dto.expenseDateEpochMs,
                    splitType = runCatching { SplitType.valueOf(dto.splitType) }.getOrDefault(SplitType.EQUAL),
                    notes = dto.notes,
                    remoteId = dto.id,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = dto.updatedAtEpochMs,
                    syncStatus = SyncStatus.SYNCED,
                )
            expenseRepository.upsertExpenseWithSplits(
                expense,
                splits.map { split ->
                    ExpenseSplit(
                        id = split.id,
                        expenseId = split.expenseId,
                        userId = split.userId,
                        owedAmount = BigDecimal(split.owedAmount),
                        percentage = split.percentage?.let { BigDecimal(it) },
                        shares = split.shares,
                        paidAmount = split.paidAmount?.let { BigDecimal(it) },
                        adjustmentAmount = split.adjustmentAmount?.let { BigDecimal(it) },
                        syncStatus = SyncStatus.SYNCED,
                    )
                },
            )
            pullCommentsAndPhotos(dto.id)
        }

        /**
         * Marks the parent expense as updated so group Realtime listeners refresh and
         * other members pull new comments/photos.
         *
         * Upserts the expense row only — does not rewrite splits (that would clobber
         * newer remote edits made on another device).
         */
        private suspend fun touchExpenseForSideData(expenseId: String) {
            val expense = expenseRepository.getExpenseById(expenseId) ?: return
            val splits = expenseRepository.getSplits(expenseId)
            val now = System.currentTimeMillis()
            val bumped =
                expense.copy(
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.PENDING,
                )
            expenseRepository.upsertExpenseWithSplits(bumped, splits)
            runCatching {
                remote.upsertExpense(
                    ExpenseDto(
                        id = bumped.id,
                        description = bumped.description,
                        amount = bumped.amount.toPlainString(),
                        currencyCode = bumped.currencyCode,
                        categoryId = categoryRepository.categoryIdForCloud(bumped.categoryId),
                        paidByUserId = bumped.paidByUserId,
                        groupId = bumped.groupId,
                        expenseDateEpochMs = bumped.expenseDateEpochMs,
                        splitType = bumped.splitType.name,
                        notes = bumped.notes,
                        updatedAtEpochMs = bumped.updatedAtEpochMs,
                    ),
                )
                expenseRepository.upsertExpenseWithSplits(
                    bumped.copy(syncStatus = SyncStatus.SYNCED, remoteId = bumped.id),
                    splits,
                )
            }
        }

        private suspend fun pullCommentsAndPhotos(expenseId: String) {
            runCatching {
                val remoteComments = remote.fetchComments(expenseId)
                if (remoteComments.isNotEmpty()) {
                    expenseCommentRepository.upsertAll(
                        remoteComments.map { dto ->
                            ensureLocalUserExists(dto.authorUserId)
                            ExpenseComment(
                                id = dto.id,
                                expenseId = dto.expenseId,
                                authorUserId = dto.authorUserId,
                                body = dto.body,
                                kind =
                                    runCatching { ExpenseCommentKind.valueOf(dto.kind) }
                                        .getOrDefault(ExpenseCommentKind.USER),
                                createdAtEpochMs = dto.createdAtEpochMs,
                                syncStatus = SyncStatus.SYNCED,
                            )
                        },
                    )
                }
            }
            runCatching {
                val remotePhotos = remote.fetchPhotos(expenseId)
                if (remotePhotos.isEmpty()) return@runCatching
                val localById =
                    expensePhotoRepository.getForExpense(expenseId).associateBy { it.id }
                expensePhotoRepository.upsertAll(
                    remotePhotos.map { dto ->
                        ensureLocalUserExists(dto.createdByUserId)
                        val existing = localById[dto.id]
                        val existingLocal =
                            existing?.localPath?.trim()?.takeIf { path ->
                                path.isNotEmpty() && File(path).isFile
                            }
                        ExpensePhoto(
                            id = dto.id,
                            expenseId = dto.expenseId,
                            createdByUserId = dto.createdByUserId,
                            localPath = existingLocal,
                            remoteUrl = dto.remoteUrl ?: existing?.remoteUrl,
                            createdAtEpochMs = dto.createdAtEpochMs,
                            syncStatus = SyncStatus.SYNCED,
                        )
                    },
                )
            }
        }

        private suspend fun recordExpenseUpdateComment(
            before: Expense,
            beforeSplits: List<ExpenseSplit>,
            after: Expense,
            afterSplits: List<ExpenseSplit>,
            actorUserId: String,
        ) {
            val changes = describeExpenseChanges(before, beforeSplits, after, afterSplits)
            if (changes.isEmpty()) return
            val actorName = displayNameOf(actorUserId)
            val body =
                buildString {
                    append("This expense was updated by ")
                    append(actorName)
                    append('.')
                    changes.forEach { change ->
                        append('\n')
                        append(change)
                    }
                }
            persistSystemComment(
                expenseId = after.id,
                actorUserId = actorUserId,
                body = body,
            )
        }

        private fun describeExpenseChanges(
            before: Expense,
            beforeSplits: List<ExpenseSplit>,
            after: Expense,
            afterSplits: List<ExpenseSplit>,
        ): List<String> {
            val changes = mutableListOf<String>()
            if (before.description != after.description) {
                changes += "Description: \"${before.description}\" → \"${after.description}\""
            }
            if (before.amount.compareTo(after.amount) != 0) {
                changes +=
                    "Amount: ${before.currencyCode} ${before.amount.toPlainString()} → " +
                        "${after.currencyCode} ${after.amount.toPlainString()}"
            }
            if (before.paidByUserId != after.paidByUserId) {
                changes += "Paid by changed"
            }
            if (before.splitType != after.splitType) {
                changes += "Split type: ${before.splitType.name} → ${after.splitType.name}"
            }
            if ((before.notes.orEmpty()) != (after.notes.orEmpty())) {
                changes += "Notes updated"
            }
            if (before.expenseDateEpochMs != after.expenseDateEpochMs) {
                changes += "Date updated"
            }
            if (before.categoryId != after.categoryId) {
                changes += "Category updated"
            }
            val beforeOwed =
                beforeSplits.associate { it.userId to it.owedAmount.stripTrailingZeros().toPlainString() }
            val afterOwed =
                afterSplits.associate { it.userId to it.owedAmount.stripTrailingZeros().toPlainString() }
            val beforePaid =
                beforeSplits.associate {
                    it.userId to (it.paidAmount?.stripTrailingZeros()?.toPlainString() ?: "")
                }
            val afterPaid =
                afterSplits.associate {
                    it.userId to (it.paidAmount?.stripTrailingZeros()?.toPlainString() ?: "")
                }
            if (beforeOwed != afterOwed ||
                beforePaid != afterPaid ||
                beforeSplits.map { it.userId }.toSet() != afterSplits.map { it.userId }.toSet()
            ) {
                changes += "Split details updated"
            }
            return changes
        }

        private suspend fun persistSystemComment(
            expenseId: String,
            actorUserId: String,
            body: String,
        ) {
            val comment =
                ExpenseComment(
                    id = UUID.randomUUID().toString(),
                    expenseId = expenseId,
                    authorUserId = actorUserId,
                    body = body,
                    kind = ExpenseCommentKind.SYSTEM,
                    createdAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING,
                )
            expenseCommentRepository.upsert(comment)
            runCatching { pushCommentBestEffort(comment) }
        }

        private suspend fun pushCommentBestEffort(comment: ExpenseComment): ExpenseComment {
            val pushed =
                runCatching {
                    remote.upsertComment(
                        ExpenseCommentDto(
                            id = comment.id,
                            expenseId = comment.expenseId,
                            authorUserId = comment.authorUserId,
                            body = comment.body,
                            kind = comment.kind.name,
                            createdAtEpochMs = comment.createdAtEpochMs,
                        ),
                    )
                    comment.copy(syncStatus = SyncStatus.SYNCED)
                }.getOrElse {
                    comment.copy(syncStatus = SyncStatus.PENDING)
                }
            expenseCommentRepository.upsert(pushed)
            return pushed
        }

        private suspend fun pushPhotoBestEffort(photo: ExpensePhoto): ExpensePhoto {
            val localPath = photo.localPath
            val remoteUrl =
                when {
                    !photo.remoteUrl.isNullOrBlank() -> photo.remoteUrl
                    !localPath.isNullOrBlank() ->
                        runCatching {
                            receiptStorage.uploadReceipt(
                                expenseId = photo.expenseId,
                                photoId = photo.id,
                                localJpegPath = localPath,
                            )
                        }.getOrNull()
                    else -> null
                }
            val withUrl = photo.copy(remoteUrl = remoteUrl ?: photo.remoteUrl)
            withUrl.remoteUrl?.let { url ->
                localPath?.let { path ->
                    runCatching {
                        AvatarImageIO.seedRemoteImageCache(appContext, url, File(path))
                    }
                }
            }
            // Without a Storage URL the row is not fully pushed; keep PENDING so flush retries
            // the upload instead of treating a metadata-only upsert as done.
            if (withUrl.remoteUrl.isNullOrBlank()) {
                val pending = withUrl.copy(syncStatus = SyncStatus.PENDING)
                expensePhotoRepository.upsert(pending)
                return pending
            }
            val pushed =
                runCatching {
                    remote.upsertPhoto(
                        ExpensePhotoDto(
                            id = withUrl.id,
                            expenseId = withUrl.expenseId,
                            createdByUserId = withUrl.createdByUserId,
                            remoteUrl = withUrl.remoteUrl,
                            createdAtEpochMs = withUrl.createdAtEpochMs,
                        ),
                    )
                    withUrl.copy(syncStatus = SyncStatus.SYNCED)
                }.getOrElse {
                    withUrl.copy(syncStatus = SyncStatus.PENDING)
                }
            expensePhotoRepository.upsert(pushed)
            return pushed
        }

        private suspend fun displayNameOf(userId: String): String {
            val user = userRepository.getUserById(userId)
            val name = user?.displayName?.trim().orEmpty()
            return name.ifBlank { "Someone" }
        }

        private suspend fun pushExpense(expense: Expense, splits: List<ExpenseSplit>) {
            remote.upsertExpense(
                ExpenseDto(
                    id = expense.id,
                    description = expense.description,
                    amount = expense.amount.toPlainString(),
                    currencyCode = expense.currencyCode,
                    categoryId = categoryRepository.categoryIdForCloud(expense.categoryId),
                    paidByUserId = expense.paidByUserId,
                    groupId = expense.groupId,
                    expenseDateEpochMs = expense.expenseDateEpochMs,
                    splitType = expense.splitType.name,
                    notes = expense.notes,
                    updatedAtEpochMs = expense.updatedAtEpochMs,
                ),
            )
            remote.deleteSplitsForExpense(expense.id)
            remote.upsertSplits(
                splits.map { split ->
                    ExpenseSplitDto(
                        id = split.id,
                        expenseId = split.expenseId,
                        userId = split.userId,
                        owedAmount = split.owedAmount.toPlainString(),
                        percentage = split.percentage?.toPlainString(),
                        shares = split.shares,
                        paidAmount = split.paidAmount?.toPlainString(),
                        adjustmentAmount = split.adjustmentAmount?.toPlainString(),
                    )
                },
            )
        }

        private suspend fun ensureLocalUserExists(userId: String) {
            if (userId.isBlank() || userRepository.getUserById(userId) != null) return
            val now = System.currentTimeMillis()
            userRepository.upsert(
                User(
                    id = userId,
                    email = "",
                    displayName = userId.take(8),
                    photoUrl = null,
                    remoteId = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                ),
            )
        }

        private companion object {
            const val PUSH_LATEST_ATTEMPTS = 3
        }
    }
