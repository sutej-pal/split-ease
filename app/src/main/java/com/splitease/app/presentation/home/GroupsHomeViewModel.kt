package com.splitease.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsHomeUi(
    val currencyCode: String = AppCurrencies.DEFAULT,
    val balances: OverallBalancesUi? = null,
    val allGroups: List<Group> = emptyList(),
    /** True only while the first lite group list pull runs (Room empty). */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class GroupsHomeViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        private val balanceInteractor: BalanceInteractor,
        private val groupRepository: GroupRepository,
        appSettingsRepository: AppSettingsRepository,
        private val syncInteractor: SyncInteractor,
        private val socialInteractor: SocialInteractor,
    ) : ViewModel() {
        private val userId: StateFlow<String?> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val isInitialLoading = MutableStateFlow(true)
        private val isRefreshing = MutableStateFlow(false)
        private val feedback = MutableStateFlow<Pair<String?, String?>>(null to null)

        init {
            viewModelScope.launch {
                userId.collect { id ->
                    if (id == null) {
                        isInitialLoading.value = true
                        return@collect
                    }
                    // 1) Paint from Room immediately when anything is cached.
                    val cached = runCatching { groupRepository.observeGroupsForUser(id).first() }
                        .getOrDefault(emptyList())
                    if (cached.isNotEmpty() || syncInteractor.wasSyncedRecently()) {
                        isInitialLoading.value = false
                    } else {
                        // 2) First login / empty DB: wait only for a lite group-list pull
                        //    (no members/profiles/expenses).
                        isInitialLoading.value = true
                        runCatching { socialInteractor.refreshGroupList(id) }
                        isInitialLoading.value = false
                    }
                    // 3) Full hydrate (members, expenses, balances) never blocks Home.
                    launch {
                        runCatching { syncInteractor.syncForUser(id) }
                    }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val ui: StateFlow<GroupsHomeUi> =
            combine(
                userId.flatMapLatest { me ->
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
                },
                isInitialLoading,
                isRefreshing,
                feedback,
            ) { home, loading, refreshing, messages ->
                home.copy(
                    isLoading = loading,
                    isRefreshing = refreshing,
                    infoMessage = messages.first,
                    errorMessage = messages.second,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsHomeUi())

        /** Flushes PENDING local writes and pulls cloud groups/expenses/payments. */
        fun refresh() {
            val id = userId.value ?: return
            if (isInitialLoading.value) return
            viewModelScope.launch {
                isRefreshing.update { true }
                runCatching { syncInteractor.syncForUser(id, force = true) }
                isRefreshing.update { false }
            }
        }

        /** Copies [photoUri] into app storage and updates the group's list icon. */
        fun updateGroupPhoto(
            groupId: String,
            photoUri: String,
        ) {
            if (photoUri.isBlank()) return
            viewModelScope.launch {
                val result = socialInteractor.updateGroupPhoto(groupId, photoUri)
                feedback.value =
                    if (result.isSuccess) {
                        null to null
                    } else {
                        null to (result.exceptionOrNull()?.message ?: "Could not update photo.")
                    }
            }
        }
    }
