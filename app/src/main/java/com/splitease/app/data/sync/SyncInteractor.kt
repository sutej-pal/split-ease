package com.splitease.app.data.sync

import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.PaymentRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a pending flush.
 *
 * @property expensesSynced Count of expenses pushed.
 * @property paymentsSynced Count of payments pushed.
 * @property failures Error messages for failed rows.
 */
data class SyncFlushResult(
    val expensesSynced: Int = 0,
    val paymentsSynced: Int = 0,
    val failures: List<String> = emptyList(),
)

/**
 * Durable offline write queue: flushes PENDING / LOCAL_ONLY expenses and payments.
 */
@Singleton
class SyncInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val expenseRemote: ExpenseRemoteDataSource,
        private val paymentRemote: PaymentRemoteDataSource,
    ) {
        /**
         * Counts pending expenses + payments once.
         */
        suspend fun pendingCount(): Int =
            expenseRepository.getPendingSync().size + paymentRepository.getPendingSync().size

        /**
         * Pushes all pending expenses (with splits) and payments to Supabase.
         *
         * Failed rows stay PENDING for the next retry.
         *
         * @return Flush summary.
         */
        suspend fun flushPending(): SyncFlushResult {
            var expensesSynced = 0
            var paymentsSynced = 0
            val failures = mutableListOf<String>()

            expenseRepository.getPendingSync().forEach { expense ->
                runCatching {
                    val splits = expenseRepository.getSplits(expense.id)
                    pushExpense(expense, splits)
                    val synced =
                        expense.copy(
                            remoteId = expense.remoteId ?: expense.id,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    expenseRepository.upsertExpenseWithSplits(
                        synced,
                        splits.map { it.copy(syncStatus = SyncStatus.SYNCED) },
                    )
                    expensesSynced++
                }.onFailure { err ->
                    failures += "Expense ${expense.description}: ${err.message ?: "failed"}"
                }
            }

            paymentRepository.getPendingSync().forEach { payment ->
                runCatching {
                    paymentRemote.upsert(payment.toDto())
                    paymentRepository.upsert(
                        payment.copy(
                            remoteId = payment.remoteId ?: payment.id,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    paymentsSynced++
                }.onFailure { err ->
                    failures += "Payment ${payment.id.take(8)}: ${err.message ?: "failed"}"
                }
            }

            return SyncFlushResult(
                expensesSynced = expensesSynced,
                paymentsSynced = paymentsSynced,
                failures = failures,
            )
        }

        private suspend fun pushExpense(expense: Expense, splits: List<ExpenseSplit>) {
            expenseRemote.upsertExpense(
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
            expenseRemote.deleteSplitsForExpense(expense.id)
            expenseRemote.upsertSplits(
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

        private fun Payment.toDto() =
            PaymentDto(
                id = id,
                fromUserId = fromUserId,
                toUserId = toUserId,
                amount = amount.toPlainString(),
                currencyCode = currencyCode,
                groupId = groupId,
                note = note,
                paidAtEpochMs = paidAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
    }
