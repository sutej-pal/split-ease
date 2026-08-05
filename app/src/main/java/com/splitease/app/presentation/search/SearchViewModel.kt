package com.splitease.app.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.presentation.expenses.LedgerBalanceSide
import com.splitease.app.presentation.expenses.LedgerListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository,
        private val friendRepository: FriendRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")

        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val results: StateFlow<List<LedgerListItem>> =
            combine(query, userId) { q, me -> q to me }
                .flatMapLatest { (q, me) ->
                    if (q.isBlank() || me == null) {
                        flowOf(emptyList())
                    } else {
                        expenseRepository.search(q).flatMapLatest { expenses ->
                            combine(
                                expenseRepository.observeSplitsForExpenses(expenses.map { it.id }),
                                groupRepository.observeGroupsForUser(me),
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { splits, groups, users, friends ->
                                val groupNames = groups.associate { it.id to it.name }
                                val userNames = users.associate { it.id to it.displayName }
                                val friendNames =
                                    friends.associate { it.friendUserId to it.displayNameSnapshot }

                                fun nameOf(id: String): String =
                                    when (id) {
                                        me -> "You"
                                        else ->
                                            friendNames[id]
                                                ?: userNames[id]
                                                ?: id.take(8)
                                    }

                                expenses.map { expense ->
                                    expense.toSearchItem(
                                        me = me,
                                        groupNames = groupNames,
                                        nameOf = ::nameOf,
                                        splits = splits[expense.id].orEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun search(text: String) {
            query.value = text
        }

        private fun Expense.toSearchItem(
            me: String,
            groupNames: Map<String, String>,
            nameOf: (String) -> String,
            splits: List<ExpenseSplit>,
        ): LedgerListItem {
            val actorLabel = nameOf(paidByUserId)
            val contextLabel =
                groupId?.let { groupNames[it] }
                    ?: appContext.getString(R.string.non_group_expenses)
            val sortEpochMs = expenseDateEpochMs.coerceAtLeast(createdAtEpochMs)
            val (balanceSide, balanceAmount) = viewerBalance(me, this, splits)
            return LedgerListItem(
                id = "expense-$id",
                isPayment = false,
                title =
                    appContext.getString(
                        R.string.activity_added_in,
                        actorLabel,
                        description,
                        contextLabel,
                    ),
                subtitle = formatDateTime(sortEpochMs),
                sortEpochMs = sortEpochMs,
                categoryIconKey = "category_general",
                currencyCode = currencyCode,
                balanceSide = balanceSide,
                balanceAmount = balanceAmount,
            )
        }

        private fun viewerBalance(
            me: String,
            expense: Expense,
            splits: List<ExpenseSplit>,
        ): Pair<LedgerBalanceSide?, BigDecimal?> {
            val zero = BigDecimal.ZERO.setScale(2)
            val myShare =
                splits
                    .firstOrNull { it.userId == me }
                    ?.owedAmount
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?: zero
            val total = expense.amount.setScale(2, RoundingMode.HALF_UP)
            val net =
                if (expense.paidByUserId == me) {
                    total.subtract(myShare)
                } else {
                    myShare.negate()
                }
            return when {
                net.compareTo(zero) > 0 -> LedgerBalanceSide.LENT to net
                net.compareTo(zero) < 0 -> LedgerBalanceSide.BORROWED to net.abs()
                else -> null to null
            }
        }

        private fun formatDateTime(epochMs: Long): String =
            DateFormat
                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(epochMs))
    }
