package com.splitease.app.data.payment

import android.util.Log
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.dto.PaymentDto
import com.splitease.app.data.sync.REMOTE_FETCH_ROW_CAP
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentHydrateTest {
    private val remote: PaymentRemoteDataSource = mockk(relaxed = true)
    private val paymentRepository: PaymentRepository = mockk(relaxed = true)
    private val groupRepository: GroupRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private lateinit var interactor: PaymentInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0

        coEvery { paymentRepository.getById(any()) } returns null
        coEvery { paymentRepository.getSyncedIdsByGroup(any()) } returns emptyList()
        coEvery { paymentRepository.getSyncedNonGroupIdsInvolvingUser(any()) } returns emptyList()
        coEvery { userRepository.getUserById(any()) } returns stubUser("u1")

        interactor =
            PaymentInteractor(
                paymentRepository = paymentRepository,
                groupRepository = groupRepository,
                userRepository = userRepository,
                remote = remote,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun refreshPaymentsForUser_batches_group_fetch_and_skips_per_group_refetch() =
        runTest {
            val involving = paymentDto("p1", groupId = null, from = "u1", to = "u2")
            val groupOnly = paymentDto("p2", groupId = "g1", from = "u2", to = "u3")
            coEvery { remote.fetchInvolvingUser("u1") } returns listOf(involving)
            coEvery { groupRepository.observeGroupsForUser("u1") } returns flowOf(listOf(group("g1")))
            coEvery { remote.fetchByGroupIds(listOf("g1")) } returns listOf(groupOnly)

            interactor.refreshPaymentsForUser("u1")

            coVerify(exactly = 1) { remote.fetchInvolvingUser("u1") }
            coVerify(exactly = 1) { remote.fetchByGroupIds(listOf("g1")) }
            coVerify(exactly = 0) { remote.fetchByGroup(any()) }
            coVerify(exactly = 2) { paymentRepository.upsert(any()) }
        }

    @Test
    fun refreshPaymentsForUser_capped_group_fetch_skips_prune() =
        runTest {
            val capped =
                List(REMOTE_FETCH_ROW_CAP) { index ->
                    paymentDto("p$index", groupId = "g1", from = "u2", to = "u3")
                }
            coEvery { remote.fetchInvolvingUser("u1") } returns emptyList()
            coEvery { groupRepository.observeGroupsForUser("u1") } returns
                flowOf(listOf(group("g1"), group("g2")))
            coEvery {
                remote.fetchByGroupIds(match { it.toSet() == setOf("g1", "g2") })
            } returns capped
            coEvery { paymentRepository.getSyncedIdsByGroup("g1") } returns listOf("stale")
            coEvery { paymentRepository.getSyncedIdsByGroup("g2") } returns listOf("other-stale")

            interactor.refreshPaymentsForUser("u1")

            coVerify(exactly = 0) { paymentRepository.deleteById(any()) }
        }

    private fun paymentDto(
        id: String,
        groupId: String?,
        from: String,
        to: String,
    ) = PaymentDto(
        id = id,
        fromUserId = from,
        toUserId = to,
        amount = "10.00",
        currencyCode = "INR",
        groupId = groupId,
        note = null,
        paidAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun group(id: String) =
        Group(
            id = id,
            name = id,
            defaultCurrencyCode = "INR",
            groupType = GroupType.OTHER,
            createdByUserId = "u1",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            syncStatus = SyncStatus.SYNCED,
        )

    private fun stubUser(id: String) =
        User(
            id = id,
            email = "$id@example.com",
            displayName = id,
            photoUrl = null,
            remoteId = id,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            syncStatus = SyncStatus.SYNCED,
        )
}
