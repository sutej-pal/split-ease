package com.splitease.app.presentation.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeMessageHost
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeTopBarActionButton
import com.splitease.app.presentation.ui.seDetailHorizontal

@Composable
fun AddExpensePickerScreen(
    onBack: () -> Unit,
    onPickGroup: (groupId: String) -> Unit,
    onPickFriend: (friendUserId: String) -> Unit,
    onCreateGroup: () -> Unit,
    onInviteFriend: () -> Unit,
    onSearchContacts: () -> Unit,
    viewModel: AddExpensePickerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AddExpensePickerContent(
        groups = ui.groups,
        friends = ui.friends,
        onBack = onBack,
        onPickGroup = onPickGroup,
        onPickFriend = onPickFriend,
        onCreateGroup = onCreateGroup,
        onInviteFriend = onInviteFriend,
        onSearchContacts = onSearchContacts,
    )
}

@Composable
private fun AddExpensePickerContent(
    groups: List<Group>,
    friends: List<ExpensePickerFriend>,
    onBack: () -> Unit,
    onPickGroup: (groupId: String) -> Unit,
    onPickFriend: (friendUserId: String) -> Unit,
    onCreateGroup: () -> Unit,
    onInviteFriend: () -> Unit,
    onSearchContacts: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintReplay by remember { mutableIntStateOf(0) }
    val chooseSomeone = stringResource(R.string.expense_picker_choose_someone)
    val filteredGroups =
        remember(query, groups) {
            groups.filter { it.name.matchesQuery(query) }
        }
    val filteredFriends =
        remember(query, friends) {
            friends.filter { it.matchesQuery(query) }
        }

    fun confirmSelection() {
        val uniqueGroup = filteredGroups.singleOrNull().takeIf { filteredFriends.isEmpty() }
        val uniqueFriend = filteredFriends.singleOrNull().takeIf { filteredGroups.isEmpty() }
        when {
            uniqueGroup != null -> onPickGroup(uniqueGroup.id)
            uniqueFriend != null -> onPickFriend(uniqueFriend.friend.friendUserId)
            else -> {
                hint = chooseSomeone
                hintReplay++
            }
        }
    }

    SeScreen(
        title = stringResource(R.string.action_add_expense),
        onBack = onBack,
        actions = {
            SeTopBarActionButton(onClick = { confirmSelection() }) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_confirm_expense_people),
                    tint = SplitEaseColors.Primary,
                )
            }
        },
        snackbarHost = {
            SeMessageHost(
                errorMessage = hint,
                infoMessage = null,
                replayKey = hintReplay,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                PickerSearchRow(
                    query = query,
                    onQueryChange = {
                        query = it
                        hint = null
                    },
                    onSearch = { confirmSelection() },
                )
                HorizontalDivider(color = SplitEaseColors.Outline)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (filteredGroups.isNotEmpty()) {
                        item {
                            SeSectionHeader(
                                text = stringResource(R.string.expense_picker_groups_section),
                                modifier = Modifier.seDetailHorizontal(),
                            )
                        }
                        items(filteredGroups, key = { "group-${it.id}" }) { group ->
                            PickerGroupRow(
                                group = group,
                                onClick = { onPickGroup(group.id) },
                            )
                        }
                    }

                    if (filteredFriends.isNotEmpty()) {
                        item {
                            SeSectionHeader(
                                text = stringResource(R.string.expense_picker_friends_section),
                                modifier = Modifier.seDetailHorizontal(),
                            )
                        }
                        items(filteredFriends, key = { "friend-${it.friend.id}" }) { row ->
                            PickerFriendRow(
                                name = row.friend.displayNameSnapshot,
                                photoUrl = row.photoUrl,
                                onClick = { onPickFriend(row.friend.friendUserId) },
                            )
                        }
                    }

                    if (filteredGroups.isEmpty() && filteredFriends.isEmpty()) {
                        item {
                            SeEmptyState(
                                message =
                                    if (query.isBlank() && groups.isEmpty() && friends.isEmpty()) {
                                        stringResource(R.string.expense_picker_empty)
                                    } else {
                                        stringResource(R.string.expense_picker_no_matches)
                                    },
                                modifier = Modifier.seDetailHorizontal(),
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        PickerActionRow(
                            icon = Icons.Filled.Group,
                            label = stringResource(R.string.action_create_group),
                            onClick = onCreateGroup,
                        )
                    }
                    item {
                        PickerActionRow(
                            icon = Icons.Filled.PersonAdd,
                            label = stringResource(R.string.expense_picker_invite_friend),
                            onClick = onInviteFriend,
                        )
                    }
                    item {
                        SeSectionHeader(
                            text = stringResource(R.string.find_people_contacts_section),
                            modifier = Modifier.seDetailHorizontal(),
                        )
                    }
                    item {
                        PickerActionRow(
                            icon = Icons.Filled.Email,
                            label = stringResource(R.string.expense_picker_search_contacts),
                            onClick = onSearchContacts,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PickerSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val youLabel = stringResource(R.string.expense_picker_you)
    val prefix = stringResource(R.string.expense_with_you_and)
    val placeholder = stringResource(R.string.expense_picker_search_hint)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = SplitEaseColors.Navy)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .seDetailHorizontal()
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = withYouAndText(prefix, youLabel),
            style = textStyle,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = SplitEaseColors.NavyMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(SplitEaseColors.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
    }
}

@Composable
private fun PickerGroupRow(
    group: Group,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .seDetailHorizontal()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeGroupIconTile(
            photoUrl = group.photoUrl,
            fallbackIcon = groupTypeIcon(group.groupType),
            fallbackTint = groupTypeColor(group.groupType),
            size = 48,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
        )
    }
}

@Composable
private fun PickerFriendRow(
    name: String,
    photoUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .seDetailHorizontal()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeAvatarBadge(
            name = name,
            photoUrl = photoUrl,
            size = 48.dp,
            borderWidth = 0.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = SplitEaseColors.Navy,
        )
    }
}

@Composable
private fun PickerActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .seDetailHorizontal()
                .padding(vertical = 14.dp),
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

private fun String.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return contains(needle, ignoreCase = true)
}

private fun ExpensePickerFriend.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    if (friend.displayNameSnapshot.matchesQuery(needle)) return true
    if (friend.emailSnapshot.matchesQuery(needle)) return true
    if (email.orEmpty().matchesQuery(needle)) return true
    val digits = needle.filter { it.isDigit() }
    if (digits.isEmpty()) return false
    val phoneDigits = (phoneCountryCode.orEmpty() + phoneNumber.orEmpty()).filter { it.isDigit() }
    return phoneDigits.contains(digits)
}

