package com.splitease.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")

        @OptIn(ExperimentalCoroutinesApi::class)
        val results: StateFlow<List<Expense>> =
            query
                .flatMapLatest { q -> expenseRepository.search(q) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun search(text: String) {
            query.value = text
        }
    }
