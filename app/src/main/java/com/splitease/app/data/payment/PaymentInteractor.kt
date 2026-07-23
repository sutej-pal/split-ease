package com.splitease.app.data.payment

import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.PaymentRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Input for recording a settlement payment.
 *
 * @property fromUserId Debtor who pays.
 * @property toUserId Creditor who receives.
 * @property amount Positive amount.
 * @property currencyCode ISO 4217 code.
 * @property groupId Optional group context.
 * @property note Optional memo.
 */
data class RecordPaymentInput(
    val fromUserId: String,
    val toUserId: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val groupId: String? = null,
    val note: String? = null,
)

/**
 * Records settlements into Room (local-first) and pulls remote payments.
 */
@Singleton
class PaymentInteractor
    @Inject
    constructor(
        private val paymentRepository: PaymentRepository,
        private val remote: PaymentRemoteDataSource,
    ) {
        /**
         * Persists a settlement payment.
         *
         * @param input Settlement payload.
         * @return Saved [Payment].
         */
        suspend fun recordPayment(input: RecordPaymentInput): Result<Payment> =
            runCatching {
                require(input.fromUserId != input.toUserId) { "Payer and payee must differ." }
                require(input.amount > BigDecimal.ZERO) { "Amount must be positive." }
                val now = System.currentTimeMillis()
                val payment =
                    Payment(
                        id = UUID.randomUUID().toString(),
                        fromUserId = input.fromUserId,
                        toUserId = input.toUserId,
                        amount = input.amount.setScale(2, RoundingMode.HALF_UP),
                        currencyCode = input.currencyCode.trim().ifBlank { "INR" }.uppercase(),
                        groupId = input.groupId,
                        note = input.note?.trim()?.ifBlank { null },
                        paidAtEpochMs = now,
                        remoteId = null,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                paymentRepository.upsert(payment)
                payment
            }

        /**
         * Pulls remote payments involving [userId] into Room.
         *
         * @param userId Current user id.
         */
        suspend fun refreshPaymentsForUser(userId: String) {
            remote.fetchInvolvingUser(userId).forEach { dto ->
                persistRemotePayment(dto)
            }
        }

        private suspend fun persistRemotePayment(dto: PaymentDto) {
            val existing = paymentRepository.getById(dto.id)
            paymentRepository.upsert(
                Payment(
                    id = dto.id,
                    fromUserId = dto.fromUserId,
                    toUserId = dto.toUserId,
                    amount = BigDecimal(dto.amount),
                    currencyCode = dto.currencyCode,
                    groupId = dto.groupId,
                    note = dto.note,
                    paidAtEpochMs = dto.paidAtEpochMs,
                    remoteId = dto.id,
                    createdAtEpochMs = existing?.createdAtEpochMs ?: dto.updatedAtEpochMs,
                    updatedAtEpochMs = dto.updatedAtEpochMs,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
        }
    }
