package com.splitease.app.domain.model

import java.math.BigDecimal

/**
 * A shared expense that may belong to a group or a direct (non-group) friend split.
 *
 * Monetary fields use [BigDecimal] only — never Float/Double.
 *
 * @property id Stable local UUID.
 * @property description Short title shown in lists.
 * @property amount Total expense amount in [currencyCode].
 * @property currencyCode ISO 4217 currency code.
 * @property categoryId Optional category reference.
 * @property paidByUserId Who paid the merchant / upfront cost.
 * @property groupId Owning group; null for direct friend expenses.
 * @property expenseDateEpochMs Business date of the expense.
 * @property splitType How participant shares were computed.
 * @property isRecurring Whether a recurrence rule applies.
 * @property recurrenceFrequency Cadence when [isRecurring] is true.
 * @property nextOccurrenceEpochMs Next generate-at for templates; null when not recurring.
 * @property recurringTemplateId Parent template id for generated instances.
 * @property notes Optional free-form note.
 * @property remoteId Cloud id when synced.
 * @property createdAtEpochMs Creation timestamp.
 * @property updatedAtEpochMs Last mutation timestamp.
 * @property syncStatus Offline-first sync bookmark.
 */
data class Expense(
    val id: String,
    val description: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val categoryId: String? = null,
    val paidByUserId: String,
    val groupId: String? = null,
    val expenseDateEpochMs: Long,
    val splitType: SplitType,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val nextOccurrenceEpochMs: Long? = null,
    val recurringTemplateId: String? = null,
    val notes: String? = null,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    /** Amount in the currency it was originally entered (before conversion). */
    val originalAmount: BigDecimal? = null,
    /** Currency code selected at creation. */
    val originalCurrencyCode: String? = null,
    /** Captured exchange rate: 1 [originalCurrencyCode] = X [currencyCode]. */
    val rateToDefaultCurrency: BigDecimal? = null,
    /** Source of the exchange rate. */
    val rateSource: ExchangeRateSource? = null,
)

/**
 * Source of the exchange rate for an expense.
 */
enum class ExchangeRateSource {
    /** Rate fetched from a live API (e.g. ExchangeRate-API). */
    LIVE,
    /** Rate entered manually by the user. */
    CUSTOM,
}

/**
 * One participant's share of an [Expense].
 *
 * @property id Stable local UUID.
 * @property expenseId Parent expense id.
 * @property userId Participant who owes (or is owed relative to payer).
 * @property owedAmount Exact amount owed toward this expense (currency of parent).
 * @property percentage Optional percent used when [SplitType.PERCENTAGE].
 * @property shares Optional share weight used when [SplitType.SHARES].
 * @property paidAmount Optional multi-payer paid amount.
 * @property adjustmentAmount Optional extra owed when [SplitType.ADJUSTMENT].
 * @property syncStatus Offline-first sync bookmark.
 */
data class ExpenseSplit(
    val id: String,
    val expenseId: String,
    val userId: String,
    val owedAmount: BigDecimal,
    val percentage: BigDecimal? = null,
    val shares: Int? = null,
    /**
     * Amount this participant paid toward the expense.
     *
     * Null means legacy single-payer mode: only [Expense.paidByUserId] is credited the full
     * expense amount. When any split has a non-null value, balances credit each
     * [paidAmount] instead.
     */
    val paidAmount: BigDecimal? = null,
    /** Extra owed beyond equal remainder when [SplitType.ADJUSTMENT]. */
    val adjustmentAmount: BigDecimal? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
