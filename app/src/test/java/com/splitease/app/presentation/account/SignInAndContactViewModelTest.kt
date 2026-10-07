package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SecondaryEmail
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInAndContactViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow =
        MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(AuthUser("u1", "bob@example.com", "Bob", emailConfirmed = true, isGoogleOnly = false)),
        )
    private val secondaryEmailsFlow = MutableStateFlow<List<SecondaryEmail>>(emptyList())
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var context: Context
    private lateinit var viewModel: SignInAndContactViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { authRepository.observeSession() } returns sessionFlow
        every { authRepository.observeSecondaryEmails() } returns secondaryEmailsFlow
        every { userRepository.observeUsers() } returns flowOf(emptyList())

        every { context.getString(R.string.signup_error_phone_invalid) } returns "Invalid phone"
        every { context.getString(R.string.signup_error_password_short) } returns "Password short"
        every { context.getString(R.string.error_invalid_email) } returns "Invalid email"
        every { context.getString(R.string.error_password_required) } returns "Password required"
        every { context.getString(R.string.error_invalid_credentials) } returns "Invalid credentials"
        every { context.getString(R.string.error_session_expired) } returns "Your session expired. Sign in again."
        every { context.getString(R.string.error_generic) } returns "Something went wrong. Try again."
        every { context.getString(R.string.error_network) } returns "Network error"
        every { context.getString(R.string.verify_email_sent) } returns "Verification sent"
        every { context.getString(R.string.account_password_updated_toast) } returns "Password updated"

        viewModel =
            SignInAndContactViewModel(
                appContext = context,
                authRepository = authRepository,
                userRepository = userRepository,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggle_row_ensures_only_one_row_open_at_a_time() =
        runTest(dispatcher) {
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.expandedRow)

            viewModel.toggleRow(SignInContactRow.EMAIL)
            assertEquals(SignInContactRow.EMAIL, viewModel.uiState.value.expandedRow)

            viewModel.toggleRow(SignInContactRow.PHONE)
            assertEquals(SignInContactRow.PHONE, viewModel.uiState.value.expandedRow)

            viewModel.toggleRow(SignInContactRow.PHONE)
            assertNull(viewModel.uiState.value.expandedRow)
        }

    @Test
    fun google_only_user_flag_hides_password_requirement() =
        runTest(dispatcher) {
            sessionFlow.value =
                AuthSession.SignedIn(
                    AuthUser("u2", "google@example.com", "Google User", emailConfirmed = true, isGoogleOnly = true),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isGoogleOnly)
        }

    @Test
    fun invalid_phone_length_sets_phone_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onPhoneNumberDraftChange("123")
            viewModel.savePhone()
            advanceUntilIdle()

            assertEquals("Invalid phone", viewModel.uiState.value.phoneError)
            assertFalse(viewModel.uiState.value.isPhoneSaving)
        }

    @Test
    fun short_new_password_sets_password_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onCurrentPasswordDraftChange("secret123")
            viewModel.onNewPasswordDraftChange("short")
            viewModel.updatePassword()
            advanceUntilIdle()

            assertEquals("Password short", viewModel.uiState.value.passwordError)
        }

    @Test
    fun invalid_secondary_email_sets_email_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onNewEmailDraftChange("notanemail")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("Invalid email", viewModel.uiState.value.emailError)
        }

    @Test
    fun password_required_for_non_google_secondary_email() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onNewEmailDraftChange("second@example.com")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("Password required", viewModel.uiState.value.emailError)
            coVerify(exactly = 0) { authRepository.addSecondaryEmail(any(), any()) }
        }

    @Test
    fun google_only_add_secondary_email_skips_password() =
        runTest(dispatcher) {
            sessionFlow.value =
                AuthSession.SignedIn(
                    AuthUser("u2", "google@example.com", "Google User", emailConfirmed = true, isGoogleOnly = true),
                )
            coEvery { authRepository.addSecondaryEmail("second@example.com", null) } returns Result.success(Unit)
            advanceUntilIdle()

            viewModel.onNewEmailDraftChange("second@example.com")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            coVerify(exactly = 1) { authRepository.addSecondaryEmail("second@example.com", null) }
            assertEquals("Verification sent", viewModel.uiState.value.infoMessage)
            assertNull(viewModel.uiState.value.emailError)
        }

    @Test
    fun unauthorized_secondary_email_maps_to_session_expired() =
        runTest(dispatcher) {
            coEvery {
                authRepository.addSecondaryEmail("second@example.com", "secret123")
            } returns Result.failure(IllegalStateException("Unauthorized"))
            advanceUntilIdle()

            viewModel.onNewEmailDraftChange("second@example.com")
            viewModel.onEmailPasswordDraftChange("secret123")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("Your session expired. Sign in again.", viewModel.uiState.value.emailError)
        }

    @Test
    fun edge_function_user_message_is_surfaced() =
        runTest(dispatcher) {
            coEvery {
                authRepository.addSecondaryEmail("second@example.com", "secret123")
            } returns Result.failure(IllegalStateException("Too many email requests. Try again later."))
            advanceUntilIdle()

            viewModel.onNewEmailDraftChange("second@example.com")
            viewModel.onEmailPasswordDraftChange("secret123")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("Too many email requests. Try again later.", viewModel.uiState.value.emailError)
        }
}
