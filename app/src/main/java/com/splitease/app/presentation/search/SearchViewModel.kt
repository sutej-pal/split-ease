package com.splitease.app.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.domain.balance.BalanceCalculator
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.presentation.common.MoneyFormat
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
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val expenseRepository: ExpenseRepository,
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
                                userRepository.observeUsers(),
                                friendRepository.observeFriends(me),
                            ) { splits, users, friends ->
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
            nameOf: (String) -> String,
            splits: List<ExpenseSplit>,
        ): LedgerListItem {
            val payerLabel = nameOf(paidByUserId)
            val sortEpochMs = expenseDateEpochMs.takeIf { it > 0L } ?: createdAtEpochMs
            val (balanceSide, balanceAmount) = viewerBalance(me, this, splits)
            return LedgerListItem(
                id = "expense-$id",
                isPayment = false,
                title = description,
                subtitle =
                    appContext.getString(
                        R.string.ledger_paid_by,
                        payerLabel,
                        MoneyFormat.format(amount, currencyCode),
                    ),
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
            val net = BalanceCalculator.viewerNetForExpense(me, expense, splits)
            return when {
                net > zero -> LedgerBalanceSide.LENT to net
                net < zero -> LedgerBalanceSide.BORROWED to net.abs()
                else -> null to null
            }
        }
    }
