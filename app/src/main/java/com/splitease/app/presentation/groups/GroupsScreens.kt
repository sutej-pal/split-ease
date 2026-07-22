package com.splitease.app.presentation.groups

import android.content.Intent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateGroup) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_create_group))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.groups_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGroup(group.id) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                group.defaultCurrencyCode,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_create_group)) },
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
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_group_name)) },
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_currency)) },
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.label_add_members), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text(stringResource(R.string.no_friends_yet), style = MaterialTheme.typography.bodyMedium)
            } else {
                val addableFriends =
                    friends.filter { !it.displayNameSnapshot.contains("(invited)", ignoreCase = true) }
                if (addableFriends.isEmpty()) {
                    Text(stringResource(R.string.no_friends_yet), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(addableFriends, key = { it.id }) { friend ->
                            FriendCheckRow(
                                friend = friend,
                                checked = selected.contains(friend.friendUserId),
                                onToggle = {
                                    selected =
                                        if (selected.contains(friend.friendUserId)) {
                                            selected - friend.friendUserId
                                        } else {
                                            selected + friend.friendUserId
                                        }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.createGroup(name, currency, selected.toList(), onCreated)
                },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_create_group))
            }
            uiState.errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FriendCheckRow(
    friend: Friend,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column {
            Text(friend.displayNameSnapshot, style = MaterialTheme.typography.bodyLarge)
            Text(
                friend.emailSnapshot,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
    expensesViewModel: com.splitease.app.presentation.expenses.ExpensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val members by viewModel.observeMembers(groupId).collectAsStateWithLifecycle()
    val expenses by remember(groupId) { expensesViewModel.observeGroupExpenses(groupId) }
        .collectAsStateWithLifecycle()
    var group by remember { mutableStateOf<Group?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("INR") }
    val context = LocalContext.current

    LaunchedEffect(groupId) {
        val loaded = viewModel.getGroup(groupId)
        group = loaded
        if (loaded != null) {
            name = loaded.name
            currency = loaded.defaultCurrencyCode
        }
        expensesViewModel.refreshGroupExpenses(groupId)
    }

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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: stringResource(R.string.groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { editing = !editing }) {
                        Text(if (editing) stringResource(R.string.action_done) else stringResource(R.string.action_edit))
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (editing && group != null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_group_name)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_currency)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = group ?: return@Button
                        viewModel.updateGroup(
                            current.copy(name = name.trim(), defaultCurrencyCode = currency.trim().uppercase()),
                        )
                        editing = false
                        group = current.copy(name = name.trim(), defaultCurrencyCode = currency.trim().uppercase())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(stringResource(R.string.label_members), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            members.forEach { member ->
                MemberRow(member = member, friends = friends)
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.invite_by_email), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            var inviteEmail by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = inviteEmail,
                onValueChange = { inviteEmail = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !uiState.isSubmitting,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.inviteMemberByEmail(groupId, inviteEmail)
                    inviteEmail = ""
                },
                enabled = !uiState.isSubmitting && inviteEmail.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_send_invite))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.add_member_from_friends), style = MaterialTheme.typography.titleSmall)
            val memberIds = members.map { it.userId }.toSet()
            friends
                .filter {
                    it.friendUserId !in memberIds &&
                        !it.displayNameSnapshot.contains("(invited)", ignoreCase = true)
                }.forEach { friend ->
                    TextButton(
                        onClick = { viewModel.addMember(groupId, friend.friendUserId) },
                        enabled = !uiState.isSubmitting,
                    ) {
                        Text("+ ${friend.displayNameSnapshot}")
                    }
                }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            com.splitease.app.presentation.expenses.ExpenseListSection(
                expenses = expenses,
                emptyText = stringResource(R.string.expenses_empty),
            )

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

@Composable
private fun MemberRow(member: GroupMember, friends: List<Friend>) {
    val label =
        friends.firstOrNull { it.friendUserId == member.userId }?.displayNameSnapshot
            ?: member.userId.take(8)
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            member.role.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    }
}
