package com.splitease.app.data.remote.mapper

import com.splitease.app.data.remote.dto.ExpenseDto
import com.splitease.app.domain.model.ExchangeRateSource
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import java.math.BigDecimal

/**
 * Maps a cloud expense row to domain, keeping a local FX snapshot when the remote
 * payload omits it (older `expenses` rows, or a pull before FX columns exist).
 */
fun ExpenseDto.toDomainExpense(
    existing: Expense?,
    categoryId: String?,
    createdAtEpochMs: Long,
): Expense {
    val parsedAmount =
        amount.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Invalid expense amount: $amount")
    val remoteOriginalAmount = originalAmount.toBigDecimalOrNull()
    val remoteRate = rateToDefaultCurrency.toBigDecimalOrNull()
    val remoteCode = originalCurrencyCode?.trim()?.takeIf { it.isNotEmpty() }
    val remoteSource =
        rateSource?.let { raw ->
            runCatching { ExchangeRateSource.valueOf(raw.trim().uppercase()) }.getOrNull()
        }
    val remoteHasFx =
        remoteOriginalAmount != null ||
            remoteCode != null ||
            remoteRate != null ||
            remoteSource != null
    return Expense(
        id = id,
        description = description,
        amount = parsedAmount,
        currencyCode = currencyCode,
        categoryId = categoryId,
        paidByUserId = paidByUserId,
        groupId = groupId,
        expenseDateEpochMs = expenseDateEpochMs,
        splitType = runCatching { SplitType.valueOf(splitType) }.getOrDefault(SplitType.EQUAL),
        notes = notes,
        remoteId = id,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = SyncStatus.SYNCED,
        originalAmount = if (remoteHasFx) remoteOriginalAmount else existing?.originalAmount,
        originalCurrencyCode = if (remoteHasFx) remoteCode else existing?.originalCurrencyCode,
        rateToDefaultCurrency = if (remoteHasFx) remoteRate else existing?.rateToDefaultCurrency,
        rateSource = if (remoteHasFx) remoteSource else existing?.rateSource,
    )
}

/** Maps domain [Expense] to PostgREST [ExpenseDto]. */
fun Expense.toExpenseDto(cloudCategoryId: String?): ExpenseDto =
    ExpenseDto(
        id = id,
        description = description,
        amount = amount.toPlainString(),
        currencyCode = currencyCode,
        categoryId = cloudCategoryId,
        paidByUserId = paidByUserId,
        groupId = groupId,
        expenseDateEpochMs = expenseDateEpochMs,
        splitType = splitType.name,
        notes = notes,
        updatedAtEpochMs = updatedAtEpochMs,
        originalAmount = originalAmount?.toPlainString(),
        originalCurrencyCode = originalCurrencyCode,
        rateToDefaultCurrency = rateToDefaultCurrency?.toPlainString(),
        rateSource = rateSource?.name,
    )

/** Drop FX columns so an upsert can succeed before `migration_db.sql` FX columns are applied. */
fun ExpenseDto.withoutFxSnapshot(): ExpenseDto =
    copy(
        originalAmount = null,
        originalCurrencyCode = null,
        rateToDefaultCurrency = null,
        rateSource = null,
    )

private fun String?.toBigDecimalOrNull(): BigDecimal? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { BigDecimal(raw) }.getOrNull()
}
