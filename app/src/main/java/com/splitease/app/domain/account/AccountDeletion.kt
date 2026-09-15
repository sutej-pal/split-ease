package com.splitease.app.domain.account

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One group (or the non-group ledger) whose net must be zero before account deletion.
 *
 * @property groupId Group UUID, or blank for non-group expenses.
 * @property groupName Display name for the blocking context.
 * @property netByCurrency Viewer nets keyed by ISO 4217 code (scale 2).
 */
data class AccountDeletionBalanceSlice(
    val groupId: String,
    val groupName: String,
    val netByCurrency: Map<String, BigDecimal> = emptyMap(),
)

/**
 * A group that currently blocks account deletion.
 *
 * @property groupId Group UUID, or blank for non-group expenses.
 * @property groupName Display name shown in the UI / RPC error payload.
 */
data class AccountDeletionBlockingGroup(
    val groupId: String,
    val groupName: String,
)

/**
 * Client-side eligibility for [com.splitease.app.domain.repository.AuthRepository.deleteOwnAccount].
 *
 * The Supabase RPC recomputes nets independently — this is only a pre-flight so the
 * UI can disable confirm and list blocking groups before the user tries.
 */
object AccountDeletionEligibility {
    private val ZERO = BigDecimal.ZERO.setScale(2)

    /**
     * Returns groups (and the non-group ledger) where any currency net is non-zero
     * at scale 2 ([RoundingMode.HALF_UP], same as [com.splitease.app.domain.balance.BalanceCalculator]).
     *
     * Comparison uses [BigDecimal.compareTo], never `==` or [Double].
     *
     * @param slices Per-group viewer nets.
     * @return Blocking groups in input order; empty when deletion is allowed locally.
     */
    fun blockingGroups(slices: List<AccountDeletionBalanceSlice>): List<AccountDeletionBlockingGroup> =
        slices.mapNotNull { slice ->
            val hasBalance =
                slice.netByCurrency.values.any { net ->
                    net.setScale(2, RoundingMode.HALF_UP).compareTo(ZERO) != 0
                }
            if (hasBalance) {
                AccountDeletionBlockingGroup(groupId = slice.groupId, groupName = slice.groupName)
            } else {
                null
            }
        }
}

/**
 * Server (or fake RPC) rejected deletion because the caller still has a non-zero net.
 *
 * @property blockingGroups Groups included in the RPC error payload.
 */
class AccountDeletionBlockedException(
    val blockingGroups: List<AccountDeletionBlockingGroup>,
) : Exception(
        if (blockingGroups.isEmpty()) {
            "Account still has a non-zero balance."
        } else {
            "Settle up in ${blockingGroups.joinToString { it.groupName }} before deleting."
        },
    )

/** Deletion requires a live network call and is not queued like other offline writes. */
class AccountDeletionOfflineException : Exception("Account deletion requires an internet connection.")
