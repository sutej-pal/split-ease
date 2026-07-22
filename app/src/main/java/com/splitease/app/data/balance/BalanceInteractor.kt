package com.splitease.app.data.balance

import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.balance.DebtSimplifier
import com.splitease.app.domain.balance.DebtTransfer
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A suggested settlement with display labels.
 *
 * @property fromUserId Debtor id.
 * @property fromLabel Debtor display name.
 * @property toUserId Creditor id.
 * @property toLabel Creditor display name.
 * @property amount Positive amount.
 * @property currencyCode ISO 4217 code.
 */
data class LabeledDebt(
    val fromUserId: String,
    val fromLabel: String,
    val toUserId: String,
    val toLabel: String,
    val amount: BigDecimal,
    val currencyCode: String,
)

/**
 * Net balances and simplified debts for one group.
 *
 * @property groupId Group id.
 * @property groupName Group display name.
 * @property myNetByCurrency Current user's net (positive = owed to me).
 * @property memberNetsByCurrency All member nets by currency.
 * @property simplifiedDebts Minimized who-owes-whom list.
 */
data class GroupBalanceUi(
    val groupId: String,
    val groupName: String,
    val myNetByCurrency: Map<String, BigDecimal>,
    val memberNetsByCurrency: Map<String, Map<String, BigDecimal>>,
    val simplifiedDebts: List<LabeledDebt>,
)

/**
 * Friend pairwise balance (viewer perspective).
 *
 * @property friendUserId Friend's user id.
 * @property displayName Friend label.
 * @property netByCurrency Positive = friend owes me; negative = I owe friend.
 */
data class FriendBalanceUi(
    val friendUserId: String,
    val displayName: String,
    val netByCurrency: Map<String, BigDecimal>,
)

/**
 * Overall balances hub snapshot.
 *
 * Totals come from the viewer's global nets (not friend+group sum) to avoid double-counting.
 *
 * @property totalOwedToMeByCurrency Currencies where viewer net is positive.
 * @property totalIOweByCurrency Absolute values where viewer net is negative.
 * @property friendBalances Per-friend pairwise nets across shared expenses.
 * @property groupBalances Per-group summaries (includes settled groups).
 * @property nonGroupMyNetByCurrency Net for 1:1 (non-group) expenses only.
 * @property nonGroupDebts Simplified debts for non-group expenses involving the viewer.
 */
data class OverallBalancesUi(
    val totalOwedToMeByCurrency: Map<String, BigDecimal>,
    val totalIOweByCurrency: Map<String, BigDecimal>,
    val friendBalances: List<FriendBalanceUi>,
    val groupBalances: List<GroupBalanceUi>,
    val nonGroupMyNetByCurrency: Map<String, BigDecimal> = emptyMap(),
    val nonGroupDebts: List<LabeledDebt> = emptyList(),
)

/**
 * Observes Room expenses/splits/payments and derives balance UI models.
 *
 * @property expenseRepository Local expenses.
 * @property paymentRepository Local settlements.
 * @property friendRepository Friends for labels and pairwise scope.
 * @property groupRepository Groups and membership.
 * @property userRepository Display-name lookup.
 */
