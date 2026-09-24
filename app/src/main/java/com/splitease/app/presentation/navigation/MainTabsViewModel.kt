package com.splitease.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.ActivityEventRepository
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

/**
 * Shell-level state for the signed-in bottom tabs (unread activity badge).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainTabsViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        activityEventRepository: ActivityEventRepository,
    ) : ViewModel() {
        val activityUnreadCount: StateFlow<Int> =
            authRepository
                .observeSession()
                .map { (it as? AuthSession.SignedIn)?.user?.userId }
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(0)
                    } else {
                        activityEventRepository.observeUnseenCount(userId)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    }
