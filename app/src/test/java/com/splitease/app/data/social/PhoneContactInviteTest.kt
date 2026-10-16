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
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhoneContactInviteTest {
    private val remote: SocialRemoteDataSource = mockk(relaxed = true)
    private val inviteRepository: InviteRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val friendRepository: FriendRepository = mockk(relaxed = true)
    private val mailRepository: MailRepository = mockk(relaxed = true)
    private val friends = mutableListOf<Friend>()
    private val users = mutableMapOf<String, User>()
    private val emailLookups = mutableListOf<String>()
    private lateinit var interactor: SocialInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        users["me"] =
            User(
                id = "me",
                email = "me@example.com",
                displayName = "Ada",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            )
        coEvery { userRepository.getUserById(any()) } answers { users[firstArg()] }
        coEvery { userRepository.upsert(any()) } answers {
            val user = firstArg<User>()
            users[user.id] = user
        }
        coEvery { userRepository.getUserByEmail(any()) } returns null
        coEvery { friendRepository.upsert(any()) } answers {
            val friend = firstArg<Friend>()
            friends.removeAll { it.id == friend.id }
            friends += friend
        }
        coEvery { friendRepository.getByOwnerAndEmail(any(), any()) } answers {
            val email = secondArg<String>()
            emailLookups += email
            friends.lastOrNull { it.emailSnapshot.equals(email, ignoreCase = true) }
        }
        coEvery { friendRepository.getById(any()) } answers {
            friends.firstOrNull { it.id == firstArg<String>() }
        }
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
                mailRepository = mailRepository,
                paymentRemote = mockk<PaymentRemoteDataSource>(relaxed = true),
                authRepository = mockk<AuthRepository>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun addFriendByContact_stores_phone_placeholder_without_mail_and_dedupes() =
        runTest {
            val first =
                interactor
                    .addFriendByContact(ownerUserId = "me", contact = "98765 43210", displayName = null)
                    .getOrThrow()
            val stored = "+919876543210@${ContactIdentifier.MOBILE_CONTACT_DOMAIN}"
            assertEquals(stored, first.friend.emailSnapshot)
            assertEquals("+91 98765 43210 (invited)", first.friend.displayNameSnapshot)
            assertEquals("+919876543210", first.invitePhone)
            assertTrue(first.isInvitePending)
            assertFalse(first.inviteEmailSent)
            assertNotNull(first.inviteShareText)

            val second =
                interactor
                    .addFriendByContact(ownerUserId = "me", contact = "09876543210")
                    .getOrThrow()
            assertEquals(first.friend.id, second.friend.id)
            assertEquals(stored, second.friend.emailSnapshot)
            assertEquals(listOf(stored, stored), emailLookups)
            assertEquals(1, friends.size)
            coVerify(exactly = 0) { mailRepository.sendInviteEmail(any(), any(), any(), any()) }
            coVerify(exactly = 0) { remote.findProfileByEmail(any()) }
        }

    @Test
    fun deliverPendingInvite_does_not_email_mobile_placeholder() =
        runTest {
            val stored = "+919876543210@mobile.splitease.com"
            val friend =
                Friend(
                    id = "row",
                    ownerUserId = "me",
                    friendUserId = "ph",
                    emailSnapshot = stored,
                    displayNameSnapshot = "Sam (invited)",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                )
            friends += friend
            coEvery { inviteRepository.getByFriendRowId("row") } returns
                Invite(
                    id = "inv",
                    token = "tok",
                    inviterUserId = "me",
                    email = stored,
                    kind = InviteKind.FRIEND,
                    friendRowId = "row",
                    status = InviteStatus.PENDING,
                    createdAtEpochMs = 1L,
                )

            val outcome = interactor.deliverPendingInvite("row")

            assertNotNull(outcome)
            assertFalse(outcome!!.inviteEmailSent)
            assertEquals("+919876543210", outcome.invitePhone)
            assertNotNull(outcome.inviteShareText)
            coVerify(exactly = 0) { mailRepository.sendInviteEmail(any(), any(), any(), any()) }
        }

    @Test
    fun loadInvitePreview_maps_phone_placeholder_and_member_token() =
        runTest {
            coEvery { remote.fetchInvitePreview("tok") } returns
                InvitePreviewDto(
                    token = "tok",
                    kind = "GROUP",
                    email = "+919876543210@mobile.splitease.com",
                    inviterName = "Ada",
                    inviteeName = "Sam",
                    groupId = "g1",
                    groupName = "Roommates",
                    members =
                        listOf(
                            InvitePreviewMemberDto(
                                displayName = "Sam",
                                alreadyJoined = false,
                                inviteToken = "sam-token",
                            ),
                            InvitePreviewMemberDto(displayName = "Jo", alreadyJoined = true),
                        ),
                )

            val preview = interactor.loadInvitePreview("tok")

            assertEquals("", preview?.email)
            assertEquals("Sam", preview?.inviteeName)
            assertEquals("+91", preview?.phoneCountryCode)
            assertEquals("9876543210", preview?.phoneNumber)
            assertEquals("sam-token", preview?.members?.get(0)?.inviteToken)
            assertNull(preview?.members?.get(1)?.inviteToken)
        }

    @Test
    fun old_invite_preview_payload_without_new_fields_decodes() {
        val dto =
            Json.decodeFromString<InvitePreviewDto>(
                """
                {"token":"tok","kind":"FRIEND","email":"a@b.com","inviter_name":"Ada","members":[{"display_name":"Sam","already_joined":true}]}
                """.trimIndent(),
            )
        assertEquals("", dto.inviteeName)
        assertNull(dto.members.single().inviteToken)
        assertEquals("a@b.com", dto.email)
    }
}
