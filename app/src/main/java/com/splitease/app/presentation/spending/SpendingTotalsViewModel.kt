package com.splitease.app.presentation.spending

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.spending.SpendingInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.spending.CategorySpending
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
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

enum class SpendingPeriod {
    THIS_MONTH,
    LAST_30_DAYS,
    ALL_TIME,
}

@HiltViewModel
class SpendingTotalsViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        authRepository: AuthRepository,
        private val spendingInteractor: SpendingInteractor,
    ) : ViewModel() {
        private val userId =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _period = MutableStateFlow(SpendingPeriod.THIS_MONTH)
        val period: StateFlow<SpendingPeriod> = _period

        @OptIn(ExperimentalCoroutinesApi::class)
        val totals: StateFlow<List<CategorySpending>> =
            combine(userId, _period) { me, period -> me to period }
                .flatMapLatest { (me, period) ->
                    if (me == null) {
                        flowOf(emptyList())
                    } else {
                        val (from, to) = period.bounds()
                        spendingInteractor.observeTotals(
                            viewerUserId = me,
                            fromEpochMs = from,
                            toEpochMs = to,
                            uncategorizedLabel = appContext.getString(R.string.uncategorized_label),
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun setPeriod(period: SpendingPeriod) {
            _period.value = period
        }

        private fun SpendingPeriod.bounds(): Pair<Long, Long> {
            val now = System.currentTimeMillis()
            return when (this) {
                SpendingPeriod.ALL_TIME -> 0L to now
                SpendingPeriod.LAST_30_DAYS -> (now - 30L * 24 * 60 * 60 * 1000) to now
                SpendingPeriod.THIS_MONTH -> {
                    val cal =
                        Calendar.getInstance(TimeZone.getDefault()).apply {
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                    cal.timeInMillis to now
                }
            }
        }
    }
