package com.splitease.app.presentation.pinboard

import android.content.Context
import android.util.Log
import com.splitease.app.R
import com.splitease.app.data.pinboard.PinBoardDto
import com.splitease.app.data.pinboard.PinBoardInteractor
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ProfileDto
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinBoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow = MutableStateFlow<AuthSession>(AuthSession.SignedOut)

    private lateinit var context: Context
    private lateinit var authRepository: AuthRepository
    private lateinit var interactor: PinBoardInteractor
    private lateinit var socialRemote: SocialRemoteDataSource
    private lateinit var groupRepository: GroupRepository
    private lateinit var viewModel: PinBoardViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        context = mockk(relaxed = true)
        every { context.getString(R.string.error_generic) } returns "Something went wrong."

        authRepository = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        socialRemote = mockk(relaxed = true)
        groupRepository = mockk(relaxed = true)

        every { authRepository.observeSession() } returns sessionFlow
        sessionFlow.value =
            AuthSession.SignedIn(
                AuthUser(userId = "u1", email = "a@b.com", displayName = "Ada"),
            )
        coEvery { groupRepository.getGroupById("g1") } returns testGroup()
        coEvery { socialRemote.fetchProfileById("u1") } returns profile("Ada")
        coEvery { socialRemote.fetchProfileById("u2") } returns profile("Bob")

        viewModel = PinBoardViewModel(context, authRepository, interactor, socialRemote, groupRepository)
    }

    @AfterEach
    fun tearDown() {
        viewModel.onCleared()
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun load_shows_cached_content_before_remote_completes() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns dto(content = "cached draft")
            coEvery { interactor.load("g1") } returns dto(content = "from server")

            viewModel.load("g1")
            advanceUntilIdle()

            assertEquals("from server", viewModel.uiState.value.content)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun load_failure_with_no_cache_retries_on_refresh_loop() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns null
            coEvery { interactor.load("g1") } throws RuntimeException("offline") andThen dto(content = "recovered")

            viewModel.load("g1")
            advanceUntilIdle()

            assertEquals("Something went wrong.", viewModel.uiState.value.errorMessage)

            advanceTimeBy(15_000)
            advanceUntilIdle()

            coVerify(atLeast = 2) { interactor.load("g1") }
            assertEquals("recovered", viewModel.uiState.value.content)
        }

    @Test
    fun refreshFromRemote_does_not_overwrite_pending_draft() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns null
            coEvery { interactor.load("g1") } returns dto(content = "server copy")

            viewModel.load("g1")
            advanceUntilIdle()

            viewModel.onContentChanged("my unsaved draft")
            viewModel.refreshFromRemote()
            advanceUntilIdle()

            assertEquals("my unsaved draft", viewModel.uiState.value.content)
            assertEquals(SaveState.PENDING, viewModel.uiState.value.saveState)
        }

    @Test
    fun refreshFromRemote_preserves_saved_state_when_remote_unchanged() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns null
            coEvery { interactor.load("g1") } returns dto(content = "hello")
            coEvery { interactor.saveLocal("g1", "hello", "u1") } returns Unit
            coEvery { interactor.sync("g1") } returns "hello"

            viewModel.load("g1")
            advanceUntilIdle()
            viewModel.saveImmediately()
            advanceUntilIdle()

            assertEquals(SaveState.SAVED, viewModel.uiState.value.saveState)

            viewModel.refreshFromRemote()
            advanceUntilIdle()

            assertEquals(SaveState.SAVED, viewModel.uiState.value.saveState)
        }

    @Test
    fun refreshFromRemote_resets_saved_state_when_remote_content_changes() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns null
            coEvery { interactor.load("g1") } returnsMany
                listOf(
                    dto(content = "hello"),
                    dto(content = "someone else edited", updatedBy = "u2"),
                )
            coEvery { interactor.saveLocal("g1", "hello", "u1") } returns Unit
            coEvery { interactor.sync("g1") } returns "hello"

            viewModel.load("g1")
            advanceUntilIdle()
            viewModel.saveImmediately()
            advanceUntilIdle()
            assertEquals(SaveState.SAVED, viewModel.uiState.value.saveState)

            viewModel.refreshFromRemote()
            advanceUntilIdle()

            assertEquals("someone else edited", viewModel.uiState.value.content)
            assertEquals(SaveState.IDLE, viewModel.uiState.value.saveState)
            assertEquals("Bob", viewModel.uiState.value.lastEditedBy)
        }

    @Test
    fun load_with_cache_shows_content_even_when_initial_fetch_fails() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns dto(content = "offline draft")
            coEvery { interactor.load("g1") } throws RuntimeException("offline")

            viewModel.load("g1")
            advanceUntilIdle()

            assertEquals("offline draft", viewModel.uiState.value.content)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun load_with_no_cache_clears_error_after_successful_refresh() =
        runTest {
            coEvery { interactor.peekLocal("g1") } returns null
            coEvery { interactor.load("g1") } throws RuntimeException("offline") andThen dto(content = "loaded")

            viewModel.load("g1")
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.errorMessage)

            advanceTimeBy(15_000)
            advanceUntilIdle()

            assertEquals("loaded", viewModel.uiState.value.content)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    private fun dto(
        content: String,
        updatedBy: String = "u1",
    ) = PinBoardDto(
        groupId = "g1",
        content = content,
        updatedBy = updatedBy,
        updatedAt = "2024-01-01T00:00:00Z",
    )

    private fun testGroup() =
        Group(
            id = "g1",
            name = "Trip",
            defaultCurrencyCode = "USD",
            createdByUserId = "u1",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )

    private fun profile(displayName: String) =
        ProfileDto(
            id = "u1",
            email = "a@b.com",
            displayName = displayName,
            updatedAtEpochMs = 0L,
        )
}
