package com.splitease.app.data.social

import android.content.Context
import android.util.Log
import com.splitease.app.data.expense.ExpenseInteractor
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.GroupCoverStorage
import com.splitease.app.data.remote.PaymentRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.InvitePreviewDto
import com.splitease.app.data.remote.dto.InvitePreviewMemberDto
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.AuthRepository
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InviteNameTest {
    private val remote: SocialRemoteDataSource = mockk(relaxed = true)
    private val inviteRepository: InviteRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val groupRepository: GroupRepository = mockk(relaxed = true)
    private lateinit var interactor: SocialInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        interactor =
            SocialInteractor(
                appContext = mockk<Context>(relaxed = true),
                friendRepository = mockk<FriendRepository>(relaxed = true),
                groupRepository = groupRepository,
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
                authRepository = mockk<AuthRepository>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun blank_inviter_name_maps_to_null_without_member_substitution() =
        runTest {
            coEvery { remote.fetchInvitePreview("tok") } returns
                InvitePreviewDto(
                    token = "tok",
                    kind = "GROUP",
                    email = "guest@example.com",
                    inviterName = "  ",
                    groupId = "g1",
                    groupName = "Roommates",
                    members =
                        listOf(
                            InvitePreviewMemberDto(displayName = "Sam", alreadyJoined = true),
                        ),
                )

            val preview = interactor.loadInvitePreview("tok")

            assertEquals("tok", preview?.token)
            assertNull(preview?.inviterName)
            assertEquals("Sam", preview?.members?.single()?.displayName)
        }

    @Test
    fun pending_share_text_returns_null_when_sender_name_is_blank() =
        runTest {
            coEvery { inviteRepository.getByFriendRowId("row") } returns
                Invite(
                    id = "inv",
                    token = "tok",
                    inviterUserId = "me",
                    email = "guest@example.com",
                    kind = InviteKind.FRIEND,
                    createdAtEpochMs = 1L,
                )
            coEvery { userRepository.getUserById("me") } returns user("me", displayName = "  ")

            assertNull(interactor.pendingInviteShareText("row"))
        }

    @Test
    fun group_share_link_fails_when_sender_name_is_blank_and_does_not_push() =
        runTest {
            coEvery { inviteRepository.getGroupShareInvites("g1", any()) } returns emptyList()
            coEvery { groupRepository.getGroupById("g1") } returns
                Group(
                    id = "g1",
                    name = "Roommates",
                    defaultCurrencyCode = "INR",
                    createdByUserId = "me",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                )
            coEvery { userRepository.getUserById("me") } returns user("me", displayName = "")

            val result = interactor.getOrCreateGroupShareLink("me", "g1")

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message?.contains("Invite sender profile is missing") == true,
            )
            coVerify(exactly = 0) { remote.upsertInvite(any()) }
            coVerify(exactly = 0) { inviteRepository.upsert(any()) }
        }

    private fun user(
        id: String,
        displayName: String,
    ): User =
        User(
            id = id,
            email = "me@example.com",
            displayName = displayName,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
}