private fun withYouAndText(
    full: String,
    you: String,
) = buildAnnotatedString {
    val start = full.indexOf(you, ignoreCase = true)
    if (start < 0) {
        append(full)
        return@buildAnnotatedString
    }
    append(full.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(full.substring(start, start + you.length))
    }
    append(full.substring(start + you.length))
}

private fun groupTypeIcon(type: GroupType): ImageVector =
    when (type) {
        GroupType.FRIENDS -> Icons.Filled.Group
        GroupType.HOME -> Icons.Filled.Home
        GroupType.OTHER -> Icons.AutoMirrored.Filled.List
    }

private fun groupTypeColor(type: GroupType) =
    when (type) {
        GroupType.FRIENDS -> SplitEaseColors.IconFriends
        GroupType.HOME -> SplitEaseColors.IconHome
        GroupType.OTHER -> SplitEaseColors.IconOther
    }

@Preview(name = "Add expense picker", showBackground = true, heightDp = 780)
@Composable
private fun AddExpensePickerPreview() {
    SePreview {
        AddExpensePickerContent(
            groups =
                listOf(
                    Group(
                        id = "1",
                        name = "Birthday",
                        defaultCurrencyCode = "INR",
                        groupType = GroupType.FRIENDS,
                        createdByUserId = "me",
                        createdAtEpochMs = 0L,
                        updatedAtEpochMs = 0L,
                    ),
                    Group(
                        id = "2",
                        name = "Room Feb",
                        defaultCurrencyCode = "INR",
                        groupType = GroupType.HOME,
                        createdByUserId = "me",
                        createdAtEpochMs = 0L,
                        updatedAtEpochMs = 0L,
                    ),
                ),
            friends =
                listOf(
                    ExpensePickerFriend(
                        friend =
                            Friend(
                                id = "f1",
                                ownerUserId = "me",
                                friendUserId = "u1",
                                emailSnapshot = "deepak@example.com",
                                displayNameSnapshot = "Deepak joshi",
                                createdAtEpochMs = 0L,
                                updatedAtEpochMs = 0L,
                            ),
                        photoUrl = null,
                    ),
                    ExpensePickerFriend(
                        friend =
                            Friend(
                                id = "f2",
                                ownerUserId = "me",
                                friendUserId = "u2",
                                emailSnapshot = "gopal@example.com",
                                displayNameSnapshot = "Gopal Joshi",
                                createdAtEpochMs = 0L,
                                updatedAtEpochMs = 0L,
                            ),
                        photoUrl = null,
                    ),
                ),
            onBack = {},
            onPickGroup = {},
            onPickFriend = {},
            onCreateGroup = {},
            onInviteFriend = {},
            onSearchContacts = {},
        )
    }
}
