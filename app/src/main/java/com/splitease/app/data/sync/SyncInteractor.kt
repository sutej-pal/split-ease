package com.splitease.app.data.sync

import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.remote.dto.GroupDto
import com.splitease.app.data.remote.dto.GroupMemberDto
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.pendingOpenTarget
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
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
 * @property expensesSynced Count of expenses pushed.
 * @property paymentsSynced Count of payments pushed.
 * @property failures Error messages for failed rows.
 */
data class SyncFlushResult(
    val groupsSynced: Int = 0,
    val membersSynced: Int = 0,
    val expensesSynced: Int = 0,
    val paymentsSynced: Int = 0,
    val failures: List<String> = emptyList(),
)

/**
 * Offline write queue + remote hydrate for multi-device sync.
 *
 * Flush order: groups → members → expenses → payments (FK-safe).
 * Hydrate pulls friends/groups/expenses/payments into Room.
 */
@Singleton
class SyncInteractor
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val groupRepository: GroupRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val socialRemote: SocialRemoteDataSource,
        private val expenseRemote: ExpenseRemoteDataSource,
        private val paymentRemote: PaymentRemoteDataSource,
        private val socialInteractor: Provider<SocialInteractor>,
        private val expenseInteractor: Provider<ExpenseInteractor>,
        private val paymentInteractor: Provider<PaymentInteractor>,
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        /**
         * Counts pending social + expense + payment rows once.
         */
        suspend fun pendingCount(): Int =
            groupRepository.getPendingGroups().size +
                groupRepository.getPendingMembers().size +
                expenseRepository.getPendingSync().size +
                paymentRepository.getPendingSync().size

        /**
         * Pushes pending groups, members, expenses, and payments to Supabase.
         *
         * Failed rows stay PENDING for the next retry.
         *
         * @return Flush summary.
         */
        suspend fun flushPending(): SyncFlushResult {
            var groupsSynced = 0
            var membersSynced = 0
            var expensesSynced = 0
            var paymentsSynced = 0
            val failures = mutableListOf<String>()

            groupRepository.getPendingGroups().forEach { group ->
                runCatching {
                    pushGroup(group)
                    groupRepository.upsertGroup(
                        group.copy(
                            remoteId = group.remoteId ?: group.id,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    groupsSynced++
                }.onFailure { err ->
                    failures += "Group ${group.name}: ${err.message ?: "failed"}"
                }
            }

            groupRepository.getPendingMembers().forEach { member ->
                runCatching {
                    pushMember(member)
                    groupRepository.upsertMember(member.copy(syncStatus = SyncStatus.SYNCED))
                    membersSynced++
                }.onFailure { err ->
                    failures += "Member ${member.userId.take(8)}: ${err.message ?: "failed"}"
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
                groupsSynced = groupsSynced,
                membersSynced = membersSynced,
                expensesSynced = expensesSynced,
                paymentsSynced = paymentsSynced,
                failures = failures,
            )
        }

        /**
         * Flushes local PENDING rows, then pulls cloud data for the signed-in user into Room.
         *
         * Safe to call on login, cold start, Account → Sync now, and Activity/Groups refresh.
         *
         * @param userId Optional override; defaults to the current auth user.
         * @return Flush summary from the push half (pull failures are soft).
         */
        suspend fun syncForUser(userId: String? = null): SyncFlushResult {
            val uid = userId ?: supabase.auth.currentUserOrNull()?.id ?: return SyncFlushResult()
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
            runCatching {
                socialInteractor.get().acceptPendingInvitesForCurrentUser(
                    userId = uid,
                    inviteToken = pendingInviteToken,
                )
            }.onFailure {
                // RPC may be missing on older projects; fall back to PostgREST accept.
                runCatching { socialRemote.acceptPendingInvites() }
            }
            if (!pendingInviteToken.isNullOrBlank()) {
                runCatching { appSettingsRepository.setPendingInviteToken(null) }
            }
            // Always hydrate after flush so other members' writes become visible.
            runCatching { socialInteractor.get().refreshFriends(uid) }
            runCatching { socialInteractor.get().refreshGroups(uid) }
            runCatching { socialInteractor.get().refreshSentInvites(uid) }
            runCatching { expenseInteractor.get().refreshExpensesForUser(uid) }
            runCatching { paymentInteractor.get().refreshPaymentsForUser(uid) }
            return flush
        }

        private suspend fun pushGroup(group: Group) {
            socialRemote.upsertGroup(
                GroupDto(
                    id = group.id,
                    name = group.name,
                    defaultCurrencyCode = group.defaultCurrencyCode,
                    createdByUserId = group.createdByUserId,
                    updatedAtEpochMs = group.updatedAtEpochMs,
                ),
            )
        }

        private suspend fun pushMember(member: GroupMember) {
            socialRemote.upsertGroupMember(
                GroupMemberDto(
                    id = member.id,
                    groupId = member.groupId,
                    userId = member.userId,
                    role = member.role.name,
                    joinedAtEpochMs = member.joinedAtEpochMs,
                ),
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
