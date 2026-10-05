package com.splitease.app.presentation.account

import android.content.Context
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SecondaryEmail
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow =
        MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(AuthUser("u1", "bob@example.com", "Bob", emailConfirmed = true, isGoogleOnly = false)),
        )
    private val secondaryEmailsFlow = MutableStateFlow<List<SecondaryEmail>>(emptyList())
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
        every { authRepository.observeSecondaryEmails() } returns secondaryEmailsFlow
        every { userRepository.observeUsers() } returns flowOf(emptyList())
        every { appSettingsRepository.observeCurrencyCode() } returns flowOf("INR")
        every { appSettingsRepository.observeAppLocale() } returns flowOf(AppLocale.DEFAULT)
        every { appSettingsRepository.observeTimeZone() } returns flowOf("Asia/Kolkata")
        every { appSettingsRepository.observeAllowFriendSuggestions() } returns flowOf(true)

        every { context.getString(R.string.signup_error_phone_invalid) } returns "Invalid phone"
        every { context.getString(R.string.signup_error_password_short) } returns "Password short"
        every { context.getString(R.string.error_invalid_email) } returns "Invalid email"
        every { context.getString(R.string.error_password_required) } returns "Password required"

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
    fun toggle_row_ensures_only_one_row_open_at_a_time() =
        runTest(dispatcher) {
            advanceUntilIdle()
            assertNull(viewModel.settings.value.expandedRow)

            viewModel.toggleRow(AccountRow.EMAIL)
            assertEquals(AccountRow.EMAIL, viewModel.settings.value.expandedRow)

            viewModel.toggleRow(AccountRow.PHONE)
            assertEquals(AccountRow.PHONE, viewModel.settings.value.expandedRow)

            viewModel.toggleRow(AccountRow.PHONE)
            assertNull(viewModel.settings.value.expandedRow)
        }

    @Test
    fun google_only_user_flag_hides_password_row_state() =
        runTest(dispatcher) {
            sessionFlow.value = AuthSession.SignedIn(
                AuthUser("u2", "google@example.com", "Google User", emailConfirmed = true, isGoogleOnly = true),
            )
            advanceUntilIdle()
            assertTrue(viewModel.settings.value.isGoogleOnly)
        }

    @Test
    fun invalid_phone_length_sets_phone_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onPhoneNumberDraftChange("123")
            viewModel.savePhone()
            advanceUntilIdle()

            assertEquals("Invalid phone", viewModel.settings.value.phoneError)
            assertFalse(viewModel.settings.value.isPhoneSaving)
        }

    @Test
    fun short_new_password_sets_password_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onCurrentPasswordDraftChange("secret123")
            viewModel.onNewPasswordDraftChange("short")
            viewModel.updatePassword()
            advanceUntilIdle()

            assertEquals("Password short", viewModel.settings.value.passwordError)
        }

    @Test
    fun invalid_secondary_email_sets_email_error() =
        runTest(dispatcher) {
            advanceUntilIdle()
            viewModel.onNewEmailDraftChange("notanemail")
            viewModel.addSecondaryEmail()
            advanceUntilIdle()

            assertEquals("Invalid email", viewModel.settings.value.emailError)
        }
}
