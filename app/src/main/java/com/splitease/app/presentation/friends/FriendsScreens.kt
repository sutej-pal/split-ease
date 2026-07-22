package com.splitease.app.presentation.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsListScreen(
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
    onOpenFriend: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.friends_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFriend) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_friend))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (friends.isEmpty()) {
                Text(
                    text = stringResource(R.string.friends_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(friends, key = { it.id }) { friend ->
                        FriendRow(friend = friend, onClick = { onOpenFriend(friend.friendUserId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    val pending = friend.displayNameSnapshot.contains("(invited)", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(friend.displayNameSnapshot, style = MaterialTheme.typography.titleMedium)
        Text(
            friend.emailSnapshot,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        if (pending) {
            Text(
                text = stringResource(R.string.invite_pending_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.invite_email_subject))
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share_invite)))
        viewModel.consumeShareText()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_add_friend)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.add_friend_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !uiState.isSubmitting,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.addFriend(email) { onDone() }
                },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_add_friend))
            }
            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            uiState.infoMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
