package com.splitease.app.presentation.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextField

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.search_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp),
            ) {
                SeTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.search(it)
                    },
                    label = stringResource(R.string.search_hint),
                )
                if (results.isEmpty()) {
                    SeEmptyState(
                        message =
                            if (query.isBlank()) {
                                stringResource(R.string.search_empty_prompt)
                            } else {
                                stringResource(R.string.search_no_results)
                            },
                    )
                } else {
                    LazyColumn {
                        items(results, key = { it.id }) { expense ->
                            SeListRow(
                                title = expense.description,
                                subtitle =
                                    "${expense.currencyCode} ${expense.amount.toPlainString()}" +
                                        (expense.notes?.let { " · $it" } ?: ""),
                            )
                        }
                    }
                }
            }
        },
    )
}
