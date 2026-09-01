package com.splitease.app.data.expense

import android.content.Context
import android.util.Log
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.remote.ExpenseReceiptStorage
import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.remote.dto.ExpenseCommentDto
import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.data.remote.dto.ExpensePhotoDto
import com.splitease.app.data.remote.dto.ExpenseSplitDto
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseCommentRepository
import com.splitease.app.domain.repository.ExpensePhotoRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.data.sync.REMOTE_FETCH_ROW_CAP
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Provider

class ExpenseHydrateTest {
    private val remote: ExpenseRemoteDataSource = mockk(relaxed = true)
    private val expenseRepository: ExpenseRepository = mockk(relaxed = true)
    private val expenseCommentRepository: ExpenseCommentRepository = mockk(relaxed = true)
    private val expensePhotoRepository: ExpensePhotoRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val groupRepository: GroupRepository = mockk(relaxed = true)
    private lateinit var interactor: ExpenseInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0

        coEvery { expenseRepository.getExpensesByIds(any()) } returns emptyMap()
        coEvery { expenseRepository.getSyncedIdsByGroup(any()) } returns emptyList()
        coEvery { expenseRepository.getSyncedNonGroupIdsInvolvingUser(any()) } returns emptyList()
        coEvery { expensePhotoRepository.getForExpenses(any()) } returns emptyList()
        coEvery { categoryRepository.resolveCategoryForRemotePull(any()) } answers { firstArg() }
        coEvery { userRepository.getUserById(any()) } returns stubUser("u1")

