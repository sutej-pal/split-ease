package com.splitease.app.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.R
import com.splitease.app.data.sync.SyncFlushResult
import com.splitease.app.data.sync.SyncInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSyncUi(
    val pendingCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastMessage: String? = null,
)

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val syncInteractor: SyncInteractor,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _sync = MutableStateFlow(AccountSyncUi())
        val sync: StateFlow<AccountSyncUi> = _sync.asStateFlow()

        init {
            refreshPending()
        }

        fun refreshPending() {
            viewModelScope.launch {
                val count = syncInteractor.pendingCount()
                _sync.update { it.copy(pendingCount = count) }
            }
        }

        fun syncNow() {
            viewModelScope.launch {
                _sync.update { it.copy(isSyncing = true, lastMessage = null) }
                val result = syncInteractor.syncForUser()
                val count = syncInteractor.pendingCount()
                _sync.update {
                    it.copy(
                        isSyncing = false,
                        pendingCount = count,
                        lastMessage = result.toMessage(),
                    )
                }
            }
        }

        private fun SyncFlushResult.toMessage(): String {
            val parts = mutableListOf<String>()
            if (groupsSynced > 0) parts += "$groupsSynced groups"
            if (membersSynced > 0) parts += "$membersSynced members"
            if (expensesSynced > 0) parts += "$expensesSynced expenses"
            if (paymentsSynced > 0) parts += "$paymentsSynced payments"
            val summary = parts.joinToString(", ")
            return when {
                failures.isNotEmpty() && parts.isEmpty() ->
                    appContext.getString(R.string.msg_sync_failed, failures.first())
                failures.isNotEmpty() ->
                    appContext.getString(R.string.msg_synced_partial, summary, failures.size)
                parts.isEmpty() -> appContext.getString(R.string.msg_everything_up_to_date)
                else -> appContext.getString(R.string.msg_synced, summary)
            }
        }
    }
