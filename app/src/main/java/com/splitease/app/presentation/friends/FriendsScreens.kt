package com.splitease.app.presentation.friends

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextField

@Composable
fun FriendsListScreen(
    onAddFriend: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.friends_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = SplitEaseColors.Navy,
                )
            }
        },
        floatingActionButton = {
            SeFab(
                onClick = onAddFriend,
                contentDescription = stringResource(R.string.action_add_friend),
                icon = Icons.Filled.Add,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                uiState.errorMessage?.let {
                    SeErrorText(it, modifier = Modifier.padding(16.dp))
                }
                if (friends.isEmpty()) {
                    SeEmptyState(
                        message = stringResource(R.string.friends_empty),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        actionLabel = stringResource(R.string.action_add_friend),
                        onAction = onAddFriend,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        items(friends, key = { it.id }) { friend ->
                            FriendRow(friend = friend, onClick = { onOpenFriend(friend.friendUserId) })
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    val pending = friend.displayNameSnapshot.contains("(invited)", ignoreCase = true)
    SeListRow(
        title = friend.displayNameSnapshot,
        subtitle =
            if (pending) {
                "${friend.emailSnapshot} · ${stringResource(R.string.invite_pending_label)}"
            } else {
                friend.emailSnapshot
            },
        leading = { SeIconTile(Icons.Filled.Person, SplitEaseColors.IconFriends, size = 48) },
        onClick = onClick,
    )
}

@Composable
fun AddFriendScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    groupId: String? = null,
    prefillName: String = "",
    prefillContact: String = "",
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable(prefillName) { mutableStateOf(prefillName) }
    var contact by rememberSaveable(prefillContact) { mutableStateOf(prefillContact) }
    val context = LocalContext.current
    val canSubmit =
        name.isNotBlank() && contact.isNotBlank() && !uiState.isSubmitting

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.invite_email_subject))
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.action_share_invite)),
        )
        viewModel.consumeShareText()
        onDone()
    }

    SeScreen(
        title = stringResource(R.string.action_add_friend),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    viewModel.addFriend(
                        name = name,
                        contact = contact,
                        groupId = groupId,
                    ) { onDone() }
                },
                enabled = canSubmit,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_done),
                    tint =
                        if (canSubmit) {
                            SplitEaseColors.Primary
                        } else {
                            SplitEaseColors.OutlineStrong
                        },
                )
            }
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SeTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.label_name),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = stringResource(R.string.label_phone_or_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.add_friend_review_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.weight(1f))
                SePrimaryButton(
                    text = stringResource(R.string.action_next),
                    onClick = {
                        viewModel.addFriend(
                            name = name,
                            contact = contact,
                            groupId = groupId,
                        ) { onDone() }
                    },
                    enabled = canSubmit,
                )
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
                uiState.infoMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(it)
                }
            }
        },
    )
}
