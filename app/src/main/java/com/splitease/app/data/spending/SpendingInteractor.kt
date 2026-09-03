package com.splitease.app.data.spending

import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.spending.CategorySpending
import com.splitease.app.domain.spending.SpendingTotalsCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes spending totals for the signed-in user over a date window.
 */
@Singleton
class SpendingInteractor
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
    ) {
        /**
         * @param viewerUserId Signed-in user.
         * @param fromEpochMs Inclusive start.
         * @param toEpochMs Inclusive end.
         * @param uncategorizedLabel UI label for null categories.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeTotals(
            viewerUserId: String,
            fromEpochMs: Long,
            toEpochMs: Long,
            uncategorizedLabel: String,
        ): Flow<List<CategorySpending>> =
            combine(
                expenseRepository.observeInPeriod(fromEpochMs, toEpochMs),
                categoryRepository.observeCategories(),
            ) { expenses, categories ->
                expenses to categories
            }.flatMapLatest { (expenses, categories) ->
                flow {
                    val splits = expenseRepository.getSplitsForExpenses(expenses.map { it.id })
                    val viewerExpenses =
                        expenses.filter { expense ->
                            splits[expense.id].orEmpty().any { it.userId == viewerUserId } ||
                                expense.paidByUserId == viewerUserId
                        }
                    emit(
                        SpendingTotalsCalculator.byCategory(
                            viewerUserId = viewerUserId,
                            expenses = viewerExpenses,
                            splitsByExpenseId = splits,
                            categoryNames = categories.associate { it.id to it.name },
                            uncategorizedLabel = uncategorizedLabel,
                        ),
                    )
                }
            }
    }
