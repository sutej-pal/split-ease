package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.ExpenseDao
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [ExpenseRepository].
 *
 * @property expenseDao Local expenses DAO.
 */
@Singleton
class RoomExpenseRepository
    @Inject
    constructor(
        private val expenseDao: ExpenseDao,
    ) : ExpenseRepository {
        override fun observeExpenses(groupId: String?): Flow<List<Expense>> {
            val source =
                if (groupId == null) {
                    expenseDao.observeAll()
                } else {
                    expenseDao.observeByGroup(groupId)
                }
            return source.map { rows -> rows.map { it.toDomain() } }
        }

        override suspend fun getExpenseById(id: String): Expense? = expenseDao.getById(id)?.toDomain()

        override fun observeExpenseById(id: String): Flow<Expense?> =
            expenseDao.observeById(id).map { rows -> rows.firstOrNull()?.toDomain() }

        override suspend fun upsertExpenseWithSplits(
            expense: Expense,
            splits: List<ExpenseSplit>,
        ) {
            expenseDao.upsertExpenseWithSplits(
                expense = expense.toEntity(),
                splits = splits.map { it.toEntity() },
            )
        }

        override suspend fun deleteExpenseById(id: String) {
            expenseDao.deleteById(id)
        }

        override fun observeSplits(expenseId: String): Flow<List<ExpenseSplit>> =
            expenseDao.observeSplits(expenseId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getSplits(expenseId: String): List<ExpenseSplit> =
            expenseDao.getSplits(expenseId).map { it.toDomain() }

        override fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<Expense>> =
            expenseDao.observeBetweenUsers(userId, otherUserId).map { rows -> rows.map { it.toDomain() } }

        override fun observeInvolvingUser(userId: String): Flow<List<Expense>> =
            expenseDao.observeInvolvingUser(userId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getSplitsForExpenses(expenseIds: List<String>): Map<String, List<ExpenseSplit>> {
            if (expenseIds.isEmpty()) return emptyMap()
            val grouped = linkedMapOf<String, MutableList<ExpenseSplit>>()
            expenseIds.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
                expenseDao.getSplitsForExpenses(chunk).forEach { row ->
                    grouped.getOrPut(row.expenseId) { mutableListOf() }.add(row.toDomain())
                }
            }
            return expenseIds.associateWith { id -> grouped[id].orEmpty() }
        }

        override fun observeSplitsByGroup(groupId: String): Flow<Map<String, List<ExpenseSplit>>> =
            expenseDao.observeSplitsByGroup(groupId).map { rows ->
                rows.groupBy { it.expenseId }.mapValues { (_, list) -> list.map { it.toDomain() } }
            }

        override fun observeSplitsForExpenses(expenseIds: List<String>): Flow<Map<String, List<ExpenseSplit>>> {
            if (expenseIds.isEmpty()) return flowOf(emptyMap())
            return expenseDao.observeSplitsForExpenses(expenseIds).map { rows ->
                val grouped =
                    rows.groupBy { it.expenseId }.mapValues { (_, list) -> list.map { it.toDomain() } }
                expenseIds.associateWith { id -> grouped[id].orEmpty() }
            }
        }

        override suspend fun remapUserId(fromUserId: String, toUserId: String) {
            if (fromUserId == toUserId) return
            expenseDao.remapSplitUserId(fromUserId, toUserId)
            expenseDao.remapPaidByUserId(fromUserId, toUserId)
        }

        override suspend fun getDueRecurringTemplates(nowEpochMs: Long): List<Expense> =
            expenseDao.getDueRecurringTemplates(nowEpochMs).map { it.toDomain() }

        override fun search(query: String): Flow<List<Expense>> =
            expenseDao.search(query.trim()).map { rows -> rows.map { it.toDomain() } }

        override fun observeInPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<Expense>> =
            expenseDao.observeInPeriod(fromEpochMs, toEpochMs).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getPendingSync(): List<Expense> =
            expenseDao.getPendingSync().map { it.toDomain() }

        override suspend fun getSyncedIdsByGroup(groupId: String): List<String> =
            expenseDao.getSyncedIdsByGroup(groupId)

        override suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String> =
            expenseDao.getSyncedNonGroupIdsInvolvingUser(userId)
    }

/** SQLite bind limit is 999 on many Android builds; stay well under it. */
private const val SQLITE_BIND_CHUNK = 400
