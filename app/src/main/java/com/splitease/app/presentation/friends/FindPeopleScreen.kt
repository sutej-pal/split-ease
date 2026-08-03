package com.splitease.app.presentation.friends

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.contacts.DeviceContact
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.Friend
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTopBar

/**
 * Search friends and device contacts; optionally add them to a group.
 *
 * @param groupId When set, selecting a friend adds them to that group.
 * @param onBack Navigate up.
 * @param onManualAdd Opens Edit contact for a manual entry (returns to Review).
 * @param onReviewSelected Opens Review with selected device contacts.
 */
@Composable
fun FindPeopleScreen(
    groupId: String?,
    onBack: () -> Unit,
    onManualAdd: () -> Unit,
    onReviewSelected: () -> Unit,
    viewModel: FindPeopleViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.onContactsPermissionResult(granted)
        }

    LaunchedEffect(groupId) {
        viewModel.setGroupId(groupId)
        viewModel.clearMessages()
    }

    LaunchedEffect(uiState.contactsPermissionGranted, permissionRequested) {
        if (!uiState.contactsPermissionGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
        val html = InviteLinks.htmlForShareText(text)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, inviteSubject)
                putExtra(Intent.EXTRA_TEXT, text)
                if (html != null) {
                    putExtra(Intent.EXTRA_HTML_TEXT, html)
                }
            }
        context.startActivity(
            Intent.createChooser(intent, shareInvite),
        )
        viewModel.consumeShareText()
    }

    val filteredFriends = viewModel.filteredFriends(friends)
    val filteredContacts = viewModel.filteredContacts()
    val isGroupMode = !groupId.isNullOrBlank()
    val hasSelection = uiState.selectedContactIds.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.find_people_title),
                onBack = onBack,
                actions = {
                    if (hasSelection) {
                        IconButton(
                            onClick = {
                                if (viewModel.prepareReview()) {
                                    onReviewSelected()
                                }
                            },
                            enabled = !uiState.isSubmitting,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.action_next),
                                tint = SplitEaseColors.Primary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::setQuery,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = {
                        Text(stringResource(R.string.find_people_search_hint))
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = SplitEaseColors.NavyMuted,
                        )
                    },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitEaseColors.Primary,
                            unfocusedBorderColor = SplitEaseColors.OutlineStrong,
                            focusedContainerColor = SplitEaseColors.Surface,
                            unfocusedContainerColor = SplitEaseColors.Surface,
                            cursorColor = SplitEaseColors.Primary,
                            focusedTextColor = SplitEaseColors.Navy,
                            unfocusedTextColor = SplitEaseColors.Navy,
                        ),
                )
            }

            item {
                FindPeopleActionTile(
                    icon = Icons.Filled.PersonAdd,
                    label = stringResource(R.string.find_people_title),
                    onClick = {
                        viewModel.prepareManualAdd()
                        onManualAdd()
                    },
                )
            }

            uiState.errorMessage?.let { msg ->
                item {
                    SeErrorText(msg, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            uiState.infoMessage?.let { msg ->
                item {
                    SeInfoText(msg, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }

            item {
                SeSectionHeader(
                    text = stringResource(R.string.find_people_friends_section),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (filteredFriends.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.find_people_no_friends),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(filteredFriends, key = { it.id }) { friend ->
                    val syncedInGroup =
                        isGroupMode && friend.friendUserId in uiState.syncedMemberUserIds
                    val locallyOnly =
                        isGroupMode &&
                            friend.friendUserId in uiState.memberUserIds &&
                            !syncedInGroup
                    FriendPickRow(
                        friend = friend,
                        alreadyInGroup = syncedInGroup,
                        locallyPending = locallyOnly,
                        enabled = !uiState.isSubmitting && (!isGroupMode || !syncedInGroup),
                        onClick = {
                            if (isGroupMode && !syncedInGroup) {
                                viewModel.addFriendToGroup(friend.friendUserId)
                            }
                        },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SeSectionHeader(
                    text = stringResource(R.string.find_people_contacts_section),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            when {
                !uiState.contactsPermissionGranted -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(
                                text = stringResource(R.string.find_people_contacts_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SplitEaseColors.NavyMuted,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SeOutlinedButton(
                                text = stringResource(R.string.action_allow_contacts),
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                            )
                        }
                    }
                }
                uiState.isLoadingContacts -> {
                    item {
                        Text(
                            text = stringResource(R.string.find_people_loading_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SplitEaseColors.NavyMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                filteredContacts.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.find_people_no_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SplitEaseColors.NavyMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                else -> {
                    items(filteredContacts, key = { it.id }) { contact ->
                        ContactPickRow(
                            contact = contact,
                            selected = contact.id in uiState.selectedContactIds,
                            enabled = !uiState.isSubmitting,
                            onClick = { viewModel.toggleContactSelection(contact.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FindPeopleActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeIconTile(
            icon = icon,
            tint = SplitEaseColors.Primary,
            size = 40,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FriendPickRow(
    friend: Friend,
    alreadyInGroup: Boolean,
    locallyPending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeIconTile(Icons.Filled.Person, SplitEaseColors.IconFriends, size = 48)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                alreadyInGroup -> {
                    Text(
                        text = stringResource(R.string.find_people_already_in_group),
                        style = MaterialTheme.typography.bodySmall,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
                locallyPending -> {
                    Text(
                        text = stringResource(R.string.find_people_tap_to_sync_member),
                        style = MaterialTheme.typography.bodySmall,
                        color = SplitEaseColors.Primary,
                    )
                }
                friend.emailSnapshot.isNotBlank() -> {
                    Text(
                        text = friend.emailSnapshot,
                        style = MaterialTheme.typography.bodySmall,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
            }
        }
        if (alreadyInGroup) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = SplitEaseColors.OutlineStrong,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ContactPickRow(
    contact: DeviceContact,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = contact.phoneNumber ?: contact.email
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeIconTile(Icons.Filled.Phone, SplitEaseColors.IconOther, size = 48)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName.ifBlank { subtitle.orEmpty() },
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank() && subtitle != contact.displayName) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SplitEaseColors.NavyMuted,
                )
            }
        }
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = SplitEaseColors.Primary,
                    uncheckedColor = SplitEaseColors.OutlineStrong,
                ),
        )
    }
}
