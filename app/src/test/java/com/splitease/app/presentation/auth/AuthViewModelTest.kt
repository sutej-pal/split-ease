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
        coEvery { appSettings.setCurrencyCode(any()) } returns Unit
        viewModel = AuthViewModel(repository, appSettings, context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signIn success clears loading and leaves no error`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            assertFalse(viewModel.formState.value.isLoading)
            assertNull(viewModel.formState.value.errorMessage)
            coVerify(exactly = 1) { repository.signIn("a@b.com", "secret1") }
        }

    @Test
    fun `signIn failure surfaces error message`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns
                Result.failure(IllegalStateException("Invalid login"))
            viewModel.signIn("a@b.com", "bad")
            advanceUntilIdle()
            assertEquals("Invalid login", viewModel.formState.value.errorMessage)
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
            assertEquals(
                "Account created. Check your email for a verification code.",
                viewModel.formState.value.infoMessage,
            )
        }

    @Test
    fun `signUp skips OTP gate when repository returns SignedIn`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.SignedIn)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(
                "Signup complete. You can continue to the app.",
                viewModel.formState.value.infoMessage,
            )
        }

    @Test
    fun `verifySignupOtp rejects wrong length code`() =
        runTest {
            viewModel.verifySignupOtp("a@b.com", "9999")
            advanceUntilIdle()
            assertEquals("Enter a valid 6-digit code.", viewModel.formState.value.errorMessage)
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
        }

    @Test
    fun `verifySignupOtp rejects 8-digit code`() =
        runTest {
            viewModel.verifySignupOtp("a@b.com", "12345678")
            advanceUntilIdle()
            assertEquals("Enter a valid 6-digit code.", viewModel.formState.value.errorMessage)
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
        }

    @Test
    fun `verifySignupOtp with valid 6-digit code calls repository and clears pending`() =
        runTest {
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.PendingEmailConfirmation("a@b.com"))
            coEvery { repository.verifySignupOtp(any(), any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
            advanceUntilIdle()
            viewModel.verifySignupOtp("a@b.com", "123456")
            advanceUntilIdle()
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            coVerify(exactly = 1) { repository.verifySignupOtp("a@b.com", "123456") }
        }

    @Test
    fun `resendConfirmation calls repository resend`() =
        runTest {
            coEvery { repository.resendSignupConfirmation(any()) } returns Result.success(Unit)
            viewModel.resendConfirmation("a@b.com")
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.resendSignupConfirmation("a@b.com") }
        }
}
