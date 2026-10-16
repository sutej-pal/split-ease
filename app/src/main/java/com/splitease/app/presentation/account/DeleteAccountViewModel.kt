package com.splitease.app.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.core.ErrorMessages
import com.splitease.app.data.balance.BalanceInteractor
import com.splitease.app.data.balance.OverallBalancesUi
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.account.AccountDeletionBalanceSlice
import com.splitease.app.domain.account.AccountDeletionBlockedException
import com.splitease.app.domain.account.AccountDeletionBlockingGroup
import com.splitease.app.domain.account.AccountDeletionEligibility
import com.splitease.app.domain.account.AccountDeletionOfflineException
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteAccountUiState(
    val blockingGroups: List<AccountDeletionBlockingGroup> = emptyList(),
    val isLoadingBalances: Boolean = true,
    val isRefreshingBalances: Boolean = false,
)

@HiltViewModel
class DeleteAccountViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val authRepository: AuthRepository,
        private val balanceInteractor: BalanceInteractor,
        private val syncInteractor: SyncInteractor,
    ) : ViewModel() {
        private val isRefreshingBalances = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<DeleteAccountUiState> =
            combine(
                authRepository.observeSession().flatMapLatest { session ->
                    val signedIn = session as? AuthSession.SignedIn
                    if (signedIn == null) {
                        flowOf(DeleteAccountUiState(isLoadingBalances = false))
                    } else {
                        balanceInteractor.observeOverallBalances(signedIn.user.userId).map { balances ->
                            DeleteAccountUiState(
                                blockingGroups =
                                    AccountDeletionEligibility.blockingGroups(
                                        balances.toDeletionSlices(
                                            appContext.getString(R.string.non_group_expenses),
                                        ),
                                    ),
                                isLoadingBalances = false,
                            )
                        }
                    }
                },
                isRefreshingBalances,
            ) { base, refreshing ->
                base.copy(isRefreshingBalances = refreshing)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DeleteAccountUiState(),
            )

        private val _action = MutableStateFlow(DeleteAccountActionState())
        val action: StateFlow<DeleteAccountActionState> = _action.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository.observeSession().collectLatest { session ->
                    val signedIn = session as? AuthSession.SignedIn
                    if (signedIn == null) {
                        isRefreshingBalances.value = false
                        return@collectLatest
                    }
                    isRefreshingBalances.value = true
                    try {
                        runCatching {
                            syncInteractor.syncForUser(signedIn.user.userId, force = true)
                        }
                    } finally {
                        isRefreshingBalances.value = false
                    }
                }
            }
        }

        fun clearError() {
            _action.update { it.copy(errorMessage = null) }
        }

        fun deleteAccount() {
            if (_action.value.isDeleting) return
            viewModelScope.launch {
                _action.update { it.copy(isDeleting = true, errorMessage = null) }
                val signedIn = authRepository.observeSession().first() as? AuthSession.SignedIn
                if (signedIn == null) {
                    _action.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = appContext.getString(R.string.error_generic),
                        )
                    }
                    return@launch
                }
                isRefreshingBalances.value = true
                try {
                    runCatching {
                        syncInteractor.syncForUser(signedIn.user.userId, force = true)
                    }
                    val balances =
                        balanceInteractor.observeOverallBalances(signedIn.user.userId).first()
                    val blocking =
                        AccountDeletionEligibility.blockingGroups(
                            balances.toDeletionSlices(
                                appContext.getString(R.string.non_group_expenses),
                            ),
                        )
                    if (blocking.isNotEmpty()) {
                        _action.update {
                            it.copy(
                                isDeleting = false,
                                errorMessage = blockedMessage(blocking),
                            )
                        }
                        return@launch
                    }
                    val result = authRepository.deleteOwnAccount()
                    val err = result.exceptionOrNull()
                    _action.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = err?.let { throwable -> userFacingError(throwable) },
                        )
                    }
                } finally {
                    isRefreshingBalances.value = false
                }
            }
        }

        private fun userFacingError(error: Throwable): String =
            when (error) {
                is AccountDeletionOfflineException ->
                    appContext.getString(R.string.account_delete_error_offline)
                is AccountDeletionBlockedException -> blockedMessage(error.blockingGroups)
                else ->
                    if (ErrorMessages.isNetworkError(error)) {
                        appContext.getString(R.string.account_delete_error_offline)
                    } else {
                        ErrorMessages.message(appContext, TAG, error)
                    }
            }

        private fun blockedMessage(groups: List<AccountDeletionBlockingGroup>): String {
            val names = groups.map { it.groupName }.filter { it.isNotBlank() }
            return if (names.isEmpty()) {
                appContext.getString(R.string.account_delete_blocked_generic)
            } else {
                appContext.getString(
                    R.string.account_delete_blocked_named,
                    names.joinToString(", "),
                )
            }
        }

        data class DeleteAccountActionState(
            val isDeleting: Boolean = false,
            val errorMessage: String? = null,
        )

        private companion object {
            const val TAG = "DeleteAccountViewModel"
        }
    }

internal fun OverallBalancesUi.toDeletionSlices(nonGroupLabel: String): List<AccountDeletionBalanceSlice> =
    buildList {
        groupBalances.forEach { group ->
            add(
                AccountDeletionBalanceSlice(
                    groupId = group.groupId,
                    groupName = group.groupName,
                    netByCurrency = group.myNetByCurrency,
                ),
            )
        }
        add(
            AccountDeletionBalanceSlice(
                groupId = "",
                groupName = nonGroupLabel,
                netByCurrency = nonGroupMyNetByCurrency,
            ),
        )
    }
