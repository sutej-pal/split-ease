package com.splitease.app.presentation.auth

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
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
    private lateinit var resources: Resources
    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        appSettings = mockk(relaxed = true)
        context = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        every { context.resources } returns resources
        every { context.getString(any(), *anyVararg()) } answers {
            val id = invocation.args[0] as Int
            val formatArgs =
                invocation.args
                    .drop(1)
                    .flatMap { arg ->
                        when (arg) {
                            is Array<*> -> arg.asList()
                            else -> listOf(arg)
                        }
                    }.toTypedArray()
            if (formatArgs.isEmpty()) {
                authString(id)
            } else {
                formatAuthString(id, formatArgs)
            }
        }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } answers {
            val quantity = invocation.args[1] as Int
            val minutes =
                (invocation.args.getOrNull(2) as? Number)?.toInt() ?: quantity
            if (quantity == 1) {
                "Try again in $minutes minute."
            } else {
                "Try again in $minutes minutes."
            }
        }
        every { repository.observeSession() } returns sessionFlow
        every { appSettings.observePendingInviteToken() } returns flowOf(null)
        coEvery { appSettings.setCurrencyCode(any()) } returns Unit
        coEvery { repository.isEmailRegistered(any()) } returns Result.success(false)
        coEvery { repository.isPhoneRegistered(any(), any()) } returns Result.success(false)
        viewModel = AuthViewModel(repository, appSettings, context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun msg(@StringRes id: Int): String = context.getString(id)

    @Test
    fun `signIn success opens app without login OTP`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns Result.success(Unit)
            coEvery { repository.ensureLocalProfile() } returns Result.success(Unit)
            viewModel.signIn("a@b.com", "secret1")
            advanceUntilIdle()
            assertFalse(viewModel.formState.value.isLoading)
            assertFalse(viewModel.formState.value.holdSignedInForOtp)
            assertNull(viewModel.formState.value.errorMessage)
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            assertNull(viewModel.formState.value.pendingOtpPurpose)
            assertNull(viewModel.formState.value.infoMessage)
            coVerify(exactly = 1) { repository.signIn("a@b.com", "secret1") }
            coVerify(exactly = 1) { repository.ensureLocalProfile() }
            coVerify(exactly = 0) { repository.signOut() }
            coVerify(exactly = 0) { repository.sendLoginOtp(any()) }
        }

    @Test
    fun `signIn blank fields skips api and asks to fill values`() =
        runTest {
            viewModel.signIn("", "")
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.LOGIN_FIELDS_REQUIRED),
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) { repository.signIn(any(), any()) }
        }

    @Test
    fun `signIn blank password skips api`() =
        runTest {
            viewModel.signIn("a@b.com", "  ")
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.LOGIN_FIELDS_REQUIRED),
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) { repository.signIn(any(), any()) }
        }

    @Test
    fun `signIn failure does not send OTP`() =
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
            coVerify(exactly = 0) { repository.ensureLocalProfile() }
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
                msg(AuthMessages.NOT_REGISTERED),
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
                msg(AuthMessages.INVALID_CREDENTIALS),
                viewModel.formState.value.errorMessage,
            )
        }

    @Test
    fun `signIn locks after repeated invalid credentials`() =
        runTest {
            coEvery { repository.signIn(any(), any()) } returns
                Result.failure(IllegalStateException("Invalid login credentials"))
            coEvery { repository.isEmailRegistered("a@b.com") } returns Result.success(true)
            repeat(AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                viewModel.signIn("a@b.com", "bad")
                advanceUntilIdle()
            }
            assertTrue(
                viewModel.formState.value.errorMessage?.startsWith("Too many login attempts") == true,
            )
            coVerify(exactly = AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                repository.signIn("a@b.com", "bad")
            }
            // Further taps must not hit the API while locked.
            viewModel.signIn("a@b.com", "bad")
            advanceUntilIdle()
            coVerify(exactly = AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                repository.signIn("a@b.com", "bad")
            }
            assertTrue(
                viewModel.formState.value.errorMessage?.startsWith("Too many login attempts") == true,
            )
        }

    @Test
    fun `signUp locks after repeated already-registered email`() =
        runTest {
            coEvery { repository.isEmailRegistered("a@b.com") } returns Result.success(true)
            repeat(AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                viewModel.signUp(
                    email = "a@b.com",
                    password = "Secret12",
                    displayName = "Alex",
                )
                advanceUntilIdle()
            }
            assertTrue(
                viewModel.formState.value.errorMessage?.startsWith("Too many sign-up attempts") == true,
            )
            viewModel.signUp(
                email = "a@b.com",
                password = "Secret12",
                displayName = "Alex",
            )
            advanceUntilIdle()
            coVerify(exactly = AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                repository.isEmailRegistered("a@b.com")
            }
        }

    @Test
    fun `requestPasswordReset locks after repeated requests`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            repeat(AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                viewModel.requestPasswordReset("a@b.com")
                advanceUntilIdle()
            }
            assertTrue(
                viewModel.formState.value.errorMessage
                    ?.startsWith("Too many password-reset requests") == true,
            )
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            coVerify(exactly = AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                repository.requestPasswordReset("a@b.com")
            }
        }

    @Test
    fun `completePasswordReset locks after repeated invalid OTP`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            coEvery { repository.verifyRecoveryOtp(any(), any()) } returns
                Result.failure(IllegalStateException("Token has expired or is invalid"))
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            repeat(AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                viewModel.completePasswordReset(
                    email = "a@b.com",
                    token = "000000",
                    newPassword = "Secret12",
                    confirmPassword = "Secret12",
                )
                advanceUntilIdle()
            }
            assertTrue(
                viewModel.formState.value.errorMessage
                    ?.startsWith("Too many password-reset attempts") == true,
            )
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "000000",
                newPassword = "Secret12",
                confirmPassword = "Secret12",
            )
            advanceUntilIdle()
            coVerify(exactly = AuthRateLimiter.DEFAULT_MAX_ATTEMPTS) {
                repository.verifyRecoveryOtp("a@b.com", "000000")
            }
        }

    @Test
    fun `requestPasswordReset opens recovery OTP gate`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.RECOVERY, viewModel.formState.value.pendingOtpPurpose)
            assertEquals(
                "If an account exists for a@b.com, we've sent a code.",
                viewModel.formState.value.infoMessage,
            )
            assertFalse(viewModel.formState.value.recoveryOtpVerified)
        }

    @Test
    fun `requestPasswordReset always opens gate even when repository soft-fails`() =
        runTest {
            // Unknown addresses still succeed from Supabase; ViewModel always opens the gate.
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("missing@example.com")
            advanceUntilIdle()
            assertEquals("missing@example.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.RECOVERY, viewModel.formState.value.pendingOtpPurpose)
            assertNull(viewModel.formState.value.errorMessage)
        }

    @Test
    fun `requestPasswordReset surfaces rate limit but still opens gate`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns
                Result.failure(RuntimeException("over_email_send_rate_limit"))
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals(PendingOtpPurpose.RECOVERY, viewModel.formState.value.pendingOtpPurpose)
            assertEquals(
                msg(AuthMessages.EMAIL_RATE_LIMITED),
                viewModel.formState.value.errorMessage,
            )
            assertNull(viewModel.formState.value.infoMessage)
        }

    @Test
    fun `completePasswordReset verifies OTP then updates password`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            coEvery { repository.verifyRecoveryOtp(any(), any()) } returns Result.success(Unit)
            coEvery { repository.updatePassword(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "123456",
                newPassword = "Secret12",
                confirmPassword = "Secret12",
            )
            advanceUntilIdle()
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
            assertNull(viewModel.formState.value.pendingOtpPurpose)
            assertEquals(msg(AuthMessages.RESET_PASSWORD_SUCCESS), viewModel.formState.value.infoMessage)
            coVerify(exactly = 1) { repository.verifyRecoveryOtp("a@b.com", "123456") }
            coVerify(exactly = 1) { repository.updatePassword("Secret12") }
        }

    @Test
    fun `completePasswordReset shows generic invalid code on OTP failure`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            coEvery { repository.verifyRecoveryOtp(any(), any()) } returns
                Result.failure(IllegalStateException("Token has expired or is invalid"))
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "000000",
                newPassword = "Secret12",
                confirmPassword = "Secret12",
            )
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.RESET_OTP_INVALID_OR_EXPIRED),
                viewModel.formState.value.errorMessage,
            )
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            coVerify(exactly = 0) { repository.updatePassword(any()) }
        }

    @Test
    fun `completePasswordReset rejects mismatched passwords`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "123456",
                newPassword = "Secret12",
                confirmPassword = "Secret99",
            )
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.RESET_PASSWORD_MISMATCH),
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) { repository.verifyRecoveryOtp(any(), any()) }
            coVerify(exactly = 0) { repository.updatePassword(any()) }
        }

    @Test
    fun `completePasswordReset rejects weak password rules`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "123456",
                newPassword = "secret12",
                confirmPassword = "secret12",
            )
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.RESET_PASSWORD_REQUIREMENTS),
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) { repository.verifyRecoveryOtp(any(), any()) }
        }

    @Test
    fun `completePasswordReset retries password only after OTP verified`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            coEvery { repository.verifyRecoveryOtp(any(), any()) } returns Result.success(Unit)
            coEvery { repository.updatePassword("Badpass1") } returns
                Result.failure(IllegalStateException("weak"))
            coEvery { repository.updatePassword("Secret12") } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "123456",
                newPassword = "Badpass1",
                confirmPassword = "Badpass1",
            )
            advanceUntilIdle()
            assertTrue(viewModel.formState.value.recoveryOtpVerified)
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            viewModel.completePasswordReset(
                email = "a@b.com",
                token = "",
                newPassword = "Secret12",
                confirmPassword = "Secret12",
            )
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.verifyRecoveryOtp("a@b.com", "123456") }
            coVerify(exactly = 1) { repository.updatePassword("Secret12") }
            assertNull(viewModel.formState.value.pendingConfirmationEmail)
        }

    @Test
    fun `resendConfirmation calls requestPasswordReset when purpose is recovery`() =
        runTest {
            coEvery { repository.requestPasswordReset(any()) } returns Result.success(Unit)
            viewModel.requestPasswordReset("a@b.com")
            advanceUntilIdle()
            viewModel.resendConfirmation("a@b.com")
            advanceUntilIdle()
            coVerify(atLeast = 2) { repository.requestPasswordReset("a@b.com") }
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
                msg(AuthMessages.VERIFY_EMAIL_SENT),
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
                msg(AuthMessages.EMAIL_ALREADY_REGISTERED),
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
                msg(AuthMessages.PHONE_ALREADY_REGISTERED),
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
                msg(AuthMessages.VERIFY_EMAIL_SENT),
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
            assertEquals(
                msg(AuthMessages.VERIFY_EMAIL_INVALID_CODE),
                viewModel.formState.value.errorMessage,
            )
            coVerify(exactly = 0) { repository.verifySignupOtp(any(), any()) }
            coVerify(exactly = 0) { repository.verifyLoginOtp(any(), any()) }
        }

    @Test
    fun `verifyPendingOtp rejects 8-digit code`() =
        runTest {
            viewModel.verifyPendingOtp("a@b.com", "12345678")
            advanceUntilIdle()
            assertEquals(
                msg(AuthMessages.VERIFY_EMAIL_INVALID_CODE),
                viewModel.formState.value.errorMessage,
            )
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
            // Autoconfirm signup still uses the LOGIN OTP gate.
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.SignedIn)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            coEvery { repository.verifyLoginOtp(any(), any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
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
            coEvery {
                repository.signUp(any(), any(), any(), any(), any(), any(), anyNullable())
            } returns Result.success(SignUpResult.SignedIn)
            coEvery { repository.signOut() } returns Result.success(Unit)
            coEvery { repository.sendLoginOtp(any()) } returns Result.success(Unit)
            viewModel.signUp("a@b.com", "secret12", "Ada")
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

    private companion object {
        fun authString(@StringRes id: Int): String =
            AUTH_STRINGS[id] ?: error("Unmapped auth string id=$id")

        fun formatAuthString(@StringRes id: Int, args: Array<out Any?>): String {
            var result = authString(id)
            args.forEachIndexed { index, arg ->
                val value = arg?.toString().orEmpty()
                val n = index + 1
                result = result.replace("%${n}\$s", value).replace("%${n}\$d", value)
            }
            return result
        }

        private val AUTH_STRINGS =
            mapOf(
                R.string.error_generic to "Something went wrong. Try again.",
                R.string.error_login_fields_required to "Enter your email and password.",
                R.string.error_invalid_credentials to "Invalid email or password. Try again.",
                R.string.error_not_registered to "You're not registered with us. Please sign up.",
                R.string.signup_error_name_required to "Enter your full name.",
                R.string.signup_error_password_short to "Password must be at least 8 characters.",
                R.string.error_email_already_registered to "This email is already registered. Please log in.",
                R.string.error_phone_already_registered to "This phone number is already registered. Please log in.",
                R.string.error_already_registered to "You're already registered with us. Please log in.",
                R.string.error_signup_email_delivery to "We couldn't send the verification email. Please try again in a moment.",
                R.string.error_signup_email_rate_limit to "Too many verification emails were sent. Wait a few minutes and try again.",
                R.string.error_invalid_email to "Enter a valid email address.",
                R.string.verify_email_sent to "Account created. Check your email for a verification code.",
                R.string.verify_email_resent to "Verification code resent. Check your inbox.",
                R.string.verify_email_invalid_code to "Enter a valid 6-digit code.",
                R.string.reset_otp_invalid_or_expired to "Invalid or expired code.",
                R.string.reset_password_mismatch to "Passwords do not match.",
                R.string.reset_password_success to "Password updated.",
                R.string.reset_password_same_as_old to "Choose a different password than your current one.",
                R.string.reset_password_session_expired to "Your reset session expired. Request a new code and try again.",
                R.string.reset_password_requirements to "Password must include 8+ characters, upper & lowercase letters, and a number.",
                R.string.reset_otp_sent to "If an account exists for %1\$s, we've sent a code.",
                R.string.error_auth_rate_login to "Too many login attempts. %1\$s",
                R.string.error_auth_rate_signup to "Too many sign-up attempts. %1\$s",
                R.string.error_auth_rate_forgot_password to "Too many password-reset requests. %1\$s",
                R.string.error_auth_rate_reset_password to "Too many password-reset attempts. %1\$s",
            )
    }
}