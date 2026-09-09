package com.splitease.app.data.expense

import android.content.Context
import android.util.Log
import com.splitease.app.data.media.MediaStorageCleanup
import com.splitease.app.data.remote.ExpenseReceiptStorage
import com.splitease.app.data.remote.ExpenseRemoteDataSource
import com.splitease.app.data.remote.SocialRemoteDataSource
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.ExchangeRateSource
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseCommentRepository
import com.splitease.app.domain.repository.ExpensePhotoRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import javax.inject.Provider

class ConvertMixedCurrenciesTest {
    private val expenseRepository: ExpenseRepository = mockk(relaxed = true)
    private lateinit var interactor: ExpenseInteractor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0

        interactor =
            ExpenseInteractor(
                appContext = mockk<Context>(relaxed = true),
                expenseRepository = expenseRepository,
                expenseCommentRepository = mockk<ExpenseCommentRepository>(relaxed = true),
                expensePhotoRepository = mockk<ExpensePhotoRepository>(relaxed = true),
                userRepository = mockk<UserRepository>(relaxed = true),
                categoryRepository = mockk<CategoryRepository>(relaxed = true),
                groupRepository = mockk<GroupRepository>(relaxed = true),
                activityEventRepository = mockk<ActivityEventRepository>(relaxed = true),
                remote = mockk<ExpenseRemoteDataSource>(relaxed = true),
                receiptStorage = mockk<ExpenseReceiptStorage>(relaxed = true),
                mediaStorageCleanup = mockk<MediaStorageCleanup>(relaxed = true),
                socialRemote = mockk<SocialRemoteDataSource>(relaxed = true),
                syncInteractor = mockk<Provider<SyncInteractor>>(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun convertMixedCurrencies_converts_foreign_rows_and_splits_at_captured_rate() =
        runTest {
            val usd =
                expense(
                    id = "usd",
                    amount = BigDecimal("40.00"),
                    currencyCode = "USD",
                    originalAmount = BigDecimal("40.00"),
                    originalCurrencyCode = "USD",
                    rate = BigDecimal("83.50"),
                )
            val inr =
                expense(
                    id = "inr",
                    amount = BigDecimal("100.00"),
                    currencyCode = "INR",
                )
            val noRate =
                expense(
                    id = "eur",
                    amount = BigDecimal("10.00"),
                    currencyCode = "EUR",
                )
            coEvery { expenseRepository.getExpensesByGroupId("g1") } returns listOf(usd, inr, noRate)
            coEvery { expenseRepository.getSplits("usd") } returns
                listOf(
                    split("s1", "usd", "u1", BigDecimal("20.00"), paid = BigDecimal("40.00")),
                    split("s2", "usd", "u2", BigDecimal("20.00"), adjustment = BigDecimal("1.00")),
                )

            val expenseSlot = slot<Expense>()
            val splitsSlot = slot<List<ExpenseSplit>>()
            coEvery { expenseRepository.upsertExpenseWithSplits(capture(expenseSlot), capture(splitsSlot)) } returns Unit

            val result = interactor.convertMixedCurrencies(groupId = "g1", targetCurrencyCode = "INR")

            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow())
            coVerify(exactly = 1) { expenseRepository.upsertExpenseWithSplits(any(), any()) }

            val saved = expenseSlot.captured
            assertEquals(BigDecimal("3340.00"), saved.amount)
            assertEquals("INR", saved.currencyCode)
            assertEquals(BigDecimal("40.00"), saved.originalAmount)
            assertEquals("USD", saved.originalCurrencyCode)
            assertEquals(SyncStatus.PENDING, saved.syncStatus)

            assertEquals(2, splitsSlot.captured.size)
            assertEquals(BigDecimal("1670.00"), splitsSlot.captured[0].owedAmount)
            assertEquals(BigDecimal("3340.00"), splitsSlot.captured[0].paidAmount)
            assertEquals(BigDecimal("1670.00"), splitsSlot.captured[1].owedAmount)
            assertEquals(BigDecimal("83.50"), splitsSlot.captured[1].adjustmentAmount)
            assertTrue(splitsSlot.captured.all { it.syncStatus == SyncStatus.PENDING })
        }

    @Test
    fun convertMixedCurrencies_does_not_rescale_splits_when_amount_already_converted() =
        runTest {
            val usd =
                expense(
                    id = "f1",
                    amount = BigDecimal("800.00"),
                    currencyCode = "USD",
                    groupId = null,
                    originalAmount = BigDecimal("10.00"),
                    originalCurrencyCode = "USD",
                    rate = BigDecimal("80"),
                )
            coEvery { expenseRepository.getFriendshipExpenses("me", "friend") } returns listOf(usd)
            coEvery { expenseRepository.getSplits("f1") } returns
                listOf(split("s1", "f1", "me", BigDecimal("800.00")))

            val expenseSlot = slot<Expense>()
            val splitsSlot = slot<List<ExpenseSplit>>()
            coEvery { expenseRepository.upsertExpenseWithSplits(capture(expenseSlot), capture(splitsSlot)) } returns Unit

            val result =
                interactor.convertMixedCurrencies(
                    friendUserId = "friend",
                    targetCurrencyCode = "INR",
                    actorUserId = "me",
                )

            assertEquals(1, result.getOrThrow())
            assertEquals(BigDecimal("800.00"), expenseSlot.captured.amount)
            assertEquals("INR", expenseSlot.captured.currencyCode)
            assertEquals(BigDecimal("10.00"), expenseSlot.captured.originalAmount)
            assertEquals("USD", expenseSlot.captured.originalCurrencyCode)
            assertEquals(BigDecimal("800.00"), splitsSlot.captured.single().owedAmount)
        }

    @Test
    fun convertMixedCurrencies_friendship_path_uses_amount_when_original_missing() =
        runTest {
            val usd =
                expense(
                    id = "f2",
                    amount = BigDecimal("10.00"),
                    currencyCode = "USD",
                    groupId = null,
                    originalAmount = null,
                    originalCurrencyCode = null,
                    rate = BigDecimal("80"),
                )
            coEvery { expenseRepository.getFriendshipExpenses("me", "friend") } returns listOf(usd)
            coEvery { expenseRepository.getSplits("f2") } returns
                listOf(split("s1", "f2", "me", BigDecimal("10.00")))

            val expenseSlot = slot<Expense>()
            coEvery { expenseRepository.upsertExpenseWithSplits(capture(expenseSlot), any()) } returns Unit

            val result =
                interactor.convertMixedCurrencies(
                    friendUserId = "friend",
                    targetCurrencyCode = "INR",
                    actorUserId = "me",
                )

            assertEquals(1, result.getOrThrow())
            assertEquals(BigDecimal("800.00"), expenseSlot.captured.amount)
            assertEquals("INR", expenseSlot.captured.currencyCode)
            assertEquals(BigDecimal("10.00"), expenseSlot.captured.originalAmount)
            assertEquals("USD", expenseSlot.captured.originalCurrencyCode)
        }

    @Test
    fun convertMixedCurrencies_returns_zero_when_nothing_to_convert() =
        runTest {
            coEvery { expenseRepository.getExpensesByGroupId("g1") } returns
                listOf(expense(id = "inr", amount = BigDecimal("50"), currencyCode = "INR"))

            val result = interactor.convertMixedCurrencies(groupId = "g1", targetCurrencyCode = "INR")

            assertEquals(0, result.getOrThrow())
            coVerify(exactly = 0) { expenseRepository.upsertExpenseWithSplits(any(), any()) }
        }

    private fun expense(
        id: String,
        amount: BigDecimal,
        currencyCode: String,
        groupId: String? = "g1",
        originalAmount: BigDecimal? = null,
        originalCurrencyCode: String? = null,
        rate: BigDecimal? = null,
    ) = Expense(
        id = id,
        description = id,
        amount = amount,
        currencyCode = currencyCode,
        paidByUserId = "u1",
        groupId = groupId,
        expenseDateEpochMs = 1_000L,
        splitType = SplitType.EQUAL,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
        syncStatus = SyncStatus.SYNCED,
        originalAmount = originalAmount,
        originalCurrencyCode = originalCurrencyCode,
        rateToDefaultCurrency = rate,
        rateSource = if (rate != null) ExchangeRateSource.LIVE else null,
    )

    private fun split(
        id: String,
        expenseId: String,
        userId: String,
        owed: BigDecimal,
        paid: BigDecimal? = null,
        adjustment: BigDecimal? = null,
    ) = ExpenseSplit(
        id = id,
        expenseId = expenseId,
        userId = userId,
        owedAmount = owed,
        paidAmount = paid,
        adjustmentAmount = adjustment,
        syncStatus = SyncStatus.SYNCED,
    )
}
