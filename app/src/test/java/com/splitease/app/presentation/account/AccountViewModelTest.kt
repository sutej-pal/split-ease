package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppLocale
import com.splitease.app.domain.settings.AppSettingsRepository
import io.mockk.coEvery
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow =
        MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(AuthUser("u1", "bob@example.com", "Bob", emailConfirmed = true, isGoogleOnly = false)),
        )
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var context: Context
    private lateinit var viewModel: AccountViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { authRepository.observeSession() } returns sessionFlow
        every { userRepository.observeUsers() } returns flowOf(emptyList())
        every { appSettingsRepository.observeCurrencyCode() } returns flowOf("INR")
        every { appSettingsRepository.observeAppLocale() } returns flowOf(AppLocale.DEFAULT)
        every { context.getString(R.string.msg_display_name_required) } returns "Enter a display name."
        every { context.getString(R.string.msg_profile_name_saved) } returns "Display name updated."

        viewModel =
            AccountViewModel(
                appContext = context,
                authRepository = authRepository,
                userRepository = userRepository,
                appSettingsRepository = appSettingsRepository,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blank_display_name_sets_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onDisplayNameDraftChange("   ")
            viewModel.saveDisplayName()
            advanceUntilIdle()

            assertEquals("Enter a display name.", viewModel.settings.value.errorMessage)
        }

    @Test
    fun save_display_name_success_sets_info() =
        runTest(dispatcher) {
            coEvery { authRepository.updateDisplayName("Alice") } returns Result.success(Unit)
            advanceUntilIdle()
            viewModel.onDisplayNameDraftChange("Alice")
            viewModel.saveDisplayName()
            advanceUntilIdle()

            assertEquals("Display name updated.", viewModel.settings.value.infoMessage)
            assertNull(viewModel.settings.value.errorMessage)
        }

}
