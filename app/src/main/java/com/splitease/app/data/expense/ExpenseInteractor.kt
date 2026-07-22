package com.splitease.app.data.expense

import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ExpenseRepository
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
)

/**
 * Creates and syncs expenses (Room first, then PostgREST).
 */
@Singleton
class ExpenseInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
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

                val now = System.currentTimeMillis()
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
                        expenseDateEpochMs = now,
                        splitType = input.splitType,
                        isRecurring = false,
                        recurrenceFrequency = RecurrenceFrequency.NONE,
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
    }
