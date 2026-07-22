package com.splitease.app.data.expense

import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.recurrence.RecurrenceScheduler
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.split.SplitCalculator
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
 * Creates and syncs expenses (Room first, then PostgREST).
 */
@Singleton
class ExpenseInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val userRepository: UserRepository,
        private val remote: ExpenseRemoteDataSource,
    ) {
        /**
         * Creates an expense with calculated splits and best-effort cloud sync.
         *
         * @param input Creation payload.
         * @return Persisted [Expense].
         */
        suspend fun createExpense(input: CreateExpenseInput): Result<Expense> =
            runCatching {
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

                // Room FKs require local user rows for payer + every split participant.
                (input.participantIds + input.paidByUserId).distinct().forEach { id ->
                    ensureLocalUserExists(id)
                }

                val now = System.currentTimeMillis()
                val expenseDate = input.expenseDateEpochMs ?: now
                val isRecurring =
                    input.recurrenceFrequency != RecurrenceFrequency.NONE &&
                        input.recurringTemplateId == null
                val nextOccurrence =
                    if (isRecurring) {
                        RecurrenceScheduler.nextOccurrenceAfter(expenseDate, input.recurrenceFrequency)
                    } else {
                        null
                    }
                val expenseId = UUID.randomUUID().toString()
                val expense =
                    Expense(
                        id = expenseId,
                        description = input.description.trim(),
                        amount = input.amount,
                        currencyCode = input.currencyCode.trim().ifBlank { "INR" }.uppercase(),
                        categoryId = input.categoryId,
                        paidByUserId = input.paidByUserId,
                        groupId = input.groupId,
                        expenseDateEpochMs = expenseDate,
                        splitType = input.splitType,
                        isRecurring = isRecurring,
                        recurrenceFrequency =
                            if (isRecurring) input.recurrenceFrequency else RecurrenceFrequency.NONE,
                        nextOccurrenceEpochMs = nextOccurrence,
                        recurringTemplateId = input.recurringTemplateId,
                        notes = input.notes?.trim()?.ifBlank { null },
                        remoteId = null,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )

                val splits =
                    owed.map { (userId, amount) ->
                        ExpenseSplit(
                            id = UUID.randomUUID().toString(),
                            expenseId = expenseId,
                            userId = userId,
                            owedAmount = amount,
                            percentage = input.percentages[userId],
                            shares = input.shares[userId],
                            syncStatus = SyncStatus.PENDING,
                        )
                    }

                expenseRepository.upsertExpenseWithSplits(expense, splits)

                val synced =
                    runCatching {
                        pushExpense(expense, splits)
                        expense.copy(remoteId = expense.id, syncStatus = SyncStatus.SYNCED)
                    }.getOrDefault(expense)

                if (synced.syncStatus == SyncStatus.SYNCED) {
                    expenseRepository.upsertExpenseWithSplits(
                        synced,
                        splits.map { it.copy(syncStatus = SyncStatus.SYNCED) },
                    )
                }
                synced
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
                persistRemoteExpense(dto)
            }
        }

        /**
         * Pulls expenses involving [userId] (payer or split participant) into Room.
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
                persistRemoteExpense(dto)
            }
        }

        private suspend fun persistRemoteExpense(dto: ExpenseDto) {
            val splits = remote.fetchSplits(dto.id)
            val expense =
                Expense(
                    id = dto.id,
                    description = dto.description,
                    amount = BigDecimal(dto.amount),
                    currencyCode = dto.currencyCode,
                    categoryId = dto.categoryId,
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
