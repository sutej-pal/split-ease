package com.splitease.app.presentation.friends

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.splitease.app.R
import com.splitease.app.data.contacts.DeviceContact
import com.splitease.app.data.contacts.DeviceContactsDataSource
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.AddPersonOutcome
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.FriendRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditContactViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val friendRepository: FriendRepository = mockk(relaxed = true)
    private val socialInteractor: SocialInteractor = mockk(relaxed = true)
    private val deviceContacts: DeviceContactsDataSource = mockk(relaxed = true)
    private val reviewStore: PendingFriendReviewStore = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.observeSession() } returns
            flowOf(AuthSession.SignedIn(AuthUser("me", "me@example.com", "Me")))
        every { context.getString(any()) } answers {
            when (firstArg<Int>()) {
                R.string.msg_contact_required -> "required"
                R.string.msg_contact_invalid -> "invalid"
                R.string.msg_name_required -> "name"
                else -> "msg"
            }
        }
        every { context.getString(any(), *anyVararg()) } returns "formatted"
        coEvery {
            socialInteractor.addFriendByContact(any(), any(), any(), isNull())
        } returns
            Result.success(
                AddPersonOutcome(
                    friend = friend("+919876543210@mobile.splitease.com"),
                    isInvitePending = false,
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun single_field_validation_empty_invalid_phone_and_email() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.setName("Sam")

            viewModel.submit({}, {})
            advanceUntilIdle()
            assertEquals("required", viewModel.uiState.value.contactError)
            assertNull(viewModel.uiState.value.phoneConfirm)

            viewModel.setContactInput("abc")
            viewModel.submit({}, {})
            advanceUntilIdle()
            assertEquals("invalid", viewModel.uiState.value.contactError)
            assertNull(viewModel.uiState.value.phoneConfirm)

            viewModel.setContactInput("Ada@Example.com")
            viewModel.submit({}, {})
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.phoneConfirm)
            coVerify {
                socialInteractor.addFriendByContact("me", "Ada@Example.com", "Sam", null)
            }

            viewModel.setContactInput("+44 7700 900123")
            viewModel.submit({}, {})
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.phoneConfirm)
            coVerify {
                socialInteractor.addFriendByContact("me", "+44 7700 900123", "Sam", null)
            }
        }

    @Test
    fun phone_without_country_code_sets_confirm_and_cancel_saves_nothing() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.setName("Sam")
            viewModel.setContactInput("9876543210")
            viewModel.submit({}, {})
            advanceUntilIdle()

            val confirm = viewModel.uiState.value.phoneConfirm
            assertEquals("+91", confirm?.dialCode)
            assertEquals("🇮🇳", confirm?.flag)
            assertEquals("9876543210", confirm?.number)
            coVerify(exactly = 0) {
                socialInteractor.addFriendByContact(any(), any(), any(), isNull())
            }

            viewModel.dismissPhoneConfirm()
            assertNull(viewModel.uiState.value.phoneConfirm)
            assertEquals("9876543210", viewModel.uiState.value.contactInput)
            coVerify(exactly = 0) {
                socialInteractor.addFriendByContact(any(), any(), any(), isNull())
            }
        }

    @Test
    fun continue_with_another_dial_code_stores_that_code() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.setName("Sam")
            viewModel.setContactInput("9876543210")
            viewModel.submit({}, {})
            advanceUntilIdle()
            viewModel.setPhoneConfirmDial("+44", "🇬🇧")
            viewModel.continuePhoneConfirm({}, {})
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.phoneConfirm)
            assertEquals("+449876543210", viewModel.uiState.value.contactInput)
            coVerify {
                socialInteractor.addFriendByContact("me", "+449876543210", "Sam", null)
            }
        }

    @Test
    fun loadFriend_shows_e164_not_placeholder() =
        runTest(dispatcher) {
            coEvery { friendRepository.getByFriendUserId("friend-1") } returns
                friend("+919876543210@mobile.splitease.com", friendUserId = "friend-1")
            val viewModel =
                viewModel(SavedStateHandle(mapOf("friendUserId" to "friend-1")))
            advanceUntilIdle()

            assertEquals(EditContactMode.SINGLE_FIELD, viewModel.uiState.value.mode)
            assertEquals("+919876543210", viewModel.uiState.value.contactInput)
            assertEquals("Sam", viewModel.uiState.value.name)
        }

    @Test
    fun device_contact_bootstrap_builds_the_same_options() =
        runTest(dispatcher) {
            every { deviceContacts.loadContactById("c1") } returns
                DeviceContact(
                    id = "c1",
                    displayName = "Sam",
                    phoneNumbers = listOf("5551234567"),
                    emails = listOf("sam@example.com"),
                )
            val viewModel = viewModel(SavedStateHandle(mapOf("contactId" to "c1")))
            advanceUntilIdle()
            Thread.sleep(50)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(EditContactMode.DEVICE_CONTACT, state.mode)
            assertEquals(
                listOf("phone_0", "email_0", "new_phone", "new_email"),
                state.options.map { it.id },
            )
            assertEquals(ContactMethodKind.EXISTING_PHONE, state.options[0].kind)
            assertEquals("5551234567", state.options[0].value)
            assertEquals(ContactMethodKind.EXISTING_EMAIL, state.options[1].kind)
            assertEquals("sam@example.com", state.options[1].value)
            assertEquals(ContactMethodKind.NEW_PHONE, state.options[2].kind)
            assertEquals(ContactMethodKind.NEW_EMAIL, state.options[3].kind)
            assertEquals("phone_0", state.selectedOptionId)
            assertEquals("Sam", state.name)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): EditContactViewModel =
        EditContactViewModel(
            savedStateHandle = handle,
            authRepository = authRepository,
            friendRepository = friendRepository,
            socialInteractor = socialInteractor,
            deviceContactsDataSource = deviceContacts,
            reviewStore = reviewStore,
            appContext = context,
        )

    private fun friend(email: String, friendUserId: String = "them"): Friend =
        Friend(
            id = "row",
            ownerUserId = "me",
            friendUserId = friendUserId,
            emailSnapshot = email,
            displayNameSnapshot = "Sam (invited)",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
}
