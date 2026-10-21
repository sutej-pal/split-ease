package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SecondaryEmail
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        every { context.getString(R.string.error_new_password_required) } returns "New password required"
        every { context.getString(R.string.error_invalid_otp) } returns "That code is invalid or expired."

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

            viewModel.toggleRow(AccountRow.EMAIL)
            assertEquals(AccountRow.EMAIL, viewModel.uiState.value.expandedRow)

            viewModel.toggleRow(AccountRow.PHONE)
            assertEquals(AccountRow.PHONE, viewModel.uiState.value.expandedRow)

            viewModel.toggleRow(AccountRow.PHONE)
            assertNull(viewModel.uiState.value.expandedRow)
        }

    @Test
    fun google_only_user_flag_hides_password_row_state() =
        runTest(dispatcher) {
            sessionFlow.value = AuthSession.SignedIn(
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
    fun blank_current_password_sets_password_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onCurrentPasswordDraftChange("")
            viewModel.onNewPasswordDraftChange("newpassword123")
            viewModel.updatePassword()
            advanceUntilIdle()

            assertEquals("Password required", viewModel.uiState.value.passwordError)
        }

    @Test
    fun blank_new_password_sets_password_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onCurrentPasswordDraftChange("currentpassword")
            viewModel.onNewPasswordDraftChange("")
            viewModel.updatePassword()
            advanceUntilIdle()

            assertEquals("New password required", viewModel.uiState.value.passwordError)
        }

    @Test
    fun add_secondary_email_handles_repository_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            coEvery { authRepository.addSecondaryEmail("taken@example.com", any()) } returns
                Result.failure(IllegalStateException("That email can't be added."))

            viewModel.onNewEmailDraftChange("taken@example.com")
            viewModel.onEmailPasswordDraftChange("password123")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("That email can't be added.", viewModel.uiState.value.emailError)
            assertNull(viewModel.uiState.value.emailPasswordError)
            assertFalse(viewModel.uiState.value.isEmailSaving)
        }

    @Test
    fun add_secondary_email_wrong_password_sets_email_password_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            coEvery { authRepository.addSecondaryEmail("test@example.com", any()) } returns
                Result.failure(IllegalArgumentException("That password isn't right."))

            viewModel.onNewEmailDraftChange("test@example.com")
            viewModel.onEmailPasswordDraftChange("wrongpass")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.emailError)
            assertEquals("That password isn't right.", viewModel.uiState.value.emailPasswordError)
            assertFalse(viewModel.uiState.value.isEmailSaving)
        }

    @Test
    fun unconfirmed_secondary_email_hides_otp_input_until_resend_and_expires_after_timeout() =
        runTest(dispatcher) {
            secondaryEmailsFlow.value =
                listOf(
                    SecondaryEmail(id = "sec-1", email = "pending@example.com", isVerified = false),
                )
            advanceUntilIdle()

            // Opening the screen should NOT show OTP field for existing unconfirmed email
            assertTrue(viewModel.uiState.value.activeSecondaryOtpIds.isEmpty())

            coEvery { authRepository.resendSecondaryEmail("sec-1") } returns Result.success(Unit)
            viewModel.resendSecondaryEmail("sec-1")
            runCurrent()

            assertTrue("sec-1" in viewModel.uiState.value.activeSecondaryOtpIds)
            viewModel.onSecondaryOtpDraftChange("sec-1", "123456")
            assertEquals("123456", viewModel.uiState.value.secondaryOtpDrafts["sec-1"])

            // Advance past the 10-minute OTP validity window
            advanceTimeBy(SignInAndContactViewModel.OTP_VALIDITY_TIMEOUT_MS)
            runCurrent()

            assertFalse("sec-1" in viewModel.uiState.value.activeSecondaryOtpIds)
            assertNull(viewModel.uiState.value.secondaryOtpDrafts["sec-1"])
        }

    @Test
    fun add_secondary_email_activates_otp_input_for_new_row() =
        runTest(dispatcher) {
            runCurrent()
            coEvery { authRepository.addSecondaryEmail("new@example.com", "password123") } coAnswers {
                secondaryEmailsFlow.value =
                    listOf(
                        SecondaryEmail(id = "sec-new", email = "new@example.com", isVerified = false),
                    )
                Result.success(Unit)
            }

            viewModel.onNewEmailDraftChange("new@example.com")
            viewModel.onEmailPasswordDraftChange("password123")
            viewModel.addSecondaryEmail()
            runCurrent()

            assertTrue("sec-new" in viewModel.uiState.value.activeSecondaryOtpIds)

            advanceTimeBy(SignInAndContactViewModel.OTP_VALIDITY_TIMEOUT_MS)
            runCurrent()

            assertFalse("sec-new" in viewModel.uiState.value.activeSecondaryOtpIds)
        }

    @Test
    fun verify_secondary_email_expired_error_hides_otp_input() =
        runTest(dispatcher) {
            secondaryEmailsFlow.value =
                listOf(
                    SecondaryEmail(id = "sec-1", email = "pending@example.com", isVerified = false),
                )
            runCurrent()

            coEvery { authRepository.resendSecondaryEmail("sec-1") } returns Result.success(Unit)
            viewModel.resendSecondaryEmail("sec-1")
            runCurrent()
            assertTrue("sec-1" in viewModel.uiState.value.activeSecondaryOtpIds)

            coEvery { authRepository.verifySecondaryEmail("sec-1", "123456") } returns
                Result.failure(IllegalStateException("Verification code has expired. Request a new code."))

            viewModel.onSecondaryOtpDraftChange("sec-1", "123456")
            viewModel.verifySecondaryEmail("sec-1")
            runCurrent()

            assertFalse("sec-1" in viewModel.uiState.value.activeSecondaryOtpIds)
            assertNull(viewModel.uiState.value.secondaryOtpDrafts["sec-1"])
            assertEquals("That code is invalid or expired.", viewModel.uiState.value.emailError)
        }

    @Test
    fun resend_primary_verification_shows_otp_input_and_expires_after_timeout() =
        runTest(dispatcher) {
            sessionFlow.value =
                AuthSession.SignedIn(
                    AuthUser("u1", "bob@example.com", "Bob", emailConfirmed = false, isGoogleOnly = false),
                )
            runCurrent()
            assertFalse(viewModel.uiState.value.showPrimaryOtpInput)

            coEvery { authRepository.resendSignupConfirmation("bob@example.com") } returns Result.success(Unit)
            viewModel.resendPrimaryVerification()
            runCurrent()

            assertTrue(viewModel.uiState.value.showPrimaryOtpInput)
            viewModel.onPrimaryOtpDraftChange("654321")
            assertEquals("654321", viewModel.uiState.value.primaryOtpDraft)

            advanceTimeBy(SignInAndContactViewModel.OTP_VALIDITY_TIMEOUT_MS)
            runCurrent()

            assertFalse(viewModel.uiState.value.showPrimaryOtpInput)
            assertEquals("", viewModel.uiState.value.primaryOtpDraft)
        }
}
