package com.splitease.app.presentation.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.spending.GroupMonthSpending
import com.splitease.app.domain.spending.GroupSpendingCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class GroupTotalsUi(
    val groupId: String = "",
    val groupName: String = "",
    val currencyCode: String = AppCurrencies.DEFAULT,
    val allTime: Boolean = false,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val totalSpent: BigDecimal = BigDecimal.ZERO.setScale(2),
    val yourShare: BigDecimal = BigDecimal.ZERO.setScale(2),
    val sharePercent: Int? = null,
    val chartBars: List<GroupMonthSpending> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupTotalsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        authRepository: AuthRepository,
        groupRepository: GroupRepository,
        expenseRepository: ExpenseRepository,
    ) : ViewModel() {
        private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()
        private val timeZone: TimeZone = TimeZone.getDefault()

        private val userId =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val period =
            MutableStateFlow(
                PeriodState(
                    allTime = false,
                    year = Calendar.getInstance(timeZone).get(Calendar.YEAR),
                    month = Calendar.getInstance(timeZone).get(Calendar.MONTH),
                ),
            )

        val ui: StateFlow<GroupTotalsUi> =
            combine(
                groupRepository.observeGroupById(groupId),
                userId.flatMapLatest { me ->
                    if (me == null || groupId.isBlank()) {
                        flowOf(
                            emptyList<Expense>() to emptyMap<String, List<ExpenseSplit>>(),
                        )
                    } else {
                        combine(
                            expenseRepository.observeExpenses(groupId),
                            expenseRepository.observeSplitsByGroup(groupId),
                        ) { expenses, splits -> expenses to splits }
                    }
                },
                period,
                userId,
            ) { group, expenseData, periodState, me ->
                val (expenses, splits) = expenseData
                val currency =
                    group?.defaultCurrencyCode?.takeIf { it.isNotBlank() } ?: AppCurrencies.DEFAULT
                val chartEndYear: Int
                val chartEndMonth: Int
                val fromMs: Long
                val toMs: Long
                if (periodState.allTime) {
                    val now = Calendar.getInstance(timeZone)
                    chartEndYear = now.get(Calendar.YEAR)
                    chartEndMonth = now.get(Calendar.MONTH)
                    fromMs = 0L
                    toMs = System.currentTimeMillis()
                } else {
                    chartEndYear = periodState.year
                    chartEndMonth = periodState.month
                    val bounds =
                        GroupSpendingCalculator.monthBounds(
                            periodState.year,
                            periodState.month,
                            timeZone,
                        )
                    fromMs = bounds.first
                    toMs = bounds.second
                }
                val (total, share) =
                    if (me == null) {
                        BigDecimal.ZERO.setScale(2) to BigDecimal.ZERO.setScale(2)
                    } else {
                        GroupSpendingCalculator.periodTotals(
                            viewerUserId = me,
                            expenses = expenses,
                            splitsByExpenseId = splits,
                            currencyCode = currency,
                            fromEpochMs = fromMs,
                            toEpochMs = toMs,
                        )
                    }
                val bars =
                    if (me == null) {
                        emptyList()
                    } else {
                        GroupSpendingCalculator.monthlyBuckets(
                            viewerUserId = me,
                            expenses = expenses,
                            splitsByExpenseId = splits,
                            currencyCode = currency,
                            endYear = chartEndYear,
                            endMonth = chartEndMonth,
                            monthCount = 3,
                            timeZone = timeZone,
                        )
                    }
                GroupTotalsUi(
                    groupId = groupId,
                    groupName = group?.name.orEmpty(),
                    currencyCode = currency,
                    allTime = periodState.allTime,
                    selectedYear = periodState.year,
                    selectedMonth = periodState.month,
                    totalSpent = total,
                    yourShare = share,
                    sharePercent = GroupSpendingCalculator.sharePercent(total, share),
                    chartBars = bars,
                    isLoading = group == null && groupId.isNotBlank(),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                GroupTotalsUi(groupId = groupId, isLoading = true),
            )

        fun selectAllTime() {
            period.update { it.copy(allTime = true) }
        }

        fun selectMonthMode() {
            period.update { it.copy(allTime = false) }
        }

        fun previousMonth() {
            period.update { state ->
                val cal =
                    Calendar.getInstance(timeZone).apply {
                        set(Calendar.YEAR, state.year)
                        set(Calendar.MONTH, state.month)
                        add(Calendar.MONTH, -1)
                    }
                state.copy(
                    allTime = false,
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH),
                )
            }
        }

        fun nextMonth() {
            period.update { state ->
                val cal =
                    Calendar.getInstance(timeZone).apply {
                        set(Calendar.YEAR, state.year)
                        set(Calendar.MONTH, state.month)
                        add(Calendar.MONTH, 1)
                    }
                val now = Calendar.getInstance(timeZone)
                // Don't navigate past the current calendar month.
                if (cal.get(Calendar.YEAR) > now.get(Calendar.YEAR) ||
                    (
                        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            cal.get(Calendar.MONTH) > now.get(Calendar.MONTH)
                    )
                ) {
                    return@update state
                }
                state.copy(
                    allTime = false,
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH),
                )
            }
        }

        fun selectChartMonth(year: Int, month: Int) {
            period.update {
                it.copy(allTime = false, year = year, month = month)
            }
        }

        private data class PeriodState(
            val allTime: Boolean,
            val year: Int,
            val month: Int,
        )
    }
