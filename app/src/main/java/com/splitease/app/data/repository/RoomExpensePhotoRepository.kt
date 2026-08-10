package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.ExpensePhotoDao
import com.splitease.app.data.local.entity.ExpensePhotoEntity
import com.splitease.app.domain.model.ExpensePhoto
import com.splitease.app.domain.repository.ExpensePhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [ExpensePhotoRepository]. */
@Singleton
class RoomExpensePhotoRepository
    @Inject
    constructor(
        private val dao: ExpensePhotoDao,
    ) : ExpensePhotoRepository {
        override fun observeForExpense(expenseId: String): Flow<List<ExpensePhoto>> =
            dao.observeForExpense(expenseId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun upsert(photo: ExpensePhoto) {
            dao.upsert(photo.toEntity())
        }

        override suspend fun upsertAll(photos: List<ExpensePhoto>) {
            if (photos.isEmpty()) return
            dao.upsertAll(photos.map { it.toEntity() })
        }

        override suspend fun getPendingSync(): List<ExpensePhoto> =
            dao.getPendingSync().map { it.toDomain() }

        override suspend fun deleteForExpense(expenseId: String) {
            dao.deleteForExpense(expenseId)
        }

        override suspend fun getById(id: String): ExpensePhoto? = dao.getById(id)?.toDomain()

        private fun ExpensePhoto.toEntity(): ExpensePhotoEntity =
            ExpensePhotoEntity(
                id = id,
                expenseId = expenseId,
                createdByUserId = createdByUserId,
                localPath = localPath,
                remoteUrl = remoteUrl,
                createdAtEpochMs = createdAtEpochMs,
                syncStatus = syncStatus,
            )

        private fun ExpensePhotoEntity.toDomain(): ExpensePhoto =
            ExpensePhoto(
                id = id,
                expenseId = expenseId,
                createdByUserId = createdByUserId,
                localPath = localPath,
                remoteUrl = remoteUrl,
                createdAtEpochMs = createdAtEpochMs,
                syncStatus = syncStatus,
            )
    }
