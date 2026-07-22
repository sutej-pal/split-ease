package com.splitease.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GroupsHomeUi(
    val currencyCode: String = AppCurrencies.DEFAULT,
    val balances: OverallBalancesUi? = null,
    val allGroups: List<Group> = emptyList(),
)

@HiltViewModel
class GroupsHomeViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val balanceInteractor: BalanceInteractor,
        groupRepository: GroupRepository,
        appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val ui: StateFlow<GroupsHomeUi> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(GroupsHomeUi())
                    } else {
                        combine(
                            balanceInteractor.observeOverallBalances(me),
                            groupRepository.observeGroupsForUser(me),
                            appSettingsRepository.observeCurrencyCode(),
                        ) { balances, groups, currency ->
                            GroupsHomeUi(
                                currencyCode = currency,
                                balances = balances,
                                allGroups = groups,
                            )
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsHomeUi())
    }