        interactor =
            ExpenseInteractor(
                appContext = mockk<Context>(relaxed = true),
                expenseRepository = expenseRepository,
                expenseCommentRepository = expenseCommentRepository,
                expensePhotoRepository = expensePhotoRepository,
                userRepository = userRepository,
                categoryRepository = categoryRepository,
                groupRepository = groupRepository,
                activityEventRepository = mockk<ActivityEventRepository>(relaxed = true),
                remote = remote,
                receiptStorage = mockk<ExpenseReceiptStorage>(relaxed = true),
                mediaStorageCleanup = mockk<MediaStorageCleanup>(relaxed = true),
                socialRemote = mockk<SocialRemoteDataSource>(relaxed = true),
                mailRepository = mockk(relaxed = true),
                syncInteractor = mockk<Provider<SyncInteractor>>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun refreshExpensesForUser_batches_in_filters_and_skips_per_id_and_group_refetch() =
        runTest {
            val paid = expenseDto("a", groupId = "g1", paidBy = "u1")
            val extra = expenseDto("b", groupId = null, paidBy = "u2")
            val groupOnly = expenseDto("c", groupId = "g1", paidBy = "u3")
            coEvery { remote.fetchPaidBy("u1") } returns listOf(paid)
            coEvery { remote.fetchSplitExpenseIdsForUser("u1") } returns listOf("a", "b")
            coEvery { groupRepository.observeGroupsForUser("u1") } returns flowOf(listOf(group("g1")))
            coEvery { remote.fetchByGroupIds(listOf("g1")) } returns listOf(paid, groupOnly)
            coEvery { remote.fetchByIds(match { it.toSet() == setOf("b") }) } returns listOf(extra)
            coEvery { remote.fetchSplitsForExpenseIds(match { it.toSet() == setOf("a", "b", "c") }) } returns
                listOf(
                    splitDto("sa", "a", "u1"),
                    splitDto("sb", "b", "u1"),
                    splitDto("sc", "c", "u3"),
                )
            coEvery { remote.fetchCommentsForExpenseIds(match { it.toSet() == setOf("a", "b", "c") }) } returns
                listOf(commentDto("ca", "a"))
            coEvery { remote.fetchPhotosForExpenseIds(match { it.toSet() == setOf("a", "b", "c") }) } returns
                listOf(photoDto("pa", "a"))

            interactor.refreshExpensesForUser("u1")

            coVerify(exactly = 0) { remote.fetchExpense(any()) }
            coVerify(exactly = 0) { remote.fetchSplits(any()) }
            coVerify(exactly = 0) { remote.fetchComments(any()) }
            coVerify(exactly = 0) { remote.fetchPhotos(any()) }
            coVerify(exactly = 0) { remote.fetchByGroup(any()) }
            coVerify(exactly = 1) { remote.fetchByGroupIds(listOf("g1")) }
            coVerify(exactly = 1) { remote.fetchByIds(match { it.toSet() == setOf("b") }) }
            coVerify(exactly = 1) {
                remote.fetchSplitsForExpenseIds(match { it.toSet() == setOf("a", "b", "c") })
            }
            coVerify(exactly = 1) {
                remote.fetchCommentsForExpenseIds(match { it.toSet() == setOf("a", "b", "c") })
            }
            coVerify(exactly = 1) {
                remote.fetchPhotosForExpenseIds(match { it.toSet() == setOf("a", "b", "c") })
            }
            coVerify(exactly = 1) {
                expenseRepository.upsertExpensesWithSplits(
                    match { expenses -> expenses.map { it.id }.toSet() == setOf("a", "b", "c") },
                    match { splits -> splits.map { it.expenseId }.toSet() == setOf("a", "b", "c") },
                )
            }
            coVerify(exactly = 1) {
                expenseCommentRepository.upsertAll(match { it.map { c -> c.id } == listOf("ca") })
            }
            coVerify(exactly = 1) {
                expensePhotoRepository.upsertAll(match { it.map { p -> p.id } == listOf("pa") })
            }
        }

    @Test
    fun refreshExpensesForUser_skips_id_fetch_when_paid_and_groups_cover_all() =
        runTest {
            val paid = expenseDto("a", groupId = "g1", paidBy = "u1")
            coEvery { remote.fetchPaidBy("u1") } returns listOf(paid)
            coEvery { remote.fetchSplitExpenseIdsForUser("u1") } returns listOf("a")
            coEvery { groupRepository.observeGroupsForUser("u1") } returns flowOf(listOf(group("g1")))
            coEvery { remote.fetchByGroupIds(listOf("g1")) } returns listOf(paid)
            coEvery { remote.fetchSplitsForExpenseIds(listOf("a")) } returns listOf(splitDto("sa", "a", "u1"))
            coEvery { remote.fetchCommentsForExpenseIds(listOf("a")) } returns emptyList()
            coEvery { remote.fetchPhotosForExpenseIds(listOf("a")) } returns emptyList()

            interactor.refreshExpensesForUser("u1")

            coVerify(exactly = 0) { remote.fetchByIds(any()) }
            coVerify(exactly = 0) { remote.fetchExpense(any()) }
            coVerify(exactly = 0) { remote.fetchByGroup(any()) }
        }

    @Test
    fun refreshExpensesForUser_capped_group_in_filter_omits_other_group_and_skips_prune() =
        runTest {
            val g1Rows =
                (1..REMOTE_FETCH_ROW_CAP).map { n ->
                    expenseDto("g1-$n", groupId = "g1", paidBy = "u3")
                }
            coEvery { remote.fetchPaidBy("u1") } returns emptyList()
            coEvery { remote.fetchSplitExpenseIdsForUser("u1") } returns emptyList()
            coEvery { groupRepository.observeGroupsForUser("u1") } returns
                flowOf(listOf(group("g1"), group("g2")))
            coEvery { remote.fetchByGroupIds(match { it.toSet() == setOf("g1", "g2") }) } returns g1Rows
            coEvery { remote.fetchSplitsForExpenseIds(any()) } returns emptyList()
            coEvery { remote.fetchCommentsForExpenseIds(any()) } returns emptyList()
            coEvery { remote.fetchPhotosForExpenseIds(any()) } returns emptyList()
            coEvery { expenseRepository.getSyncedIdsByGroup("g2") } returns listOf("g2-local")

            val expensesSlot = slot<List<com.splitease.app.domain.model.Expense>>()
            coEvery {
                expenseRepository.upsertExpensesWithSplits(capture(expensesSlot), any())
            } returns Unit

            interactor.refreshExpensesForUser("u1")

            assertEquals(REMOTE_FETCH_ROW_CAP, expensesSlot.captured.size)
            assertTrue(expensesSlot.captured.all { it.groupId == "g1" })
            assertFalse(expensesSlot.captured.any { it.groupId == "g2" })
            coVerify(exactly = 0) { expenseRepository.deleteExpenseById("g2-local") }
        }

    @Test
    fun refreshExpensesForUser_truncated_splits_below_row_cap_still_apply_empty_split_parents() =
        runTest {
            val kept = expenseDto("a", groupId = "g1", paidBy = "u1")
            val empty = expenseDto("b", groupId = "g1", paidBy = "u1")
            coEvery { remote.fetchPaidBy("u1") } returns listOf(kept, empty)
            coEvery { remote.fetchSplitExpenseIdsForUser("u1") } returns listOf("a", "b")
            coEvery { groupRepository.observeGroupsForUser("u1") } returns flowOf(listOf(group("g1")))
            coEvery { remote.fetchByGroupIds(listOf("g1")) } returns listOf(kept, empty)
            coEvery { remote.fetchSplitsForExpenseIds(match { it.toSet() == setOf("a", "b") }) } returns
                listOf(splitDto("sa", "a", "u1"))
            coEvery { remote.fetchCommentsForExpenseIds(any()) } returns emptyList()
            coEvery { remote.fetchPhotosForExpenseIds(any()) } returns emptyList()

            val expensesSlot = slot<List<com.splitease.app.domain.model.Expense>>()
            val splitsSlot = slot<List<com.splitease.app.domain.model.ExpenseSplit>>()
            coEvery {
                expenseRepository.upsertExpensesWithSplits(capture(expensesSlot), capture(splitsSlot))
            } returns Unit

            interactor.refreshExpensesForUser("u1")

            assertEquals(setOf("a", "b"), expensesSlot.captured.map { it.id }.toSet())
            assertEquals(setOf("a"), splitsSlot.captured.map { it.expenseId }.toSet())
        }

    @Test
    fun refreshExpensesForUser_truncated_splits_at_row_cap_do_not_wipe_missing_parents() =
        runTest {
            val kept = expenseDto("a", groupId = "g1", paidBy = "u1")
            val dropped = expenseDto("b", groupId = "g1", paidBy = "u1")
            val cappedSplits =
                (1..REMOTE_FETCH_ROW_CAP).map { n -> splitDto("sa-$n", "a", "u1") }
            coEvery { remote.fetchPaidBy("u1") } returns listOf(kept, dropped)
            coEvery { remote.fetchSplitExpenseIdsForUser("u1") } returns listOf("a", "b")
            coEvery { groupRepository.observeGroupsForUser("u1") } returns flowOf(listOf(group("g1")))
            coEvery { remote.fetchByGroupIds(listOf("g1")) } returns listOf(kept, dropped)
            coEvery { remote.fetchSplitsForExpenseIds(match { it.toSet() == setOf("a", "b") }) } returns
                cappedSplits
            coEvery { remote.fetchCommentsForExpenseIds(any()) } returns emptyList()
            coEvery { remote.fetchPhotosForExpenseIds(any()) } returns emptyList()

            val expensesSlot = slot<List<com.splitease.app.domain.model.Expense>>()
            val splitsSlot = slot<List<com.splitease.app.domain.model.ExpenseSplit>>()
            coEvery {
                expenseRepository.upsertExpensesWithSplits(capture(expensesSlot), capture(splitsSlot))
            } returns Unit

            interactor.refreshExpensesForUser("u1")

            assertEquals(setOf("a"), expensesSlot.captured.map { it.id }.toSet())
            assertTrue(splitsSlot.captured.all { it.expenseId == "a" })
            assertEquals(REMOTE_FETCH_ROW_CAP, splitsSlot.captured.size)
        }

    private fun expenseDto(
        id: String,
        groupId: String?,
        paidBy: String,
    ) = ExpenseDto(
        id = id,
        description = "Expense $id",
        amount = "10.00",
        currencyCode = "INR",
        paidByUserId = paidBy,
        groupId = groupId,
        expenseDateEpochMs = 1_000L,
        splitType = "EQUAL",
        updatedAtEpochMs = 2_000L,
    )

    private fun splitDto(
        id: String,
        expenseId: String,
        userId: String,
    ) = ExpenseSplitDto(
        id = id,
        expenseId = expenseId,
        userId = userId,
        owedAmount = "10.00",
    )

    private fun commentDto(
        id: String,
        expenseId: String,
    ) = ExpenseCommentDto(
        id = id,
        expenseId = expenseId,
        authorUserId = "u1",
        body = "hi",
        kind = "USER",
        createdAtEpochMs = 1L,
    )

    private fun photoDto(
        id: String,
        expenseId: String,
    ) = ExpensePhotoDto(
        id = id,
        expenseId = expenseId,
        createdByUserId = "u1",
        remoteUrl = "https://example.com/$id.jpg",
        createdAtEpochMs = 1L,
    )

    private fun group(id: String) =
        Group(
            id = id,
            name = "Group $id",
            defaultCurrencyCode = "INR",
            groupType = GroupType.OTHER,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            syncStatus = SyncStatus.SYNCED,
        )

    private fun stubUser(id: String) =
        User(
            id = id,
            email = "$id@example.com",
            displayName = id,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
}
