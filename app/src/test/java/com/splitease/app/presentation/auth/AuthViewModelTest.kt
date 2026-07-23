package com.splitease.app.presentation.auth

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var context: Context
    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { repository.observeSession() } returns sessionFlow
        every { context.getString(R.string.error_generic) } returns "Something went wrong. Try again."
        every { context.getString(R.string.verify_email_sent) } returns "Confirmation email sent."
        every { context.getString(R.string.verify_email_resent) } returns "Confirmation email resent."
        viewModel = AuthViewModel(repository, context)
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
    fun `sendPasswordReset shows success message`() =
        runTest {
            coEvery { repository.sendPasswordReset(any()) } returns Result.success(Unit)
            viewModel.sendPasswordReset("a@b.com", "Check your email")
            advanceUntilIdle()
            assertEquals("Check your email", viewModel.formState.value.infoMessage)
        }

    @Test
    fun `signUp pending confirmation exposes email`() =
        runTest {
            coEvery { repository.signUp(any(), any(), any()) } returns
                Result.success(SignUpResult.PendingEmailConfirmation("a@b.com"))
            viewModel.signUp("a@b.com", "secret1", "Ada")
            advanceUntilIdle()
            assertEquals("a@b.com", viewModel.formState.value.pendingConfirmationEmail)
            assertEquals("Confirmation email sent.", viewModel.formState.value.infoMessage)
        }
}
