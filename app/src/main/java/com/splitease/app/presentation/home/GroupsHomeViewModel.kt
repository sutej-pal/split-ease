package com.splitease.app.presentation.home

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GroupsHomeUi(
    val currencyCode: String = AppCurrencies.DEFAULT,
    val balances: OverallBalancesUi? = null,
    val allGroups: List<Group> = emptyList(),
    /**
     * True only while the first lite group list pull runs (Room empty).
     * The screen still shows the list chrome + skeletons instead of a full-screen blocker.
     */
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
                            // Pause live balance math during first-login hydrate and pull-to-refresh
                            // so Room write storms do not recompute friend×group nets on every row.
                            combine(
                                isInitialLoading,
                                isRefreshing,
                                syncInteractor.syncState,
                            ) { loading, refreshing, sync ->
                                Triple(loading, refreshing, sync)
                            }.flatMapLatest { (loading, refreshing, sync) ->
                                if (loading || refreshing || sync.shouldFreezeBalances) {
                                    // Pause Room observation during write storms, but do not
                                    // wipe the last snapshot (filters / non-group rows).
                                    flowOf<OverallBalancesUi?>(null)
                                } else {
                                    balanceInteractor
                                        .observeOverallBalances(
                                            viewerUserId = me,
                                            includeFriendBalances = false,
                                        )
                                        // Emit immediately so the group list is not gated on
                                        // the first (often expensive) balance pass.
                                        .map<OverallBalancesUi, OverallBalancesUi?> { it }
                                        .onStart { emit(null) }
                                        .flowOn(Dispatchers.Default)
                                }
                            }.runningFold(null as OverallBalancesUi?) { held, next ->
                                next ?: held
                            },
                            groupRepository.observeGroupsForUser(me),
                            appSettingsRepository.observeCurrencyCode(),
                            isInitialLoading,
                            syncInteractor.syncState,
                        ) { balances, groups, currency, loading, sync ->
                            GroupsHomeUi(
                                currencyCode = currency,
                                // Keep listing Room groups while the lite pull / balances catch up.
                                balances = if (loading) null else balances,
                                allGroups = groups,
                                isLoading = loading,
                                syncState = sync,
                            )
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
            viewModelScope.launch {
                val startMs = System.currentTimeMillis()
                Log.d("GroupsRefresh", "refresh() started for user $id")
                isRefreshing.update { true }
                try {
                    withContext(Dispatchers.IO) {
                        if (syncInteractor.syncState.value == SyncState.FAILED) {
                            syncInteractor.markInitialHydrateStarted(id)
                        }
                        val syncStart = System.currentTimeMillis()
                        runCatching { syncInteractor.syncForUser(id, force = true) }
                            .onSuccess {
                                Log.d(
                                    "GroupsRefresh",
                                    "syncForUser(force) ok in ${System.currentTimeMillis() - syncStart}ms",
                                )
                            }
                            .onFailure { err ->
                                Log.w(
                                    "GroupsRefresh",
                                    "syncForUser(force) failed in ${System.currentTimeMillis() - syncStart}ms",
                                    err,
                                )
                            }
                    }
                } finally {
                    isRefreshing.update { false }
                }
                Log.d("GroupsRefresh", "refresh() completed in ${System.currentTimeMillis() - startMs}ms")
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
