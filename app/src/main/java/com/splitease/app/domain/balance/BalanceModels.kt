package com.splitease.app.domain.balance

import java.math.BigDecimal

/**
 * A suggested settlement transfer after debt simplification.
 *
 * [fromUserId] pays [amount] in [currencyCode] to [toUserId].
 *
 * @property fromUserId Debtor (owes money).
 * @property toUserId Creditor (is owed money).
 * @property amount Positive amount at scale 2.
 * @property currencyCode ISO 4217 currency code.
 */
data class DebtTransfer(
    val fromUserId: String,
    val toUserId: String,
    val amount: BigDecimal,
    val currencyCode: String,
)
