package com.splitease.app.data.social

import android.content.Context
import android.util.Log
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.GroupCoverStorage
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.FriendDto
import com.splitease.app.data.remote.dto.InviteDto
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SocialHydrateTest {
    private val remote: SocialRemoteDataSource = mockk(relaxed = true)
    private val friendRepository: FriendRepository = mockk(relaxed = true)
    private val inviteRepository: InviteRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private lateinit var interactor: SocialInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        coEvery { friendRepository.getById(any()) } returns null
        coEvery { userRepository.getUserById(any()) } answers {
            User(
                id = firstArg(),
                email = "local@example.com",
                displayName = "Member",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            )
        }
        coEvery { userRepository.getUserByEmail(any()) } returns null
        coEvery { inviteRepository.getByToken(any()) } returns null

        interactor =
            SocialInteractor(
                appContext = mockk<Context>(relaxed = true),
                friendRepository = friendRepository,
                groupRepository = mockk<GroupRepository>(relaxed = true),
                inviteRepository = inviteRepository,
                userRepository = userRepository,
                expenseRepository = mockk<ExpenseRepository>(relaxed = true),
                paymentRepository = mockk<PaymentRepository>(relaxed = true),
                remote = remote,
                groupCoverStorage = mockk<GroupCoverStorage>(relaxed = true),
                mediaStorageCleanup = mockk<MediaStorageCleanup>(relaxed = true),
                pinBoardInteractor = mockk<PinBoardInteractor>(relaxed = true),
                expenseInteractor = mockk<ExpenseInteractor>(relaxed = true),
                mailRepository = mockk<MailRepository>(relaxed = true),
                paymentRemote = mockk<PaymentRemoteDataSource>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun refreshFriends_batches_profile_lookup() =
        runTest {
            coEvery { remote.fetchFriends("me") } returns
                listOf(
                    friendDto("fr1", "f1"),
                    friendDto("fr2", "f2"),
                )
            coEvery { remote.fetchProfilesByIds(match { it.toSet() == setOf("f1", "f2") }) } returns
                listOf(
                    profileDto("f1"),
                    profileDto("f2"),
                )

            interactor.refreshFriends("me")

            coVerify(exactly = 1) { remote.fetchFriends("me") }
            coVerify(exactly = 1) {
                remote.fetchProfilesByIds(match { it.toSet() == setOf("f1", "f2") })
            }
            coVerify(exactly = 0) { remote.fetchProfileById(any()) }
        }

    @Test
    fun refreshSentInvites_fetches_invites_once() =
        runTest {
            coEvery { remote.fetchInvitesSentBy("me") } returns
                listOf(
                    InviteDto(
                        id = "inv1",
                        token = "tok",
                        inviterUserId = "me",
                        email = "a@b.com",
                        kind = "PERSON",
                        status = "PENDING",
                        createdAtEpochMs = 1L,
                    ),
                )

            interactor.refreshSentInvites("me")

            coVerify(exactly = 1) { remote.fetchInvitesSentBy("me") }
        }

    private fun friendDto(
        id: String,
        friendUserId: String,
    ) = FriendDto(
        id = id,
        ownerUserId = "me",
        friendUserId = friendUserId,
        emailSnapshot = "$friendUserId@example.com",
        displayNameSnapshot = friendUserId,
        updatedAtEpochMs = 1L,
    )

    private fun profileDto(id: String) =
        ProfileDto(
            id = id,
            email = "$id@example.com",
            displayName = id,
            updatedAtEpochMs = 1L,
        )
}
