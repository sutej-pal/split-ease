package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.data.sync.SyncFlushResult
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.account.AccountDeletionBlockedException
import com.splitease.app.domain.account.AccountDeletionOfflineException
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow =
        MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(AuthUser("u1", "bob@example.com", "Bob")),
        )
    private val balancesFlow = MutableStateFlow(emptyBalances())
    private lateinit var authRepository: AuthRepository
    private lateinit var balanceInteractor: BalanceInteractor
    private lateinit var syncInteractor: SyncInteractor
    private lateinit var context: Context
    private lateinit var viewModel: DeleteAccountViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        balanceInteractor = mockk(relaxed = true)
        syncInteractor = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { authRepository.observeSession() } returns sessionFlow
        every { balanceInteractor.observeOverallBalances("u1") } returns balancesFlow
        coEvery { syncInteractor.syncForUser(any(), any()) } returns SyncFlushResult()
        every { context.getString(any()) } answers { stringFor(invocation.args[0] as Int) }
        every { context.getString(any(), *anyVararg()) } answers {
            val id = invocation.args[0] as Int
            val formatArgs =
                invocation.args
                    .drop(1)
                    .flatMap { arg ->
                        when (arg) {
                            is Array<*> -> arg.map { it.toString() }
                            else -> listOf(arg.toString())
                        }
                    }
            when (id) {
                R.string.account_delete_blocked_named ->
                    "Settle up in ${formatArgs.joinToString(", ")} before deleting your account."
                else -> stringFor(id)
            }
        }
        viewModel =
            DeleteAccountViewModel(
                appContext = context,
                authRepository = authRepository,
                balanceInteractor = balanceInteractor,
                syncInteractor = syncInteractor,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun open_syncs_balances_and_lists_blocking_groups() =
        runTest(dispatcher) {
            balancesFlow.value =
                emptyBalances(
                    groups =
                        listOf(
                            group("g1", "Cabin trip", BigDecimal("10.00")),
                        ),
                )
            val job = launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertEquals("Cabin trip", viewModel.uiState.value.blockingGroups.single().groupName)
            assertFalse(viewModel.uiState.value.isRefreshingBalances)
            coVerify { syncInteractor.syncForUser("u1", force = true) }
            job.cancel()
        }

    @Test
    fun delete_blocked_after_refresh_does_not_call_rpc() =
        runTest(dispatcher) {
            val job = launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            balancesFlow.value =
                emptyBalances(
                    groups = listOf(group("g1", "Rent", BigDecimal("0.01"))),
                )
            viewModel.deleteAccount()
            advanceUntilIdle()
            coVerify(exactly = 0) { authRepository.deleteOwnAccount() }
            assertEquals(
                "Settle up in Rent before deleting your account.",
                viewModel.action.value.errorMessage,
            )
            assertFalse(viewModel.action.value.isDeleting)
            job.cancel()
        }

    @Test
    fun delete_when_settled_calls_rpc() =
        runTest(dispatcher) {
            coEvery { authRepository.deleteOwnAccount() } returns Result.success(Unit)
            val job = launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.deleteAccount()
            advanceUntilIdle()
            coVerify(exactly = 1) { authRepository.deleteOwnAccount() }
            assertFalse(viewModel.action.value.isDeleting)
            assertEquals(null, viewModel.action.value.errorMessage)
            job.cancel()
        }

    @Test
    fun delete_offline_shows_offline_copy() =
        runTest(dispatcher) {
            coEvery { authRepository.deleteOwnAccount() } returns
                Result.failure(AccountDeletionOfflineException())
            val job = launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.deleteAccount()
            advanceUntilIdle()
            assertEquals("offline", viewModel.action.value.errorMessage)
            assertFalse(viewModel.action.value.isDeleting)
            job.cancel()
        }

    @Test
    fun delete_server_blocked_shows_named_groups() =
        runTest(dispatcher) {
            coEvery { authRepository.deleteOwnAccount() } returns
                Result.failure(
                    AccountDeletionBlockedException(
                        listOf(
                            com.splitease.app.domain.account.AccountDeletionBlockingGroup(
                                "g1",
                                "Trip",
                            ),
                        ),
                    ),
                )
            val job = launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.deleteAccount()
            advanceUntilIdle()
            assertEquals(
                "Settle up in Trip before deleting your account.",
                viewModel.action.value.errorMessage,
            )
            job.cancel()
        }

    private fun stringFor(id: Int): String =
        when (id) {
            R.string.non_group_expenses -> "Non-group expenses"
            R.string.account_delete_error_offline -> "offline"
            R.string.account_delete_blocked_generic -> "generic"
            R.string.error_generic -> "Something went wrong. Try again."
            else -> "str-$id"
        }

    private fun emptyBalances(
        groups: List<GroupBalanceUi> = emptyList(),
        nonGroup: Map<String, BigDecimal> = emptyMap(),
    ) = OverallBalancesUi(
        totalOwedToMeByCurrency = emptyMap(),
        totalIOweByCurrency = emptyMap(),
        friendBalances = emptyList(),
        groupBalances = groups,
        nonGroupMyNetByCurrency = nonGroup,
    )

    private fun group(
        id: String,
        name: String,
        net: BigDecimal,
    ) = GroupBalanceUi(
        groupId = id,
        groupName = name,
        myNetByCurrency = mapOf("INR" to net),
        memberNetsByCurrency = emptyMap(),
        simplifiedDebts = emptyList(),
    )
}
