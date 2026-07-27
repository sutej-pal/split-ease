package com.splitease.app.presentation.onboarding

import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var mailRepository: MailRepository
    private lateinit var appSettings: AppSettingsRepository
    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mailRepository = mockk(relaxed = true)
        appSettings = mockk(relaxed = true)
        viewModel = OnboardingViewModel(mailRepository, appSettings)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onSignedInWelcome sends mail once and marks sent`() =
        runTest {
            coEvery { appSettings.getOnboardingEmailSent("user-1") } returns false
            coEvery {
                mailRepository.sendOnboardingStartedEmail(
                    toEmail = "a@b.com",
                    displayName = "Ada",
                )
            } returns Result.success(Unit)

            viewModel.onSignedInWelcome(
                userId = "user-1",
                email = "a@b.com",
                displayName = "Ada",
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { appSettings.getOnboardingEmailSent("user-1") }
            coVerify(exactly = 1) { mailRepository.sendOnboardingStartedEmail("a@b.com", "Ada") }
            coVerify(exactly = 1) { appSettings.setOnboardingEmailSent("user-1", true) }
        }

    @Test
    fun `onSignedInWelcome skips when already sent`() =
        runTest {
            coEvery { appSettings.getOnboardingEmailSent("user-1") } returns true

            viewModel.onSignedInWelcome(
                userId = "user-1",
                email = "a@b.com",
                displayName = "Ada",
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mailRepository.sendOnboardingStartedEmail(any(), any()) }
            coVerify(exactly = 0) { appSettings.setOnboardingEmailSent(any(), any()) }
        }

    @Test
    fun `onSignedInWelcome does not mark sent when mail fails`() =
        runTest {
            coEvery { appSettings.getOnboardingEmailSent("user-1") } returns false
            coEvery {
                mailRepository.sendOnboardingStartedEmail(any(), any())
            } returns Result.failure(IllegalStateException("smtp down"))

            viewModel.onSignedInWelcome(
                userId = "user-1",
                email = "a@b.com",
                displayName = "Ada",
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { mailRepository.sendOnboardingStartedEmail("a@b.com", "Ada") }
            coVerify(exactly = 0) { appSettings.setOnboardingEmailSent(any(), any()) }
        }
}
