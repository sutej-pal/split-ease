package com.splitease.app.data.payment

import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.sync.REMOTE_FETCH_ROW_CAP
import com.splitease.app.data.sync.SyncConflictPolicy
import com.splitease.app.data.sync.SyncNetworkLog
import com.splitease.app.data.sync.isCompleteRemoteFetch
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import kotlinx.coroutines.flow.first
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
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository,
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
                        currencyCode = AppCurrencies.normalizeOrDefault(input.currencyCode),
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
         * Pulls remote payments visible to [userId] into Room (payer/payee + member groups).
         *
         * Prunes SYNCED 1:1 rows missing remotely; group rows are pruned inside
         * [refreshGroupPayments]. PENDING / LOCAL_ONLY are never pruned.
         *
         * @param userId Current user id.
         */
        suspend fun refreshPaymentsForUser(userId: String) {
            val remoteRows = remote.fetchInvolvingUser(userId)
            SyncNetworkLog.info(
                "payments plan: involvingUser=${remoteRows.size} " +
                    "(2 GETs: from_user_id + to_user_id), then 1 GET per group",
            )
            remoteRows.forEach { dto ->
                runCatching { persistRemotePayment(dto) }
                    .onFailure { err ->
                        android.util.Log.w(
                            "PaymentSync",
                            "Failed to persist remote payment ${dto.id}",
                            err,
                        )
                    }
            }
            groupRepository.observeGroupsForUser(userId).first().forEach { group ->
                refreshGroupPayments(group.id)
            }
            pruneSyncedMissingRemote(
                localSyncedIds = paymentRepository.getSyncedNonGroupIdsInvolvingUser(userId),
                remoteIds = remoteRows.map { it.id }.toSet(),
                remoteRowCount = remoteRows.size,
            )
        }

        /**
         * Pulls remote payments for a group into Room, then removes local SYNCED rows
         * that are no longer present remotely (hard-deleted on another device).
         *
         * PENDING / LOCAL_ONLY rows are never pruned.
         *
         * @param groupId Group id.
         */
        suspend fun refreshGroupPayments(groupId: String) {
            val remoteRows = remote.fetchByGroup(groupId)
            remoteRows.forEach { dto ->
                runCatching { persistRemotePayment(dto) }
                    .onFailure { err ->
                        android.util.Log.w(
                            "PaymentSync",
                            "Failed to persist remote payment ${dto.id}",
                            err,
                        )
                    }
            }
            pruneSyncedMissingRemote(
                localSyncedIds = paymentRepository.getSyncedIdsByGroup(groupId),
                remoteIds = remoteRows.map { it.id }.toSet(),
                remoteRowCount = remoteRows.size,
            )
        }

        private suspend fun pruneSyncedMissingRemote(
            localSyncedIds: List<String>,
            remoteIds: Set<String>,
            remoteRowCount: Int,
        ) {
            if (!isCompleteRemoteFetch(remoteRowCount)) {
                android.util.Log.w(
                    "PaymentSync",
                    "Skip remote-delete prune: fetch returned $remoteRowCount rows (cap=$REMOTE_FETCH_ROW_CAP)",
                )
                return
            }
            localSyncedIds.forEach { id ->
                if (id in remoteIds) return@forEach
                paymentRepository.deleteById(id)
                android.util.Log.d("PaymentSync", "Pruned remote-deleted payment $id")
            }
        }

        private suspend fun persistRemotePayment(dto: PaymentDto) {
            val existing = paymentRepository.getById(dto.id)
            if (
                !SyncConflictPolicy.shouldApplyRemote(
                    localUpdatedAtEpochMs = existing?.updatedAtEpochMs,
                    localSyncStatus = existing?.syncStatus,
                    remoteUpdatedAtEpochMs = dto.updatedAtEpochMs,
                )
            ) {
                android.util.Log.d(
                    "PaymentSync",
                    "Skip remote payment ${dto.id}: local ${existing?.syncStatus} " +
                        "updatedAt=${existing?.updatedAtEpochMs} >= remote ${dto.updatedAtEpochMs}",
                )
                return
            }
            ensureLocalUserExists(dto.fromUserId)
            ensureLocalUserExists(dto.toUserId)
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
