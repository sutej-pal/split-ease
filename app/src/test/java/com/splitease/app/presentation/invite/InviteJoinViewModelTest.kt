package com.splitease.app.presentation.invite

import android.content.Context
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.domain.model.InvitePreviewMember
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteJoinViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val socialInteractor: SocialInteractor = mockk(relaxed = true)
    private val settings: AppSettingsRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { settings.getPendingInviteToken() } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onPendingMemberSelected_stores_token_and_ignores_joined_or_blank() =
        runTest(dispatcher) {
            val viewModel =
                InviteJoinViewModel(
                    socialInteractor = socialInteractor,
                    appSettingsRepository = settings,
                    appContext = context,
                )
            advanceUntilIdle()

            var ready = false
            viewModel.onPendingMemberSelected(
                InvitePreviewMember("Sam", alreadyJoined = false, inviteToken = "sam-token"),
            ) { ready = true }
            advanceUntilIdle()
            assertTrue(ready)
            coVerify { settings.setPendingInviteToken("sam-token") }
            coVerify(exactly = 0) { socialInteractor.loadInvitePreview(any()) }

            ready = false
            viewModel.onPendingMemberSelected(
                InvitePreviewMember("Jo", alreadyJoined = true, inviteToken = "jo-token"),
            ) { ready = true }
            viewModel.onPendingMemberSelected(
                InvitePreviewMember("Guest", alreadyJoined = false, inviteToken = "  "),
            ) { ready = true }
            advanceUntilIdle()
            assertFalse(ready)
            coVerify(exactly = 0) { settings.setPendingInviteToken("jo-token") }
            coVerify(exactly = 0) { settings.setPendingInviteToken("  ") }
            coVerify(exactly = 0) { settings.setPendingInviteToken("") }
        }
}