@Singleton
class BalanceInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val paymentRepository: PaymentRepository,
        private val friendRepository: FriendRepository,
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository,
    ) {
        /**
         * Observes balance summary for a group.
         *
         * @param groupId Group id.
         * @param viewerUserId Signed-in user id.
         * @return Cold [Flow] of [GroupBalanceUi].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeGroupBalance(
            groupId: String,
            viewerUserId: String,
        ): Flow<GroupBalanceUi> =
            combine(
                expenseRepository.observeExpenses(groupId),
                paymentRepository.observePayments(groupId),
            ) { expenses, payments ->
                expenses to payments
            }.flatMapLatest { (expenses, payments) ->
                flow {
                    emit(buildGroupBalance(groupId, viewerUserId, expenses, payments = payments))
                }
            }

        /**
         * Observes 1:1 net between viewer and a friend (non-group expenses + payments).
         *
         * @param viewerUserId Signed-in user.
         * @param friendUserId Friend user id.
         * @return Cold [Flow] of [FriendBalanceUi].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeFriendBalance(
            viewerUserId: String,
            friendUserId: String,
        ): Flow<FriendBalanceUi> =
            combine(
                expenseRepository.observeBetweenUsers(viewerUserId, friendUserId),
                paymentRepository.observeBetweenUsers(viewerUserId, friendUserId),
                friendRepository.observeFriends(viewerUserId),
            ) { expenses, payments, friends ->
                Triple(expenses, payments, friends)
            }.flatMapLatest { (expenses, payments, friends) ->
                flow {
                    val splits = loadSplits(expenses)
                    val nets =
                        BalanceCalculator.pairwiseNetByCurrency(
                            viewerUserId = viewerUserId,
                            otherUserId = friendUserId,
                            expenses = expenses,
                            splitsByExpenseId = splits,
                            payments = payments,
                        )
                    val label =
                        friends.firstOrNull { it.friendUserId == friendUserId }?.displayNameSnapshot
                            ?: userRepository.getUserById(friendUserId)?.displayName
                            ?: friendUserId.take(8)
                    emit(
                        FriendBalanceUi(
                            friendUserId = friendUserId,
                            displayName = label,
                            netByCurrency = nets,
                        ),
                    )
                }
            }

        /**
         * Observes overall balances for the signed-in user.
         *
         * @param viewerUserId Signed-in user id.
         * @return Cold [Flow] of [OverallBalancesUi].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeOverallBalances(viewerUserId: String): Flow<OverallBalancesUi> =
            combine(
                expenseRepository.observeInvolvingUser(viewerUserId),
                paymentRepository.observeInvolvingUser(viewerUserId),
                friendRepository.observeFriends(viewerUserId),
                groupRepository.observeGroupsForUser(viewerUserId),
            ) { expenses, payments, friends, groups ->
                OverallInputs(expenses, payments, friends, groups)
            }.flatMapLatest { inputs ->
                flow {
                    val splits = loadSplits(inputs.expenses)
                    val allNets =
                        BalanceCalculator.applyPayments(
                            BalanceCalculator.netBalancesByCurrency(inputs.expenses, splits),
                            inputs.payments,
                        )
                    val myNets =
                        allNets.mapNotNull { (currency, nets) ->
                            val mine = nets[viewerUserId] ?: return@mapNotNull null
                            if (mine.compareTo(ZERO) == 0) null else currency to mine
                        }.toMap()
                    val owedToMe = myNets.filterValues { it > ZERO }
                    val iOwe =
                        myNets
                            .filterValues { it < ZERO }
                            .mapValues { (_, v) -> v.abs() }

                    val friendBalances =
                        inputs.friends.map { friend ->
                            val friendPayments =
                                inputs.payments.filter { payment ->
                                    payment.groupId == null &&
                                        setOf(payment.fromUserId, payment.toUserId) ==
                                        setOf(viewerUserId, friend.friendUserId)
                                }
                            val nets =
                                BalanceCalculator.pairwiseNetByCurrency(
                                    viewerUserId = viewerUserId,
                                    otherUserId = friend.friendUserId,
                                    expenses = inputs.expenses,
                                    splitsByExpenseId = splits,
                                    payments = friendPayments,
                                )
                            FriendBalanceUi(
                                friendUserId = friend.friendUserId,
                                displayName = friend.displayNameSnapshot,
                                netByCurrency = nets,
                            )
                        }.filter { it.netByCurrency.isNotEmpty() }

                    val groupBalances =
                        inputs.groups.map { group ->
                            val groupExpenses = inputs.expenses.filter { it.groupId == group.id }
                            val groupPayments = inputs.payments.filter { it.groupId == group.id }
                            buildGroupBalance(
                                group.id,
                                viewerUserId,
                                groupExpenses,
                                group.name,
                                groupPayments,
                            )
                        }

                    val nonGroupExpenses = inputs.expenses.filter { it.groupId == null }
                    val nonGroupPayments = inputs.payments.filter { it.groupId == null }
                    val nonGroupBalance =
                        buildGroupBalance(
                            groupId = "",
                            viewerUserId = viewerUserId,
                            expenses = nonGroupExpenses,
                            knownName = "Non-group expenses",
                            payments = nonGroupPayments,
                        )

                    emit(
                        OverallBalancesUi(
                            totalOwedToMeByCurrency = owedToMe,
                            totalIOweByCurrency = iOwe,
                            friendBalances = friendBalances,
                            groupBalances = groupBalances,
                            nonGroupMyNetByCurrency = nonGroupBalance.myNetByCurrency,
                            nonGroupDebts =
                                nonGroupBalance.simplifiedDebts.filter { debt ->
                                    debt.fromUserId == viewerUserId || debt.toUserId == viewerUserId
                                },
                        ),
                    )
                }
            }

        private suspend fun buildGroupBalance(
            groupId: String,
            viewerUserId: String,
            expenses: List<Expense>,
            knownName: String? = null,
            payments: List<Payment> = emptyList(),
        ): GroupBalanceUi {
            val splits = loadSplits(expenses)
            val byCurrency =
                BalanceCalculator.applyPayments(
                    BalanceCalculator.netBalancesByCurrency(expenses, splits),
                    payments,
                )
            val transfers = DebtSimplifier.simplifyAll(byCurrency)
            val name =
                knownName
                    ?: groupRepository.getGroupById(groupId)?.name
                    ?: groupId.take(8)
            val labels = resolveLabels(viewerUserId, byCurrency, transfers)
            val myNets =
                byCurrency.mapNotNull { (currency, nets) ->
                    val mine = nets[viewerUserId] ?: return@mapNotNull null
                    if (mine.compareTo(ZERO) == 0) null else currency to mine
                }.toMap()
            return GroupBalanceUi(
                groupId = groupId,
                groupName = name,
                myNetByCurrency = myNets,
                memberNetsByCurrency = byCurrency,
                simplifiedDebts = transfers.map { it.toLabeled(labels) },
            )
        }

        private suspend fun loadSplits(expenses: List<Expense>): Map<String, List<ExpenseSplit>> =
            expenseRepository.getSplitsForExpenses(expenses.map { it.id })

        private suspend fun resolveLabels(
            viewerUserId: String,
            byCurrency: Map<String, Map<String, BigDecimal>>,
            transfers: List<DebtTransfer>,
        ): Map<String, String> {
            val ids =
                buildSet {
                    byCurrency.values.forEach { nets -> addAll(nets.keys) }
                    transfers.forEach {
                        add(it.fromUserId)
                        add(it.toUserId)
                    }
                    add(viewerUserId)
                }
            val friendLabels =
                friendRepository.observeFriends(viewerUserId).first()
                    .associate { it.friendUserId to it.displayNameSnapshot }
            return ids.associateWith { id ->
                when (id) {
                    viewerUserId -> "You"
                    else ->
                        friendLabels[id]
                            ?: userRepository.getUserById(id)?.displayName
                            ?: id.take(8)
                }
            }
        }

        private fun DebtTransfer.toLabeled(labels: Map<String, String>) =
            LabeledDebt(
                fromUserId = fromUserId,
                fromLabel = labels[fromUserId] ?: fromUserId.take(8),
                toUserId = toUserId,
                toLabel = labels[toUserId] ?: toUserId.take(8),
                amount = amount,
                currencyCode = currencyCode,
            )

        private data class OverallInputs(
            val expenses: List<Expense>,
            val payments: List<Payment>,
            val friends: List<com.splitease.app.domain.model.Friend>,
            val groups: List<com.splitease.app.domain.model.Group>,
        )

        companion object {
            private val ZERO = BigDecimal.ZERO.setScale(2)
        }
    }
