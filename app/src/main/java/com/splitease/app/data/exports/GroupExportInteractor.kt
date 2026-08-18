package com.splitease.app.data.exports

import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.domain.exports.GroupExportFileNames
import com.splitease.app.domain.exports.GroupLedgerCsvExporter
import com.splitease.app.domain.exports.GroupLedgerExportBalance
import com.splitease.app.domain.exports.GroupLedgerExportInput
import com.splitease.app.domain.exports.GroupLedgerExportSettlement
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppCurrencies
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV file ready to share.
 *
 * @property fileName Suggested download name.
 * @property csv UTF-8 CSV body (no BOM).
 */
data class GroupCsvExport(
    val fileName: String,
    val csv: String,
)

/**
 * Loads a group's ledger and balances, then renders a CSV export.
 */
@Singleton
class GroupExportInteractor
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val categoryRepository: CategoryRepository,
        private val friendRepository: FriendRepository,
        private val userRepository: UserRepository,
        private val balanceInteractor: BalanceInteractor,
    ) {
        /**
         * Builds a CSV of expenses, settlements, and current balances for [groupId].
         *
         * @param groupId Group to export.
         * @param viewerUserId Signed-in member (used for labels and simplify-debts).
         * @param exportedAtEpochMs Timestamp written into the file header.
         * @param zoneId Time zone for dates.
         */
        suspend fun buildGroupCsv(
            groupId: String,
            viewerUserId: String,
            exportedAtEpochMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): Result<GroupCsvExport> =
            runCatching {
                val group =
                    groupRepository.getGroupById(groupId)
                        ?: error("Group not found.")
                val expenses = expenseRepository.observeExpenses(groupId).first()
                val payments = paymentRepository.observePayments(groupId).first()
                val members = groupRepository.observeMembers(groupId).first()
                val splits = expenseRepository.getSplitsForExpenses(expenses.map { it.id })
                val categories =
                    categoryRepository.observeCategories().first().associate { it.id to it.name }
                val friends =
                    friendRepository.observeFriends(viewerUserId).first()
                        .associate { it.friendUserId to it.displayNameSnapshot }
                val users =
                    userRepository.observeUsers().first().associate { it.id to it.displayName }
                val balance = balanceInteractor.observeGroupBalance(groupId, viewerUserId).first()

                val extraIds =
                    buildSet {
                        expenses.forEach { expense ->
                            add(expense.paidByUserId)
                            splits[expense.id].orEmpty().forEach { split -> add(split.userId) }
                        }
                        payments.forEach { payment ->
                            add(payment.fromUserId)
                            add(payment.toUserId)
                        }
                    }
                val memberIds =
                    buildList {
                        val seen = linkedSetOf<String>()
                        members.forEach { member ->
                            if (seen.add(member.userId)) add(member.userId)
                        }
                        extraIds.forEach { id ->
                            if (seen.add(id)) add(id)
                        }
                    }
                val labels = uniqueLabels(memberIds, friends, users)
                val defaultCurrency = group.defaultCurrencyCode.ifBlank { AppCurrencies.DEFAULT }
                val zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                val balances =
                    memberIds.flatMap { userId ->
                        val label = labels[userId] ?: userId.take(8)
                        val nets =
                            balance.memberNetsByCurrency.mapNotNull { (currency, nets) ->
                                val net = nets[userId] ?: return@mapNotNull null
                                GroupLedgerExportBalance(
                                    memberLabel = label,
                                    currencyCode = currency,
                                    net = net.setScale(2, RoundingMode.HALF_UP),
                                )
                            }
                        nets.ifEmpty {
                            listOf(
                                GroupLedgerExportBalance(
                                    memberLabel = label,
                                    currencyCode = defaultCurrency,
                                    net = zero,
                                ),
                            )
                        }
                    }

                val csv =
                    GroupLedgerCsvExporter.export(
                        input =
                            GroupLedgerExportInput(
                                groupName = group.name,
                                exportedAtEpochMs = exportedAtEpochMs,
                                memberIdsInOrder = memberIds,
                                memberLabels = labels,
                                expenses = expenses,
                                payments = payments,
                                splitsByExpenseId = splits,
                                categoryNamesById = categories,
                                balances = balances,
                                suggestedSettlements =
                                    balance.simplifiedDebts.map { debt ->
                                        GroupLedgerExportSettlement(
                                            fromLabel = labels[debt.fromUserId] ?: debt.fromLabel,
                                            toLabel = labels[debt.toUserId] ?: debt.toLabel,
                                            amount = debt.amount,
                                            currencyCode = debt.currencyCode,
                                        )
                                    },
                            ),
                        zoneId = zoneId,
                    )
                GroupCsvExport(
                    fileName = GroupExportFileNames.fileName(group.name, exportedAtEpochMs, zoneId),
                    csv = csv,
                )
            }

        private fun uniqueLabels(
            ids: List<String>,
            friendNames: Map<String, String>,
            userNames: Map<String, String>,
        ): Map<String, String> {
            val raw =
                ids.associateWith { id ->
                    friendNames[id]
                        ?.removeSuffix(" (invited)")
                        ?.trim()
                        ?.ifBlank { null }
                        ?: userNames[id]?.trim()?.ifBlank { null }
                        ?: id.take(8)
                }
            val counts = raw.values.groupingBy { it.lowercase() }.eachCount()
            return raw.mapValues { (id, name) ->
                if ((counts[name.lowercase()] ?: 0) > 1) "$name (${id.take(8)})" else name
            }
        }
    }
