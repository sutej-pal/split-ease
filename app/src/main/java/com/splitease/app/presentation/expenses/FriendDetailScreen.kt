package com.splitease.app.presentation.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.balances.FriendBalanceHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    friendUserId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val expenses by remember(friendUserId) { viewModel.observeFriendExpenses(friendUserId) }
        .collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(friendUserId.take(8)) }

    LaunchedEffect(friendUserId) {
        title = viewModel.friendLabel(friendUserId)
        viewModel.refreshMyExpenses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_expense))
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.balances_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FriendBalanceHeader(friendUserId = friendUserId)
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ExpenseListSection(
                expenses = expenses,
                emptyText = stringResource(R.string.expenses_empty),
            )
            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
