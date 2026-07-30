package com.splitease.app.presentation.auth

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppSettingsRepository
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
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow = MutableStateFlow<AuthSession>(AuthSession.SignedOut)
    private lateinit var repository: AuthRepository
    private lateinit var appSettings: AppSettingsRepository
    private lateinit var context: Context
    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        appSettings = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { repository.observeSession() } returns sessionFlow
        every { appSettings.observePendingInviteToken() } returns flowOf(null)
        every { context.getString(R.string.error_generic) } returns "Something went wrong. Try again."
        every { context.getString(R.string.error_invalid_credentials) } returns
            "Invalid email or password. Try again."
        every { context.getString(R.string.error_not_registered) } returns
            "You're not registered with us. Please sign up."
        every { context.getString(R.string.verify_email_sent) } returns
            "Account created. Check your email for a verification code."
        every { context.getString(R.string.verify_login_otp_sent) } returns
            "Check your email for a verification code to finish signing in."
        every { context.getString(R.string.verify_email_resent) } returns
            "Verification code resent. Check your inbox."
        every { context.getString(R.string.verify_email_invalid_code) } returns
            "Enter a valid 6-digit code."
        every { context.getString(R.string.signup_complete_message) } returns
            "Signup complete. You can continue to the app."
        every { context.getString(R.string.signup_error_name_required) } returns
            "Enter your full name."
        every { context.getString(R.string.signup_error_password_short) } returns
            "Password must be at least 8 characters."
        every { context.getString(R.string.error_email_already_registered) } returns
            "This email is already registered. Please log in."
        every { context.getString(R.string.error_phone_already_registered) } returns
            "This phone number is already registered. Please log in."
        every { context.getString(R.string.error_already_registered) } returns
            "You're already registered with us. Please log in."
        coEvery { appSettings.setCurrencyCode(any()) } returns Unit
        coEvery { repository.isEmailRegistered(any()) } returns Result.success(false)
        coEvery { repository.isPhoneRegistered(any(), any()) } returns Result.success(false)
        viewModel = AuthViewModel(repository, appSettings, context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signIn success gates on login OTP and signs out password session`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns Result.success(Unit)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            assertFalse(viewModel.formState.value.isLoading)
            assertFalse(viewModel.formState.value.holdSignedInForOtp)
            assertNull(viewModel.formState.value.errorMessage)
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.LOGIN, viewModel.formState.value.pendingOtpPurpose)
            assertEquals(
                "Check your email for a verification code to finish signing in.",
                viewModel.formState.value.infoMessage,
            )
            coVerify(exactly = 1) { repository.signIn("a@b.com", "secret1") }
            coVerify(exactly = 1) { repository.signOut() }
            coVerify(exactly = 1) { repository.sendLoginOtp("a@b.com") }
        }

    @Test
    fun `signIn arms hold before password auth completes`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } coAnswers {
                assertTrue(viewModel.formState.value.holdSignedInForOtp)
                Result.success(Unit)
            }
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            assertFalse(viewModel.formState.value.holdSignedInForOtp)
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
        }

    @Test
    fun `signIn failure clears hold and does not send OTP`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns
                Result.failure(IllegalStateException("Invalid login"))
            coEvery { repository.signOut() } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "bad")
            advanceUntilIdle()
            assertEquals("Invalid login", viewModel.formState.value.errorMessage)
            assertFalse(viewModel.formState.value.holdSignedInForOtp)
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            coVerify(exactly = 0) { repository.sendLoginOtp(any()) }
        }

    @Test
    fun `signIn unknown email shows not registered message`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns
                Result.failure(IllegalStateException("invalid_credentials"))
            coEvery { repository.isEmailRegistered("new@b.com") } returns Result.success(false)
            viewModel.signIn("new@b.com", "secret1")
            advanceUntilIdle()
            assertEquals(
                "You're not registered with us. Please sign up.",
                viewModel.formState.value.errorMessage,
            )
        }

    @Test
    fun `signIn wrong password shows invalid credentials`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns
                Result.failure(IllegalStateException("Invalid login credentials"))
            coEvery { repository.isEmailRegistered("a@b.com") } returns Result.success(true)
            viewModel.signIn("a@b.com", "bad")
            advanceUntilIdle()
            assertEquals(
                "Invalid email or password. Try again.",
                viewModel.formState.value.errorMessage,
            )
        }

    @Test
    fun `sendPasswordReset shows success message`() =
        runTest {
            coEvery { repository.sendPasswordReset(any()) } returns Result.success(Unit)
            viewModel.sendPasswordReset("a@b.com", "Check your email")
            advanceUntilIdle()
            assertEquals("Check your email", viewModel.formState.value.infoMessage)
        }

    @Test
    fun `signUp gates on OTP when confirmation is pending`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.PendingEmailConfirmation("a@b.com"))
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.SIGNUP, viewModel.formState.value.pendingOtpPurpose)
            assertEquals(
                "Account created. Check your email for a verification code.",
                viewModel.formState.value.infoMessage,
            )
        }

    @Test
    fun `signUp shows already registered when email exists`() =
        runTest {
            coEvery { repository.isEmailRegistered("a@b.com") } returns Result.success(true)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            assertEquals(
                "This email is already registered. Please log in.",
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            }
        }

    @Test
    fun `signUp shows already registered when phone exists`() =
        runTest {
            coEvery { repository.isPhoneRegistered("+91", "9876543210") } returns
                Result.success(true)
            viewModel.signUp(
                email = "new@b.com",
                password = "secret12",
                displayName = "Ada",
                phoneCountryCode = "+91",
                phoneNumber = "9876543210",
            )
            advanceUntilIdle()
            assertEquals(
                "This phone number is already registered. Please log in.",
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            }
        }

    @Test
    fun `signUp with SignedIn still gates on login OTP and does not open app`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.SignedIn)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.LOGIN, viewModel.formState.value.pendingOtpPurpose)
            assertFalse(viewModel.formState.value.holdSignedInForOtp)
            assertEquals(
                "Account created. Check your email for a verification code.",
                viewModel.formState.value.infoMessage,
            )
            coVerify(exactly = 1) { repository.signOut() }
            coVerify(exactly = 1) { repository.sendLoginOtp("a@b.com") }
        }

    @Test
    fun `verifyPendingOtp rejects wrong length code`() =
        runTest {
            viewModel.verifyPendingOtp("a@b.com", "9999")
            advanceUntilIdle()
            assertEquals("Enter a valid 6-digit code.", viewModel.formState.value.errorMessage)
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
            coVerify(exactly = 0) { repository.verifyLoginOtp(any(), any()) }
        }

    @Test
    fun `verifyPendingOtp rejects 8-digit code`() =
        runTest {
            viewModel.verifyPendingOtp("a@b.com", "12345678")
            advanceUntilIdle()
            assertEquals("Enter a valid 6-digit code.", viewModel.formState.value.errorMessage)
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
        }

    @Test
    fun `verifyPendingOtp with valid signup code calls repository and clears pending`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.PendingEmailConfirmation("a@b.com"))
            coEvery { repository.verifySignupOtp(any(), any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            viewModel.verifyPendingOtp("a@b.com", "123456")
            advanceUntilIdle()
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            assertNull(viewModel.formState.value.pendingOtpPurpose)
            coVerify(exactly = 1) { repository.verifySignupOtp("a@b.com", "123456") }
        }

    @Test
    fun `verifyPendingOtp with valid login code calls login verify`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns Result.success(Unit)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            coEvery { repository.verifyLoginOtp(any(), any()) } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            viewModel.verifyPendingOtp("a@b.com", "654321")
            advanceUntilIdle()
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            coVerify(exactly = 1) { repository.verifyLoginOtp("a@b.com", "654321") }
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
        }

    @Test
    fun `resendConfirmation calls login otp send when purpose is login`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns Result.success(Unit)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            viewModel.resendConfirmation("a@b.com")
            advanceUntilIdle()
            coVerify(atLeast = 2) { repository.sendLoginOtp("a@b.com") }
            coVerify(exactly = 0) { repository.resendSignupConfirmation(any()) }
        }

    @Test
    fun `resendConfirmation calls repository resend for signup`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.PendingEmailConfirmation("a@b.com"))
            coEvery { repository.resendSignupConfirmation(any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            viewModel.resendConfirmation("a@b.com")
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.resendSignupConfirmation("a@b.com") }
        }
}
