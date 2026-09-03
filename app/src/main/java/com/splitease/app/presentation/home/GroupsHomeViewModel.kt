package com.splitease.app.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.data.social.SocialInteractor
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.data.sync.SyncState
import com.splitease.app.data.sync.shouldFreezeBalances
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GroupsHomeUi(
    val currencyCode: String = AppCurrencies.DEFAULT,
    val balances: OverallBalancesUi? = null,
    val allGroups: List<Group> = emptyList(),
    /** True only while the first lite group list pull runs (Room empty). */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** First-login full hydrate phase; subsequent opens stay [SyncState.IDLE]. */
    val syncState: SyncState = SyncState.IDLE,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class GroupsHomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
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
                    withContext(Dispatchers.IO) {
                        val alreadyHydrated = syncInteractor.hasCompletedInitialHydrate(id)
                        val cached =
                            runCatching { groupRepository.observeGroupsForUser(id).first() }
                                .getOrDefault(emptyList())
                        if (!alreadyHydrated) {
                            // Pin IN_PROGRESS before any Room writes so summary cards never
                            // observe incrementing partial totals on first login.
                            syncInteractor.markInitialHydrateStarted(id)
                        }
                        if (cached.isNotEmpty() ||
                            alreadyHydrated ||
                            syncInteractor.wasSyncedRecently()
                        ) {
                            isInitialLoading.value = false
                        } else {
                            isInitialLoading.value = true
                            runCatching { socialInteractor.refreshGroupList(id) }
                            isInitialLoading.value = false
                        }
                    }
                    launch(Dispatchers.IO) {
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
                            combine(isInitialLoading, syncInteractor.syncState) { loading, sync ->
                                loading to sync
                            }.flatMapLatest { (loading, sync) ->
                                if (loading || sync.shouldFreezeBalances) {
                                    flowOf<OverallBalancesUi?>(null)
                                } else {
                                    balanceInteractor
                                        .observeOverallBalances(me)
                                        .flowOn(Dispatchers.Default)
                                }
                            },
                            groupRepository.observeGroupsForUser(me),
                            appSettingsRepository.observeCurrencyCode(),
                            isInitialLoading,
                            syncInteractor.syncState,
                        ) { balances, groups, currency, loading, sync ->
                            if (loading) {
                                GroupsHomeUi(
                                    currencyCode = currency,
                                    isLoading = true,
                                    syncState = sync,
                                )
                            } else {
                                GroupsHomeUi(
                                    currencyCode = currency,
                                    balances = balances,
                                    allGroups = groups,
                                    isLoading = false,
                                    syncState = sync,
                                )
                            }
                        }
                    }
                },
                isRefreshing,
                feedback,
            ) { home, refreshing, messages ->
                home.copy(
                    isRefreshing = refreshing,
                    infoMessage = messages.first,
                    errorMessage = messages.second,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsHomeUi())

        /** Flushes PENDING local writes and pulls cloud groups/expenses/payments. */
        fun refresh() {
            val id = userId.value ?: return
            if (isInitialLoading.value) return
            if (syncInteractor.syncState.value == SyncState.IN_PROGRESS) return
            viewModelScope.launch {
                isRefreshing.update { true }
                withContext(Dispatchers.IO) {
                    if (syncInteractor.syncState.value == SyncState.FAILED) {
                        syncInteractor.markInitialHydrateStarted(id)
                    }
                    runCatching { syncInteractor.syncForUser(id, force = true) }
                }
                isRefreshing.update { false }
            }
        }

        /**
         * Retries a failed first-login hydrate. Cards return to skeleton until COMPLETE.
         */
        fun retryInitialHydrate() {
            val id = userId.value ?: return
            if (syncInteractor.syncState.value == SyncState.IN_PROGRESS) return
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    syncInteractor.markInitialHydrateStarted(id)
                    runCatching { syncInteractor.syncForUser(id, force = true) }
                }
            }
        }

        /** Copies [photoUri] into app storage and updates the group's list icon. */
        fun updateGroupPhoto(
            groupId: String,
            photoUri: String,
        ) {
            if (photoUri.isBlank()) return
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        socialInteractor.updateGroupPhoto(groupId, photoUri)
                    }
                feedback.value =
                    if (result.isSuccess) {
                        null to null
                    } else {
                        null to ErrorMessages.messageOrNull(
                            appContext,
                            TAG,
                            result.exceptionOrNull(),
                        )
                    }
            }
        }

        private companion object {
            const val TAG = "GroupsHomeViewModel"
        }
    }
