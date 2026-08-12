package com.splitease.app.data.sync

import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.remote.mapper.toDto
import com.splitease.app.data.pinboard.PinBoardPolicy
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.pendingOpenTarget
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Result of a pending flush.
 *
 * @property groupsSynced Count of groups pushed.
 * @property membersSynced Count of memberships pushed.
 * @property invitesSynced Count of invites pushed.
 * @property expensesSynced Count of expenses pushed.
 * @property paymentsSynced Count of payments pushed.
 * @property failures Error messages for failed rows.
 */
data class SyncFlushResult(
    val groupsSynced: Int = 0,
    val membersSynced: Int = 0,
    val invitesSynced: Int = 0,
    val expensesSynced: Int = 0,
    val paymentsSynced: Int = 0,
    val failures: List<String> = emptyList(),
)

/**
 * Offline write queue + remote hydrate for multi-device sync.
 *
 * Flush order: groups → members → invites → expenses → payments (FK-safe).
 * Hydrate pulls friends/groups/expenses/payments into Room.
 *
 * **Out of scope (online-only surfaces):** pin boards ([PinBoardPolicy]), activity events.
 * Do not add them here without an explicit offline-first design.
 */
@Singleton
class SyncInteractor
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val groupRepository: GroupRepository,
        private val inviteRepository: InviteRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val categoryRepository: CategoryRepository,
        private val socialRemote: SocialRemoteDataSource,
        private val expenseRemote: ExpenseRemoteDataSource,
        private val paymentRemote: PaymentRemoteDataSource,
        private val socialInteractor: Provider<SocialInteractor>,
        private val expenseInteractor: Provider<ExpenseInteractor>,
        private val paymentInteractor: Provider<PaymentInteractor>,
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        /** Wall-clock of the last completed [syncForUser] (skips back-to-back full syncs). */
        @Volatile
        private var lastSyncCompletedAtMs: Long = 0L

        /** True when a full [syncForUser] finished within [MIN_SYNC_GAP_MS]. */
        fun wasSyncedRecently(): Boolean =
            System.currentTimeMillis() - lastSyncCompletedAtMs < MIN_SYNC_GAP_MS

        /**
         * Pushes pending groups, members, invites, expenses, and payments to Supabase.
         *
         * Failed rows stay PENDING for the next retry.
         *
         * @return Flush summary.
         */
        suspend fun flushPending(): SyncFlushResult {
            var groupsSynced = 0
            var membersSynced = 0
            var invitesSynced = 0
            var expensesSynced = 0
            var paymentsSynced = 0
            val failures = mutableListOf<String>()

            groupRepository.getPendingGroups().forEach { group ->
                runCatching {
                    val sessionUserId = supabase.auth.currentUserOrNull()?.id
                    val withCreator =
                        if (sessionUserId != null && group.createdByUserId != sessionUserId) {
                            // Stale local creator after re-signup — RLS requires auth.uid().
                            group.copy(createdByUserId = sessionUserId).also {
                                groupRepository.upsertGroup(it)
                            }
                        } else {
                            group
                        }
                    val withCover = socialInteractor.get().ensureCoverUploaded(withCreator)
                    val toUpload = socialInteractor.get().ensurePhotoUploaded(withCover)
                    if (
                        toUpload.coverUrl != group.coverUrl ||
                        toUpload.photoUrl != group.photoUrl
                    ) {
                        groupRepository.upsertGroup(toUpload.copy(syncStatus = SyncStatus.PENDING))
                    }
                    socialRemote.upsertGroup(toUpload.toDto())
                    groupRepository.upsertGroup(
                        toUpload.copy(
                            remoteId = toUpload.remoteId ?: toUpload.id,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    groupsSynced++
                }.onFailure { err ->
                    failures += "Group ${group.name}: ${err.message ?: "failed"}"
                }
            }

            groupRepository
                .getPendingMembers()
                .filter { it.syncStatus != SyncStatus.LOCAL_ONLY }
                .forEach { member ->
                runCatching {
                    socialRemote.upsertGroupMember(member.toDto())
                    groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
                    membersSynced++
                }.onFailure { err ->
                    failures += "Member ${member.userId.take(8)}: ${err.message ?: "failed"}"
                }
            }

            // Invites after groups so group_id FK exists for GROUP invites.
            inviteRepository.getPendingSync().forEach { invite ->
                runCatching {
                    socialRemote.upsertInvite(invite.toDto())
                    inviteRepository.upsert(invite.copy(syncStatus = SyncStatus.SYNCED))
                    invitesSynced++
                }.onFailure { err ->
                    failures += "Invite ${invite.email}: ${err.message ?: "failed"}"
                }
            }

            expenseRepository.getPendingSync().forEach { expense ->
                runCatching {
                    val splits = expenseRepository.getSplits(expense.id)
                    pushExpense(expense, splits)
                    val synced =
                        expense.copy(
                            remoteId = expense.remoteId ?: expense.id,
                            syncStatus = SyncStatus.SYNCED,
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
                        ),
                    )
                    paymentsSynced++
                }.onFailure { err ->
                    failures += "Payment ${payment.id.take(8)}: ${err.message ?: "failed"}"
                }
            }

            runCatching { expenseInteractor.get().flushPendingCommentsAndPhotos() }

            return SyncFlushResult(
                groupsSynced = groupsSynced,
                membersSynced = membersSynced,
                invitesSynced = invitesSynced,
                expensesSynced = expensesSynced,
                paymentsSynced = paymentsSynced,
                failures = failures,
            )
        }

        /**
         * Flushes local PENDING rows, then pulls cloud data for the signed-in user into Room.
         *
         * Safe to call on login, cold start, and Activity/Groups refresh.
         * Back-to-back calls within [MIN_SYNC_GAP_MS] (login hydrate → Home) are skipped
         * unless [force] is true (pull-to-refresh).
         *
         * @param userId Optional override; defaults to the current auth user.
         * @param force When true, ignore the recent-sync short-circuit.
         * @return Flush summary from the push half (pull failures are soft).
         */
        suspend fun syncForUser(
            userId: String? = null,
            force: Boolean = false,
        ): SyncFlushResult {
            val uid = userId ?: supabase.auth.currentUserOrNull()?.id ?: return SyncFlushResult()
            val now = System.currentTimeMillis()
            if (!force && now - lastSyncCompletedAtMs < MIN_SYNC_GAP_MS) {
                return SyncFlushResult()
            }
            val flush = flushPending()
            val pendingInviteToken =
                runCatching { appSettingsRepository.getPendingInviteToken() }.getOrNull()
            if (!pendingInviteToken.isNullOrBlank()) {
                // Capture where to navigate after accept (survives token clear).
                runCatching {
                    socialInteractor.get().loadInvitePreview(pendingInviteToken)?.let { preview ->
                        appSettingsRepository.setPendingInviteOpenTarget(preview.pendingOpenTarget())
                    }
                }
            }
            val inviteClaimed =
                runCatching {
                    socialInteractor.get().acceptPendingInvitesForCurrentUser(
                        _userId = uid,
                        inviteToken = pendingInviteToken,
                    )
                }.getOrElse {
                    // RPC may be missing on older projects; fall back to email-based accept.
                    runCatching { socialRemote.acceptPendingInvites() }
                    // Keep the deep-link token so a later sync can retry accept-by-token.
                    false
                }
            // Only clear when the invite is actually gone (or there was no token).
            if (!pendingInviteToken.isNullOrBlank() && inviteClaimed) {
                runCatching { appSettingsRepository.setPendingInviteToken(null) }
            }
            // Always hydrate after flush so other members' writes become visible.
            runCatching { socialInteractor.get().refreshFriends(uid) }
            runCatching { socialInteractor.get().refreshGroups(uid) }
            runCatching { socialInteractor.get().refreshSentInvites(uid) }
            runCatching { expenseInteractor.get().refreshExpensesForUser(uid) }
            runCatching { paymentInteractor.get().refreshPaymentsForUser(uid) }
            // Invitee may already have shared expenses/groups but no A←B friendship row.
            runCatching { socialInteractor.get().ensureFriendsFromSharedActivity(uid) }
            lastSyncCompletedAtMs = System.currentTimeMillis()
            return flush
        }

        private companion object {
            const val MIN_SYNC_GAP_MS = 8_000L
        }

        private suspend fun pushExpense(expense: Expense, splits: List<ExpenseSplit>) {
            expenseRemote.upsertExpense(
                ExpenseDto(
                    id = expense.id,
                    description = expense.description,
                    amount = expense.amount.toPlainString(),
                    currencyCode = expense.currencyCode,
                    categoryId = categoryRepository.categoryIdForCloud(expense.categoryId),
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
                        paidAmount = split.paidAmount?.toPlainString(),
                        adjustmentAmount = split.adjustmentAmount?.toPlainString(),
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
