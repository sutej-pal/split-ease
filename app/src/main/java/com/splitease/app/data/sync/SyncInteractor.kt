package com.splitease.app.data.sync

import android.content.Context
import androidx.core.content.edit
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.payment.PaymentInteractor
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.remote.mapper.toDto
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.pendingOpenTarget
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
    val pinBoardsSynced: Int = 0,
    val failures: List<String> = emptyList(),
)

/**
 * Offline write queue + remote hydrate for multi-device sync.
 *
 * Flush order: groups → members → invites → expenses → payments → pin boards (FK-safe).
 * Hydrate pulls friends/groups/expenses/payments into Room.
 *
 * Activity events stay local-only. Pin boards use Room + [PinBoardInteractor.flushPending].
 */
@Singleton
class SyncInteractor
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val supabase: SupabaseClient,
        private val groupRepository: GroupRepository,
        private val inviteRepository: InviteRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val socialRemote: SocialRemoteDataSource,
        private val paymentRemote: PaymentRemoteDataSource,
        private val socialInteractor: Provider<SocialInteractor>,
        private val expenseInteractor: Provider<ExpenseInteractor>,
        private val paymentInteractor: Provider<PaymentInteractor>,
        private val pinBoardInteractor: Provider<PinBoardInteractor>,
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        private val hydratePrefs =
            context.getSharedPreferences(HYDRATE_PREFS_NAME, Context.MODE_PRIVATE)
        private val hydrateGate =
            InitialHydrateGate(
                loadCompletedUserId = {
                    hydratePrefs.getString(KEY_HYDRATE_COMPLETED_USER_ID, null)
                },
                persistCompletedUserId = { userId ->
                    hydratePrefs.edit {
                        if (userId.isNullOrBlank()) {
                            remove(KEY_HYDRATE_COMPLETED_USER_ID)
                        } else {
                            putString(KEY_HYDRATE_COMPLETED_USER_ID, userId)
                        }
                    }
                },
            )
        private val syncMutex = Mutex()

        /** Wall-clock of the last completed [syncForUser] (skips back-to-back full syncs). */
        @Volatile
        private var lastSyncCompletedAtMs: Long = 0L

        /** Observed by Groups home to skeleton summary cards during first-login hydrate. */
        val syncState: StateFlow<SyncState> = hydrateGate.state

        /** True when a full [syncForUser] finished within [MIN_SYNC_GAP_MS]. */
        fun wasSyncedRecently(): Boolean =
            System.currentTimeMillis() - lastSyncCompletedAtMs < MIN_SYNC_GAP_MS

        /**
         * True when this device already finished a full post-login hydrate for [userId].
         *
         * Survives process death so subsequent opens skip the summary-card skeleton.
         */
        fun hasCompletedInitialHydrate(userId: String): Boolean = hydrateGate.hasCompleted(userId)

        /**
         * Pins [SyncState.IN_PROGRESS] before the first-login [syncForUser] is awaited,
         * so the UI never observes partial Room writes. No-ops after a completed hydrate.
         */
        fun markInitialHydrateStarted(userId: String) {
            hydrateGate.onStarted(userId)
        }

        /**
         * Clears first-login hydrate tracking after sign-out so the next account hydrates
         * from scratch.
         */
        fun resetSession() {
            lastSyncCompletedAtMs = 0L
            hydrateGate.reset()
        }

        /**
         * Invalidates in-flight expense Room writes so a late cloud callback cannot
         * re-insert rows after sign-out wipe.
         */
        fun discardLocalWrites() {
            expenseInteractor.get().invalidateSession()
        }

        /**
         * Waits for in-flight local expense writes, then pushes PENDING rows while
         * the session is still valid. Call this **before** auth sign-out / Room wipe.
         *
         * Each step has its own timeout so a hung push cannot skip the Room flush,
         * and a hung flush cannot block sign-out forever. Remaining PENDING rows
         * are then discarded with the rest of local data.
         */
        suspend fun flushBeforeSignOut() {
            // Local create may still be in cloudScope; wait, but do not skip the
            // Room flush if that wait times out (row may already be PENDING).
            withTimeoutOrNull(SIGN_OUT_FLUSH_TIMEOUT_MS) {
                expenseInteractor.get().awaitInFlightWork()
            }
            withTimeoutOrNull(SIGN_OUT_FLUSH_TIMEOUT_MS) {
                val result = flushPending()
                if (result.failures.isNotEmpty()) {
                    android.util.Log.w(
                        "SignOutSync",
                        "Flush before sign-out had failures: ${result.failures}",
                    )
                }
            }
        }

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
            var pinBoardsSynced = 0
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
                    val toUpload = socialInteractor.get().ensurePhotoUploaded(withCreator)
                    if (toUpload.photoUrl != group.photoUrl) {
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
                    if (expenseInteractor.get().flushPendingExpense(expense.id)) {
                        expensesSynced++
                    }
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

            runCatching {
                pinBoardsSynced = pinBoardInteractor.get().flushPending()
            }.onFailure { err ->
                failures += "Pin boards: ${err.message ?: "failed"}"
            }

            runCatching { expenseInteractor.get().flushPendingCommentsAndPhotos() }

            return SyncFlushResult(
                groupsSynced = groupsSynced,
                membersSynced = membersSynced,
                invitesSynced = invitesSynced,
                expensesSynced = expensesSynced,
                paymentsSynced = paymentsSynced,
                pinBoardsSynced = pinBoardsSynced,
                failures = failures,
            )
        }

        /**
         * Flushes local PENDING rows, then pulls cloud data for the signed-in user into Room.
         *
         * Safe to call on login, cold start, and Activity/Groups refresh.
         * Back-to-back calls within [MIN_SYNC_GAP_MS] (login hydrate → Home) are skipped
         * unless [force] is true (pull-to-refresh) or this is still the first-login hydrate.
         *
         * Concurrent callers share one mutex so login hydrate and Home cannot double-pull.
         *
         * @param userId Optional override; defaults to the current auth user.
         * @param force When true, ignore the recent-sync short-circuit.
         * @return Flush summary from the push half. First-login pull failures throw
         *   after [SyncState.FAILED] is set; later opens still treat pulls as soft.
         */
        suspend fun syncForUser(
            userId: String? = null,
            force: Boolean = false,
        ): SyncFlushResult =
            syncMutex.withLock {
                val uid =
                    userId ?: supabase.auth.currentUserOrNull()?.id
                        ?: return@withLock SyncFlushResult()
                val isInitialHydrate = !hydrateGate.hasCompleted(uid)
                val now = System.currentTimeMillis()
                // First-login hydrate must not be skipped by the recent-sync gap.
                if (!force && !isInitialHydrate && now - lastSyncCompletedAtMs < MIN_SYNC_GAP_MS) {
                    return@withLock SyncFlushResult()
                }
                if (isInitialHydrate) {
                    hydrateGate.onStarted(uid)
                }
                SyncNetworkLog.beginHydrate(initial = isInitialHydrate)
                try {
                    val flush = flushPending()
                    val pendingInviteToken =
                        runCatching { appSettingsRepository.getPendingInviteToken() }.getOrNull()
                    if (!pendingInviteToken.isNullOrBlank()) {
                        // Capture where to navigate after accept (survives token clear).
                        runCatching {
                            socialInteractor.get().loadInvitePreview(pendingInviteToken)?.let { preview ->
                                appSettingsRepository.setPendingInviteOpenTarget(
                                    preview.pendingOpenTarget(),
                                )
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
                    pullCloudForUser(uid, strict = isInitialHydrate)
                    lastSyncCompletedAtMs = System.currentTimeMillis()
                    if (isInitialHydrate) {
                        hydrateGate.onSuccess(uid)
                    }
                    flush
                } catch (err: CancellationException) {
                    throw err
                } catch (err: Throwable) {
                    if (isInitialHydrate) {
                        hydrateGate.onFailure()
                    }
                    throw err
                } finally {
                    SyncNetworkLog.endHydrate()
                }
            }

        /**
         * Pulls friends/groups/expenses/payments into Room.
         *
         * [strict] is true only for the first-login hydrate: groups/expenses/payments
         * failures propagate so the UI can show retry instead of partial totals.
         * Subsequent opens keep the historical soft (non-throwing) pulls.
         */
        private suspend fun pullCloudForUser(
            uid: String,
            strict: Boolean,
        ) {
            suspend fun soft(block: suspend () -> Unit) {
                runCatching { block() }
            }

            suspend fun maybeHard(block: suspend () -> Unit) {
                if (strict) {
                    block()
                } else {
                    soft(block)
                }
            }

            coroutineScope {
                val friends =
                    async {
                        soft { SyncNetworkLog.phase("friends") { socialInteractor.get().refreshFriends(uid) } }
                    }
                val groups =
                    async {
                        maybeHard {
                            SyncNetworkLog.phase("groups") { socialInteractor.get().refreshGroups(uid) }
                        }
                    }
                val invites =
                    async {
                        soft {
                            SyncNetworkLog.phase("invites") { socialInteractor.get().refreshSentInvites(uid) }
                        }
                    }
                friends.await()
                groups.await()
                invites.await()
            }
            maybeHard {
                SyncNetworkLog.phase("expenses") { expenseInteractor.get().refreshExpensesForUser(uid) }
            }
            maybeHard {
                SyncNetworkLog.phase("payments") { paymentInteractor.get().refreshPaymentsForUser(uid) }
            }
            // Invitee may already have shared expenses/groups but no A←B friendship row.
            soft {
                SyncNetworkLog.phase("friends-from-activity") {
                    socialInteractor.get().ensureFriendsFromSharedActivity(uid)
                }
            }
        }

        private companion object {
            const val MIN_SYNC_GAP_MS = 8_000L
            const val SIGN_OUT_FLUSH_TIMEOUT_MS = 10_000L
            const val HYDRATE_PREFS_NAME = "splitease_sync"
            const val KEY_HYDRATE_COMPLETED_USER_ID = "initial_hydrate_completed_user_id"
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
