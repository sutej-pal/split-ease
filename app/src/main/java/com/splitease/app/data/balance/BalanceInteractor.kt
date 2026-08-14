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
import com.splitease.app.domain.settings.AppSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    val fromPhotoUrl: String? = null,
    val toPhotoUrl: String? = null,
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
 * Pairwise balance for a friend within one context (a group or non-group).
 *
 * @property contextId Group id, or blank for non-group expenses.
 * @property contextName Display name (group name or "Non-group expenses").
 * @property netByCurrency Positive = friend owes me in this context; negative = I owe friend.
 */
data class FriendContextBalanceUi(
    val contextId: String,
    val contextName: String,
    val netByCurrency: Map<String, BigDecimal>,
)

/**
 * Friend pairwise balance (viewer perspective).
 *
 * @property friendUserId Friend's user id.
 * @property displayName Friend label.
 * @property netByCurrency Positive = friend owes me; negative = I owe friend.
 * @property contexts Per-group / non-group breakdowns (shown when a friend is shared across contexts).
 */
data class FriendBalanceUi(
    val friendUserId: String,
    val displayName: String,
    val netByCurrency: Map<String, BigDecimal>,
    val contexts: List<FriendContextBalanceUi> = emptyList(),
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
 * @property hasNonGroupActivity True when any non-group expense or payment exists.
 */
data class OverallBalancesUi(
    val totalOwedToMeByCurrency: Map<String, BigDecimal>,
    val totalIOweByCurrency: Map<String, BigDecimal>,
    val friendBalances: List<FriendBalanceUi>,
    val groupBalances: List<GroupBalanceUi>,
    val nonGroupMyNetByCurrency: Map<String, BigDecimal> = emptyMap(),
    val nonGroupDebts: List<LabeledDebt> = emptyList(),
    val hasNonGroupActivity: Boolean = false,
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
        private val appSettingsRepository: AppSettingsRepository,
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
                appSettingsRepository.observeSimplifyGroupDebts(groupId),
                observeUserLooks(),
            ) { expenses, payments, simplify, userLooks ->
                GroupBalanceInputs(expenses, payments, simplify, userLooks)
            }.flatMapLatest { inputs ->
                flow {
                    emit(
                        buildGroupBalance(
                            groupId = groupId,
                            viewerUserId = viewerUserId,
                            expenses = inputs.expenses,
                            payments = inputs.payments,
                            simplifyDebts = inputs.simplifyDebts,
                            userLooks = inputs.userLooks,
                        ),
                    )
                }
            }

        /**
         * Observes aggregate balance for all non-group (1:1) expenses involving the viewer.
         *
         * @param viewerUserId Signed-in user id.
         * @return Cold [Flow] of [GroupBalanceUi] (virtual group id is blank).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeNonGroupBalance(viewerUserId: String): Flow<GroupBalanceUi> =
            combine(
                expenseRepository.observeInvolvingUser(viewerUserId),
                paymentRepository.observeInvolvingUser(viewerUserId),
                observeUserLooks(),
            ) { expenses, payments, userLooks ->
                Triple(
                    expenses.filter { it.groupId == null },
                    payments.filter { it.groupId == null },
                    userLooks,
                )
            }.flatMapLatest { (expenses, payments, userLooks) ->
                flow {
                    emit(
                        buildGroupBalance(
                            groupId = "",
                            viewerUserId = viewerUserId,
                            expenses = expenses,
                            knownName = "Non-group expenses",
                            payments = payments,
                            simplifyDebts = true,
                            userLooks = userLooks,
                        ),
                    )
                }
            }

        /**
         * Observes overall 1:1 + shared-group nets between viewer and a friend,
         * including a per-context breakdown for the expandable friend detail header.
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
                expenseRepository.observeInvolvingUser(viewerUserId),
                paymentRepository.observeInvolvingUser(viewerUserId),
                friendRepository.observeFriends(viewerUserId),
                groupRepository.observeGroupsForUser(viewerUserId),
            ) { expenses, payments, friends, groups ->
                FriendBalanceInputs(expenses, payments, friends, groups)
            }.flatMapLatest { inputs ->
                flow {
                    val splits = loadSplits(inputs.expenses)
                    val label =
                        inputs.friends
                            .firstOrNull { it.friendUserId == friendUserId }
                            ?.displayNameSnapshot
                            ?: userRepository.getUserById(friendUserId)?.displayName
                            ?: friendUserId.take(8)

                    val contexts = mutableListOf<FriendContextBalanceUi>()
                    inputs.groups.forEach { group ->
                        val groupExpenses = inputs.expenses.filter { it.groupId == group.id }
                        val groupPayments = inputs.payments.filter { it.groupId == group.id }
                        val groupNets =
                            BalanceCalculator.pairwiseNetByCurrency(
                                viewerUserId = viewerUserId,
                                otherUserId = friendUserId,
                                expenses = groupExpenses,
                                splitsByExpenseId = splits,
                                payments = groupPayments,
                            )
                        if (groupNets.isNotEmpty()) {
                            contexts +=
                                FriendContextBalanceUi(
                                    contextId = group.id,
                                    contextName = group.name,
                                    netByCurrency = groupNets,
                                )
                        }
                    }
                    val nonGroupExpenses = inputs.expenses.filter { it.groupId == null }
                    val nonGroupPayments = inputs.payments.filter { it.groupId == null }
                    val nonGroupNets =
                        BalanceCalculator.pairwiseNetByCurrency(
                            viewerUserId = viewerUserId,
                            otherUserId = friendUserId,
                            expenses = nonGroupExpenses,
                            splitsByExpenseId = splits,
                            payments = nonGroupPayments,
                        )
                    if (nonGroupNets.isNotEmpty()) {
                        contexts +=
                            FriendContextBalanceUi(
                                contextId = "",
                                contextName = "Non-group expenses",
                                netByCurrency = nonGroupNets,
                            )
                    }
                    val overallNets =
                        if (contexts.isNotEmpty()) {
                            sumNetsByCurrency(contexts.map { it.netByCurrency })
                        } else {
                            BalanceCalculator.pairwiseNetByCurrency(
                                viewerUserId = viewerUserId,
                                otherUserId = friendUserId,
                                expenses = inputs.expenses,
                                splitsByExpenseId = splits,
                                payments = inputs.payments,
                            )
                        }
                    emit(
                        FriendBalanceUi(
                            friendUserId = friendUserId,
                            displayName = label,
                            netByCurrency = overallNets,
                            contexts = contexts,
                        ),
                    )
                }
            }

        private data class FriendBalanceInputs(
            val expenses: List<Expense>,
            val payments: List<Payment>,
            val friends: List<com.splitease.app.domain.model.Friend>,
            val groups: List<com.splitease.app.domain.model.Group>,
        )

        /**
         * Observes overall balances for the signed-in user.
         *
         * @param viewerUserId Signed-in user id.
         * @return Cold [Flow] of [OverallBalancesUi].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeOverallBalances(viewerUserId: String): Flow<OverallBalancesUi> =
            combine(
                combine(
                    expenseRepository.observeInvolvingUser(viewerUserId),
                    paymentRepository.observeInvolvingUser(viewerUserId),
                    friendRepository.observeFriends(viewerUserId),
                    groupRepository.observeGroupsForUser(viewerUserId),
                    appSettingsRepository.observeSimplifyGroupDebtsMap(),
                ) { expenses, payments, friends, groups, simplifyMap ->
                    OverallInputs(expenses, payments, friends, groups, simplifyMap)
                },
                observeUserLooks(),
            ) { inputs, userLooks ->
                inputs to userLooks
            }.flatMapLatest { (inputs, userLooks) ->
                flow {
                    val splits = loadSplits(inputs.expenses)
                    val allNets =
                        BalanceCalculator.applyPayments(
                            BalanceCalculator.netBalancesByCurrency(inputs.expenses, splits),
                            inputs.payments,
                        )
                    val myNets =
                        allNets
                            .mapNotNull { (currency, nets) ->
                            val mine = nets[viewerUserId] ?: return@mapNotNull null
                            if (mine.compareTo(ZERO) == 0) null else currency to mine
                        }.toMap()
                    val owedToMe = myNets.filterValues { it > ZERO }
                    val iOwe =
                        myNets
                            .filterValues { it < ZERO }
                            .mapValues { (_, v) -> v.abs() }

                    val friendBalances =
                        inputs.friends
                            .map { friend ->
                            val contexts = mutableListOf<FriendContextBalanceUi>()
                            inputs.groups.forEach { group ->
                                val groupExpenses =
                                    inputs.expenses.filter { it.groupId == group.id }
                                val groupPayments =
                                    inputs.payments.filter { it.groupId == group.id }
                                val groupNets =
                                    BalanceCalculator.pairwiseNetByCurrency(
                                        viewerUserId = viewerUserId,
                                        otherUserId = friend.friendUserId,
                                        expenses = groupExpenses,
                                        splitsByExpenseId = splits,
                                        payments = groupPayments,
                                    )
                                if (groupNets.isNotEmpty()) {
                                    contexts +=
                                        FriendContextBalanceUi(
                                            contextId = group.id,
                                            contextName = group.name,
                                            netByCurrency = groupNets,
                                        )
                                }
                            }
                            val nonGroupExpenses =
                                inputs.expenses.filter { it.groupId == null }
                            val nonGroupPayments =
                                inputs.payments.filter { it.groupId == null }
                            val nonGroupNets =
                                BalanceCalculator.pairwiseNetByCurrency(
                                    viewerUserId = viewerUserId,
                                    otherUserId = friend.friendUserId,
                                    expenses = nonGroupExpenses,
                                    splitsByExpenseId = splits,
                                    payments = nonGroupPayments,
                                )
                            if (nonGroupNets.isNotEmpty()) {
                                contexts +=
                                    FriendContextBalanceUi(
                                        contextId = "",
                                        contextName = "Non-group expenses",
                                        netByCurrency = nonGroupNets,
                                    )
                            }
                            val overallNets = sumNetsByCurrency(contexts.map { it.netByCurrency })
                            FriendBalanceUi(
                                friendUserId = friend.friendUserId,
                                displayName = friend.displayNameSnapshot,
                                netByCurrency = overallNets,
                                contexts = contexts,
                            )
                        }.filter { it.netByCurrency.isNotEmpty() }

                    val groupBalances =
                        inputs.groups.map { group ->
                            val groupExpenses = inputs.expenses.filter { it.groupId == group.id }
                            val groupPayments = inputs.payments.filter { it.groupId == group.id }
                            buildGroupBalance(
                                groupId = group.id,
                                viewerUserId = viewerUserId,
                                expenses = groupExpenses,
                                knownName = group.name,
                                payments = groupPayments,
                                simplifyDebts = inputs.simplifyMap[group.id] ?: true,
                                userLooks = userLooks,
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
                            simplifyDebts = true,
                            userLooks = userLooks,
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
                            hasNonGroupActivity =
                                nonGroupExpenses.isNotEmpty() || nonGroupPayments.isNotEmpty(),
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
            simplifyDebts: Boolean = true,
            userLooks: Map<String, MemberLook> = emptyMap(),
        ): GroupBalanceUi {
            val splits = loadSplits(expenses)
            val byCurrency =
                BalanceCalculator.applyPayments(
                    BalanceCalculator.netBalancesByCurrency(expenses, splits),
                    payments,
                )
            val transfers =
                if (simplifyDebts) {
                    DebtSimplifier.simplifyAll(byCurrency)
                } else {
                    DebtSimplifier.fromExpenses(expenses, splits, payments)
                }
            val name =
                knownName
                    ?: groupRepository.getGroupById(groupId)?.name
                    ?: groupId.take(8)
            val labels = resolveLabels(viewerUserId, byCurrency, transfers, userLooks)
            val myNets =
                byCurrency
                    .mapNotNull { (currency, nets) ->
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

        private fun observeUserLooks(): Flow<Map<String, MemberLook>> =
            userRepository
                .observeUsers()
                .map { users ->
                    users.associate { user ->
                        user.id to MemberLook(label = user.displayName, photoUrl = user.photoUrl)
                    }
                }.distinctUntilChanged()

        private suspend fun resolveLabels(
            viewerUserId: String,
            byCurrency: Map<String, Map<String, BigDecimal>>,
            transfers: List<DebtTransfer>,
            userLooks: Map<String, MemberLook> = emptyMap(),
        ): Map<String, MemberLook> {
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
                friendRepository
                    .observeFriends(viewerUserId)
                    .first()
                    .associate { it.friendUserId to it.displayNameSnapshot }
            return ids.associateWith { id ->
                val look = userLooks[id]
                val user = if (look != null) null else userRepository.getUserById(id)
                MemberLook(
                    label =
                        when (id) {
                            viewerUserId -> "You"
                            else ->
                                friendLabels[id]
                                    ?: look?.label
                                    ?: user?.displayName
                                    ?: id.take(8)
                        },
                    photoUrl = look?.photoUrl ?: user?.photoUrl,
                )
            }
        }

        private data class GroupBalanceInputs(
            val expenses: List<Expense>,
            val payments: List<Payment>,
            val simplifyDebts: Boolean,
            val userLooks: Map<String, MemberLook>,
        )

        private fun DebtTransfer.toLabeled(labels: Map<String, MemberLook>) =
            LabeledDebt(
                fromUserId = fromUserId,
                fromLabel = labels[fromUserId]?.label ?: fromUserId.take(8),
                toUserId = toUserId,
                toLabel = labels[toUserId]?.label ?: toUserId.take(8),
                amount = amount,
                currencyCode = currencyCode,
                fromPhotoUrl = labels[fromUserId]?.photoUrl,
                toPhotoUrl = labels[toUserId]?.photoUrl,
            )

        private data class MemberLook(
            val label: String,
            val photoUrl: String?,
        )

        private data class OverallInputs(
            val expenses: List<Expense>,
            val payments: List<Payment>,
            val friends: List<com.splitease.app.domain.model.Friend>,
            val groups: List<com.splitease.app.domain.model.Group>,
            val simplifyMap: Map<String, Boolean>,
        )

        /** Sums viewer nets across contexts; drops currencies that cancel to zero. */
        private fun sumNetsByCurrency(
            netsList: List<Map<String, BigDecimal>>,
        ): Map<String, BigDecimal> {
            if (netsList.isEmpty()) return emptyMap()
            val totals = linkedMapOf<String, BigDecimal>()
            netsList.forEach { nets ->
                nets.forEach { (currency, amount) ->
                    totals[currency] = (totals[currency] ?: BigDecimal.ZERO) + amount
                }
            }
            return totals.filterValues { it.compareTo(ZERO) != 0 }
        }

        companion object {
            private val ZERO = BigDecimal.ZERO.setScale(2)
        }
    }
