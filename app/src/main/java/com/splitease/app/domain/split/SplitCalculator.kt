package com.splitease.app.domain.split

import com.splitease.app.domain.model.SplitType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure BigDecimal split math for expense participants.
 *
 * Rounding uses scale 2 and [RoundingMode.HALF_UP]. Remainder cents for
 * [SplitType.EQUAL] / percentage / shares go to participants in input order.
 */
object SplitCalculator {
    private val ZERO = BigDecimal.ZERO.setScale(2)
    private val HUNDRED = BigDecimal("100")

    /**
     * Computes owed amounts for [participantIds] under [splitType].
     *
     * @param total Expense total (must be > 0).
     * @param splitType Split mode.
     * @param participantIds Ordered participant user ids (non-empty, unique).
     * @param unequalAmounts Required when [SplitType.UNEQUAL]; map userId → amount.
     * @param percentages Required when [SplitType.PERCENTAGE]; map userId → percent (0–100).
     * @param shares Required when [SplitType.SHARES]; map userId → positive share weight.
     * @return Map of userId → owed amount (scale 2), summing to [total].
     * @throws IllegalArgumentException when inputs are invalid.
     */
    fun calculate(
        total: BigDecimal,
        splitType: SplitType,
        participantIds: List<String>,
        unequalAmounts: Map<String, BigDecimal> = emptyMap(),
        percentages: Map<String, BigDecimal> = emptyMap(),
        shares: Map<String, Int> = emptyMap(),
    ): Map<String, BigDecimal> {
        require(participantIds.isNotEmpty()) { "At least one participant is required." }
        require(participantIds.size == participantIds.distinct().size) { "Duplicate participants." }
        val normalizedTotal = total.setScale(2, RoundingMode.HALF_UP)
        require(normalizedTotal > ZERO) { "Amount must be greater than zero." }

        return when (splitType) {
            SplitType.EQUAL -> equalSplit(normalizedTotal, participantIds)
            SplitType.UNEQUAL -> unequalSplit(normalizedTotal, participantIds, unequalAmounts)
            SplitType.PERCENTAGE -> percentageSplit(normalizedTotal, participantIds, percentages)
            SplitType.SHARES -> sharesSplit(normalizedTotal, participantIds, shares)
        }
    }

    private fun equalSplit(total: BigDecimal, participants: List<String>): Map<String, BigDecimal> {
        val n = BigDecimal(participants.size)
        val base = total.divide(n, 2, RoundingMode.DOWN)
        var allocated = base.multiply(n)
        val result = participants.associateWith { base }.toMutableMap()
        var remainder = total.subtract(allocated)
        val cent = BigDecimal("0.01")
        var index = 0
        while (remainder > ZERO) {
            val id = participants[index % participants.size]
            result[id] = result.getValue(id).add(cent)
            remainder = remainder.subtract(cent)
            index++
        }
        return result
    }

    private fun unequalSplit(
        total: BigDecimal,
        participants: List<String>,
        amounts: Map<String, BigDecimal>,
    ): Map<String, BigDecimal> {
        require(amounts.keys.containsAll(participants) && amounts.size == participants.size) {
            "Provide an amount for every participant."
        }
        val normalized =
            participants.associateWith { id ->
                amounts.getValue(id).setScale(2, RoundingMode.HALF_UP)
            }
        val sum = normalized.values.fold(ZERO) { acc, v -> acc.add(v) }
        require(sum.compareTo(total) == 0) { "Unequal amounts must sum to the total ($total)." }
        return normalized
    }

    private fun percentageSplit(
        total: BigDecimal,
        participants: List<String>,
        percentages: Map<String, BigDecimal>,
    ): Map<String, BigDecimal> {
        require(percentages.keys.containsAll(participants) && percentages.size == participants.size) {
            "Provide a percentage for every participant."
        }
        val pctSum =
            participants.fold(ZERO) { acc, id ->
                acc.add(percentages.getValue(id).setScale(4, RoundingMode.HALF_UP))
            }
        require(pctSum.compareTo(HUNDRED) == 0) { "Percentages must sum to 100." }

        val provisional =
            participants
                .associateWith { id ->
                total
                    .multiply(percentages.getValue(id))
                    .divide(HUNDRED, 2, RoundingMode.DOWN)
            }.toMutableMap()
        var allocated = provisional.values.fold(ZERO) { acc, v -> acc.add(v) }
        var remainder = total.subtract(allocated)
        val cent = BigDecimal("0.01")
        var index = 0
        while (remainder > ZERO) {
            val id = participants[index % participants.size]
            provisional[id] = provisional.getValue(id).add(cent)
            remainder = remainder.subtract(cent)
            index++
        }
        return provisional
    }

    private fun sharesSplit(
        total: BigDecimal,
        participants: List<String>,
        shares: Map<String, Int>,
    ): Map<String, BigDecimal> {
        require(shares.keys.containsAll(participants) && shares.size == participants.size) {
            "Provide shares for every participant."
        }
        require(shares.values.all { it > 0 }) { "Shares must be positive." }
        val totalShares = shares.values.sum()
        val provisional =
            participants
                .associateWith { id ->
                total
                    .multiply(BigDecimal(shares.getValue(id)))
                    .divide(BigDecimal(totalShares), 2, RoundingMode.DOWN)
            }.toMutableMap()
        var allocated = provisional.values.fold(ZERO) { acc, v -> acc.add(v) }
        var remainder = total.subtract(allocated)
        val cent = BigDecimal("0.01")
        var index = 0
        while (remainder > ZERO) {
            val id = participants[index % participants.size]
            provisional[id] = provisional.getValue(id).add(cent)
            remainder = remainder.subtract(cent)
            index++
        }
        return provisional
    }
}
