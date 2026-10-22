package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class CloseAccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var context: Context
    private lateinit var viewModel: CloseAccountViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(R.string.account_deactivate_error_offline) } returns "offline"
        every { context.getString(R.string.error_generic) } returns "Something went wrong. Try again."
        viewModel =
            CloseAccountViewModel(
                appContext = context,
                authRepository = authRepository,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deactivate_success_calls_repo_once_and_clears_busy() =
        runTest(dispatcher) {
            coEvery { authRepository.deactivateOwnAccount() } returns Result.success(Unit)
            viewModel.deactivate()
            advanceUntilIdle()
            coVerify(exactly = 1) { authRepository.deactivateOwnAccount() }
            assertFalse(viewModel.uiState.value.isDeactivating)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun deactivate_unknown_host_exception_gives_offline_message() =
        runTest(dispatcher) {
            coEvery { authRepository.deactivateOwnAccount() } returns
                Result.failure(UnknownHostException("Unable to resolve host"))
            viewModel.deactivate()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isDeactivating)
            assertEquals("offline", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun deactivate_unexpected_failure_gives_generic_message_and_clear_error_resets_it() =
        runTest(dispatcher) {
            coEvery { authRepository.deactivateOwnAccount() } returns
                Result.failure(IllegalStateException("Unexpected error"))
            viewModel.deactivate()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isDeactivating)
            assertEquals("Something went wrong. Try again.", viewModel.uiState.value.errorMessage)

            viewModel.clearError()
            assertNull(viewModel.uiState.value.errorMessage)
        }
}
