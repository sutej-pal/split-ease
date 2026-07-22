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
            val source = if (groupId == null) {
                paymentDao.observeAll()
            } else {
                paymentDao.observeByGroup(groupId)
            }
            return source.map { rows -> rows.map { it.toDomain() } }
        }

        override suspend fun getById(id: String): Payment? = paymentDao.getById(id)?.toDomain()

        override suspend fun upsert(payment: Payment) {
            paymentDao.upsert(payment.toEntity())
        }

        override suspend fun deleteById(id: String) {
            paymentDao.deleteById(id)
        }
    }
