package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.ExpenseCommentDao
import com.splitease.app.data.local.entity.ExpenseCommentEntity
import com.splitease.app.domain.model.ExpenseComment
import com.splitease.app.domain.model.ExpenseCommentKind
import com.splitease.app.domain.repository.ExpenseCommentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [ExpenseCommentRepository]. */
@Singleton
class RoomExpenseCommentRepository
    @Inject
    constructor(
        private val dao: ExpenseCommentDao,
    ) : ExpenseCommentRepository {
        override fun observeForExpense(expenseId: String): Flow<List<ExpenseComment>> =
            dao.observeForExpense(expenseId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun upsert(comment: ExpenseComment) {
            dao.upsert(comment.toEntity())
        }

        override suspend fun upsertAll(comments: List<ExpenseComment>) {
            if (comments.isEmpty()) return
            dao.upsertAll(comments.map { it.toEntity() })
        }

        override suspend fun getPendingSync(): List<ExpenseComment> =
            dao.getPendingSync().map { it.toDomain() }

        override suspend fun deleteForExpense(expenseId: String) {
            dao.deleteForExpense(expenseId)
        }

        private fun ExpenseComment.toEntity(): ExpenseCommentEntity =
            ExpenseCommentEntity(
                id = id,
                expenseId = expenseId,
                authorUserId = authorUserId,
                body = body,
                kind = kind.name,
                createdAtEpochMs = createdAtEpochMs,
                syncStatus = syncStatus,
            )

        private fun ExpenseCommentEntity.toDomain(): ExpenseComment =
            ExpenseComment(
                id = id,
                expenseId = expenseId,
                authorUserId = authorUserId,
                body = body,
                kind =
                    runCatching { ExpenseCommentKind.valueOf(kind) }
                        .getOrDefault(ExpenseCommentKind.USER),
                createdAtEpochMs = createdAtEpochMs,
                syncStatus = syncStatus,
            )
    }
