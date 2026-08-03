package com.splitease.app.data.expense

import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.remote.mapper.toDto
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.ActivityEvent
import com.splitease.app.domain.model.ActivityEventKind
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.recurrence.RecurrenceScheduler
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.split.SplitCalculator
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Input for creating an expense with splits.
 *
 * @property description Title.
 * @property amount Total amount.
 * @property currencyCode ISO currency.
 * @property paidByUserId Payer.
 * @property participantIds All participants (must include payer).
 * @property splitType Split mode.
 * @property groupId Optional group.
 * @property unequalAmounts For [SplitType.UNEQUAL].
 * @property percentages For [SplitType.PERCENTAGE].
 * @property shares For [SplitType.SHARES].
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
    val notes: String? = null,
    val categoryId: String? = null,
    val recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val expenseDateEpochMs: Long? = null,
    val recurringTemplateId: String? = null,
)

/**
 * Creates, updates, deletes, and syncs expenses (Room first, then PostgREST).
 */
@Singleton
class ExpenseInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val userRepository: UserRepository,
        private val categoryRepository: CategoryRepository,
        private val groupRepository: GroupRepository,
        private val activityEventRepository: ActivityEventRepository,
        private val remote: ExpenseRemoteDataSource,
        private val socialRemote: SocialRemoteDataSource,
        private val syncInteractor: Provider<SyncInteractor>,
    ) {
        /**
         * Creates an expense with calculated splits and best-effort cloud sync.
         *
         * @param input Creation payload.
         * @param actorUserId User who added the expense (activity feed); defaults to payer.
         * @return Persisted [Expense].
         */
        suspend fun createExpense(
            input: CreateExpenseInput,
            actorUserId: String? = null,
        ): Result<Expense> =
            runCatching {
                val built = buildExpenseAndSplits(input = input, existing = null)
                val synced = pushAndPersistSynced(expense = built.expense, splits = built.splits)
                val actor = actorUserId?.takeIf { it.isNotBlank() } ?: synced.paidByUserId
                recordExpenseActivity(
                    kind = ActivityEventKind.EXPENSE_ADDED,
                    expense = synced,
                    participantIds = built.splits.map { it.userId },
                    actorUserId = actor,
                )
                synced
            }

        /**
         * Updates an existing expense (stable id) and best-effort cloud sync.
         *
         * @param expenseId Existing expense id.
         * @param input Updated fields (same shape as create).
         * @param actorUserId User who performed the update (activity feed); defaults to payer.
         * @return Persisted [Expense].
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
                val built = buildExpenseAndSplits(input = input, existing = existing)
                val synced = pushAndPersistSynced(expense = built.expense, splits = built.splits)
                val actor = actorUserId?.takeIf { it.isNotBlank() } ?: synced.paidByUserId
                recordExpenseActivity(
                    kind = ActivityEventKind.EXPENSE_UPDATED,
                    expense = synced,
                    participantIds = built.splits.map { it.userId },
                    actorUserId = actor,
                )
                synced
            }

        /**
         * Deletes an expense locally and best-effort remotely; writes a deleted activity event.
         *
         * @param expenseId Expense id.
         * @param actorUserId User performing the delete (for the activity feed).
         */
        suspend fun deleteExpense(
            expenseId: String,
            actorUserId: String,
        ): Result<Unit> =
            runCatching {
                val existing =
                    expenseRepository.getExpenseById(expenseId)
                        ?: error("Expense not found.")
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
                expenseRepository.deleteExpenseById(expenseId)
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
                                    templateSplits.mapNotNull { split ->
                                        split.percentage?.let { split.userId to it }
                                    }.toMap()
                                } else {
                                    emptyMap()
                                },
                            shares =
                                if (template.splitType == SplitType.SHARES) {
                                    templateSplits.mapNotNull { split ->
                                        split.shares?.let { split.userId to it }
                                    }.toMap()
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
         * Pulls remote expenses for a group into Room.
         *
         * @param groupId Group id.
         */
        suspend fun refreshGroupExpenses(groupId: String) {
            remote.fetchByGroup(groupId).forEach { dto ->
                runCatching { persistRemoteExpense(dto) }
                    .onFailure { err ->
                        android.util.Log.w(
                            "ExpenseSync",
                            "Failed to persist remote expense ${dto.id}: ${dto.description}",
                            err,
                        )
                    }
            }
        }

        /**
         * Pulls expenses the signed-in user can see into Room.
         *
         * Includes:
         * - expenses where [userId] is payer or split participant
         * - all expenses in groups [userId] belongs to (needed after invite join —
         *   membership alone does not put the user on historical split rows)
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
            require(input.participantIds.contains(input.paidByUserId)) {
                "Payer must be included in participants."
            }

            val owed =
                SplitCalculator.calculate(
                    total = input.amount,
                    splitType = input.splitType,
                    participantIds = input.participantIds,
                    unequalAmounts = input.unequalAmounts,
                    percentages = input.percentages,
                    shares = input.shares,
                )

            (input.participantIds + input.paidByUserId).distinct().forEach { id ->
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
            val splits =
                owed.map { (userId, amount) ->
                    ExpenseSplit(
                        id = existingSplits[userId]?.id ?: UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        userId = userId,
                        owedAmount = amount,
                        percentage = input.percentages[userId],
                        shares = input.shares[userId],
                        syncStatus = SyncStatus.PENDING,
                    )
                }
            return BuiltExpense(expense, splits)
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
                    pushExpense(expense, splits)
                }.exceptionOrNull()

            if (pushError == null) {
                return persistSyncedExpense(expense, splits)
            }

            // Retry once after another social flush (group may have been PENDING / stale SYNCED).
            runCatching { syncInteractor.get().flushPending() }
            runCatching { ensureGroupOnCloud(expense.groupId) }
            val retryError =
                runCatching {
                    pushExpense(expense, splits)
                    persistSyncedExpense(expense, splits)
                }.exceptionOrNull()

            if (retryError == null) {
                return expenseRepository.getExpenseById(expense.id)
                    ?: error("Cloud save succeeded but local read failed.")
            }

            // Cloud push failed — persist locally so the expense is not lost.
            android.util.Log.w(
                "ExpenseSync",
                "Cloud save failed for expense: ${expense.description}; saving locally",
                retryError ?: pushError,
            )
            expenseRepository.upsertExpenseWithSplits(expense, splits)
            return expense
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
            val splits = remote.fetchSplits(dto.id)
            // Room FKs require local user rows for payer + participants (other members).
            ensureLocalUserExists(dto.paidByUserId)
            splits.forEach { ensureLocalUserExists(it.userId) }
            // Default category ids are device-local UUIDs (not synced). Drop unknown
            // category_id so a co-member's expense still lands in Room.
            val categoryId =
                dto.categoryId?.takeIf { id -> categoryRepository.getById(id) != null }
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
                    createdAtEpochMs = dto.updatedAtEpochMs,
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
                        syncStatus = SyncStatus.SYNCED,
                    )
                },
            )
        }

        private suspend fun pushExpense(expense: Expense, splits: List<ExpenseSplit>) {
            remote.upsertExpense(
                ExpenseDto(
                    id = expense.id,
                    description = expense.description,
                    amount = expense.amount.toPlainString(),
                    currencyCode = expense.currencyCode,
                    categoryId = expense.categoryId,
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
    }
