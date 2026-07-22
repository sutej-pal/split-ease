package com.splitease.app.presentation.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.FriendBalanceUi
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BalancesViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val balanceInteractor: BalanceInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository.observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val overall: StateFlow<OverallBalancesUi?> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(null)
                    } else {
                        balanceInteractor.observeOverallBalances(me)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeGroupBalance(groupId: String): StateFlow<GroupBalanceUi?> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(null)
                    } else {
                        balanceInteractor.observeGroupBalance(groupId, me)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        @OptIn(ExperimentalCoroutinesApi::class)
        fun observeFriendBalance(friendUserId: String): StateFlow<FriendBalanceUi?> =
            userId
                .flatMapLatest { me ->
                    if (me == null) {
                        flowOf(null)
                    } else {
                        balanceInteractor.observeFriendBalance(me, friendUserId)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
