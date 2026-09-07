package com.splitease.app.domain.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Repository-level fake of `delete_own_account`: ignores any client-side
 * "I'm settled" flag and recomputes eligibility from server-held BigDecimal nets.
 */
class AccountDeletionRepositoryTest {
    @Test
    fun fake_rpc_rejects_nonzero_balance_even_if_client_claims_settled() {
        val repo =
            FakeAccountDeletionRepository(
                remoteSlices =
                    listOf(
                        AccountDeletionBalanceSlice(
                            groupId = "g-open",
                            groupName = "Cabin trip",
                            netByCurrency = mapOf("INR" to BigDecimal("0.01")),
                        ),
                    ),
            )
        val result = repo.deleteOwnAccount(clientClaimsSettled = true)
        val err = result.exceptionOrNull()
        assertTrue(err is AccountDeletionBlockedException)
        val blocked = err as AccountDeletionBlockedException
        assertEquals("g-open", blocked.blockingGroups.single().groupId)
        assertEquals("Cabin trip", blocked.blockingGroups.single().groupName)
    }

    @Test
    fun fake_rpc_allows_when_all_group_nets_are_zero_at_scale_2() {
        val repo =
            FakeAccountDeletionRepository(
                remoteSlices =
                    listOf(
                        AccountDeletionBalanceSlice(
                            groupId = "g1",
                            groupName = "Roommates",
                            netByCurrency = mapOf("INR" to BigDecimal("0.00")),
                        ),
                        AccountDeletionBalanceSlice(
                            groupId = "",
                            groupName = "Non-group expenses",
                            netByCurrency = mapOf("USD" to BigDecimal("0.001")),
                        ),
                    ),
            )
        assertTrue(repo.deleteOwnAccount(clientClaimsSettled = false).isSuccess)
    }

    @Test
    fun fake_rpc_rejects_half_up_rounding_edge() {
        val repo =
            FakeAccountDeletionRepository(
                remoteSlices =
                    listOf(
                        AccountDeletionBalanceSlice(
                            groupId = "g1",
                            groupName = "Rent",
                            netByCurrency = mapOf("INR" to BigDecimal("0.005")),
                        ),
                    ),
            )
        val err = repo.deleteOwnAccount().exceptionOrNull()
        assertTrue(err is AccountDeletionBlockedException)
        assertEquals("Rent", (err as AccountDeletionBlockedException).blockingGroups.single().groupName)
    }

    @Test
    fun maps_rpc_json_payload_into_blocking_groups() {
        val mapped =
            AccountDeletionErrors.map(
                Exception(
                    """{"code":"ACCOUNT_HAS_BALANCE","groups":[{"id":"g1","name":"Roommates"}]}""",
                ),
            )
        assertTrue(mapped is AccountDeletionBlockedException)
        assertEquals(
            listOf(AccountDeletionBlockingGroup("g1", "Roommates")),
            (mapped as AccountDeletionBlockedException).blockingGroups,
        )
    }

    @Test
    fun maps_postgrest_wrapped_json_message() {
        val mapped =
            AccountDeletionErrors.map(
                Exception(
                    """{"code":"P0001","message":"{\"code\":\"ACCOUNT_HAS_BALANCE\",\"groups\":[{\"id\":\"g1\",\"name\":\"Roommates\"}]}"}""",
                ),
            )
        assertTrue(mapped is AccountDeletionBlockedException)
        assertEquals(
            "Roommates",
            (mapped as AccountDeletionBlockedException).blockingGroups.single().groupName,
        )
    }

    @Test
    fun maps_network_failure_to_offline() {
        val mapped = AccountDeletionErrors.map(Exception("Unable to resolve host api.example.com"))
        assertTrue(mapped is AccountDeletionOfflineException)
    }
}

private class FakeAccountDeletionRepository(
    private val remoteSlices: List<AccountDeletionBalanceSlice>,
) {
    /**
     * @param clientClaimsSettled Ignored on purpose — the RPC must not trust a client flag.
     */
    @Suppress("UNUSED_PARAMETER")
    fun deleteOwnAccount(clientClaimsSettled: Boolean = false): Result<Unit> {
        val blocked = AccountDeletionEligibility.blockingGroups(remoteSlices)
        if (blocked.isEmpty()) return Result.success(Unit)
        val payload =
            buildString {
                append("""{"code":"ACCOUNT_HAS_BALANCE","groups":[""")
                append(
                    blocked.joinToString(",") { group ->
                        """{"id":"${group.groupId}","name":"${group.groupName}"}"""
                    },
                )
                append("]}")
            }
        return Result.failure(AccountDeletionErrors.map(Exception(payload)))
    }
}
