package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.PaymentDao
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [PaymentRepository].
 *
 * @property paymentDao Local payments DAO.
 */
@Singleton
class RoomPaymentRepository
    @Inject
    constructor(
        private val paymentDao: PaymentDao,
    ) : PaymentRepository {
        override fun observePayments(groupId: String?): Flow<List<Payment>> {
            val source =
                if (groupId == null) {
                    paymentDao.observeAll()
                } else {
                    paymentDao.observeByGroup(groupId)
                }
            return source.map { rows -> rows.map { it.toDomain() } }
        }

        override fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<Payment>> =
            paymentDao.observeBetweenUsers(userId, otherUserId).map { rows -> rows.map { it.toDomain() } }

        override fun observeInvolvingUser(userId: String): Flow<List<Payment>> =
            paymentDao.observeInvolvingUser(userId).map { rows -> rows.map { it.toDomain() } }

        override fun observeRecentInvolvingUser(
            userId: String,
            limit: Int,
        ): Flow<List<Payment>> =
            paymentDao.observeRecentInvolvingUser(userId, limit).map { rows -> rows.map { it.toDomain() } }

        override fun observeRecentSharedWithUser(
            userId: String,
            otherUserId: String,
            limit: Int,
        ): Flow<List<Payment>> =
            paymentDao
                .observeRecentSharedWithUser(userId, otherUserId, limit)
                .map { rows -> rows.map { it.toDomain() } }

        override fun observeRecentNonGroupInvolvingUser(
            userId: String,
            limit: Int,
        ): Flow<List<Payment>> =
            paymentDao
                .observeRecentNonGroupInvolvingUser(userId, limit)
                .map { rows -> rows.map { it.toDomain() } }

        override fun observeRecentByGroup(
            groupId: String,
            limit: Int,
        ): Flow<List<Payment>> =
            paymentDao.observeRecentByGroup(groupId, limit).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getById(id: String): Payment? = paymentDao.getById(id)?.toDomain()

        override suspend fun upsert(payment: Payment) {
            paymentDao.upsert(payment.toEntity())
        }

        override suspend fun deleteById(id: String) {
            paymentDao.deleteById(id)
        }

        override suspend fun getPendingSync(): List<Payment> =
            paymentDao.getPendingSync().map { it.toDomain() }

        override suspend fun getSyncedIdsByGroup(groupId: String): List<String> =
            paymentDao.getSyncedIdsByGroup(groupId)

        override suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String> =
            paymentDao.getSyncedNonGroupIdsInvolvingUser(userId)
    }
