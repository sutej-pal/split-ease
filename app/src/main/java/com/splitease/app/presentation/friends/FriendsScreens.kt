package com.splitease.app.presentation.friends

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.FriendBalanceUi
import com.splitease.app.data.balance.FriendContextBalanceUi
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.Friend
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.common.shortDisplayName
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeHeroBalancePair
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePageHeader
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSoftIconButton
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBarActionButton
import java.math.BigDecimal

/** How the friends list is filtered. */
private enum class FriendsListFilter {
    ALL,
    OUTSTANDING,
    YOU_OWE,
    OWED_TO_YOU,
}

@Composable
fun FriendsListScreen(
    onAddFriend: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val inviteFlags by viewModel.inviteFlags.collectAsStateWithLifecycle()
    val userPhotoUrls by viewModel.userPhotoUrls.collectAsStateWithLifecycle()
    val balances by viewModel.overallBalances.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)

    var listFilter by remember { mutableStateOf(FriendsListFilter.OUTSTANDING) }
    var showSettledFriends by remember { mutableStateOf(false) }

    val balanceByFriendId =
        remember(balances) {
            balances?.friendBalances?.associateBy { it.friendUserId }.orEmpty()
        }
    val settledFriends =
        remember(friends, balanceByFriendId, inviteFlags) {
            friends.filter { friend ->
                balanceByFriendId[friend.friendUserId] == null &&
                    !friend.isPendingInvite(inviteFlags)
            }
        }
    val filteredFriends =
        remember(friends, balanceByFriendId, listFilter, inviteFlags) {
            friends.filter { friend ->
                friend.matches(
                    filter = listFilter,
                    netByCurrency = balanceByFriendId[friend.friendUserId]?.netByCurrency.orEmpty(),
                    inviteFlags = inviteFlags,
                )
            }
        }
    val outstandingWithSettledHidden =
        listFilter == FriendsListFilter.OUTSTANDING && filteredFriends.isNotEmpty()
    val visibleFriends =
        when {
            listFilter == FriendsListFilter.OUTSTANDING && filteredFriends.isEmpty() -> friends
            outstandingWithSettledHidden && showSettledFriends -> filteredFriends + settledFriends
            else -> filteredFriends
        }
    val hiddenSettledCount =
        if (outstandingWithSettledHidden && !showSettledFriends) settledFriends.size else 0
    val canHideSettled =
        outstandingWithSettledHidden && showSettledFriends && settledFriends.isNotEmpty()

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
        context.startActivity(Intent.createChooser(intent, shareInvite))
        viewModel.consumeShareText()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SePageHeader(
                title = stringResource(R.string.friends_title),
                actions = {
                    SeSoftIconButton(
                        onClick = onOpenSearch,
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.cd_search),
                    )
                    SeSoftIconButton(
                        onClick = onAddFriend,
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = stringResource(R.string.action_add_friend),
                    )
                },
            )
        },
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                icon = Icons.Filled.Receipt,
            )
        },
    ) { padding ->
        SePullRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                uiState.errorMessage?.let { message ->
                    item {
                        SeErrorText(message, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                uiState.infoMessage?.let { message ->
                    item {
                        SeInfoText(message, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }

                item {
                    SeHeroBalancePair(
                        iOwe = balances?.totalIOweByCurrency.orEmpty(),
                        owedToMe = balances?.totalOwedToMeByCurrency.orEmpty(),
                        currencyCode = currencyCode,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    FriendsFilterMenu(
                        selectedFilter = listFilter,
                        onFilterSelected = {
                            listFilter = it
                            showSettledFriends = false
                        },
                    )
                }

                if (friends.isEmpty()) {
                    item {
                        SeEmptyState(
                            message = stringResource(R.string.friends_empty),
                            actionLabel = stringResource(R.string.action_add_friend),
                            onAction = onAddFriend,
                        )
                    }
                } else if (visibleFriends.isEmpty()) {
                    item {
                        SeEmptyState(
                            message = stringResource(R.string.friends_empty_filtered),
                        )
                    }
                } else {
                    items(visibleFriends, key = { it.id }) { friend ->
                        FriendBalanceListItem(
                            friend = friend,
                            pending = friend.isPendingInvite(inviteFlags),
                            balance = balanceByFriendId[friend.friendUserId],
                            isSettled =
                                settledFriends.any { it.friendUserId == friend.friendUserId },
                            currencyFallback = currencyCode,
                            photoUrl = userPhotoUrls[friend.friendUserId],
                            onClick = { onOpenFriend(friend.friendUserId) },
                            onCopyInvite = { viewModel.copyInviteLink(friend.id) },
                            onShareInvite = { viewModel.shareInviteAgain(friend.id) },
                        )
                    }
                }

                if (hiddenSettledCount > 0) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.friends_hiding_settled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SeOutlinedButton(
                            text =
                                pluralStringResource(
                                    R.plurals.friends_show_settled,
                                    hiddenSettledCount,
                                    hiddenSettledCount,
                                ),
                            onClick = { showSettledFriends = true },
                        )
                    }
                } else if (canHideSettled) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SeTextButton(
                            text = stringResource(R.string.friends_hide_settled),
                            onClick = { showSettledFriends = false },
                        )
                    }
                }
            }
        }
    }
}

private val FriendsListFilter.labelRes: Int
    get() =
        when (this) {
            FriendsListFilter.ALL -> R.string.friends_filter_all
            FriendsListFilter.OUTSTANDING -> R.string.friends_filter_outstanding
            FriendsListFilter.YOU_OWE -> R.string.friends_filter_you_owe
            FriendsListFilter.OWED_TO_YOU -> R.string.friends_filter_owed_to_you
        }

@Composable
private fun FriendsFilterMenu(
    selectedFilter: FriendsListFilter,
    onFilterSelected: (FriendsListFilter) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_filter_friends),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                FriendsListFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(option.labelRes)) },
                        onClick = {
                            onFilterSelected(option)
                            menuExpanded = false
                        },
                        leadingIcon = {
                            RadioButton(
                                selected = selectedFilter == option,
                                onClick = {
                                    onFilterSelected(option)
                                    menuExpanded = false
                                },
                                colors =
                                    RadioButtonDefaults.colors(
                                        selectedColor = SplitEaseColors.Primary,
                                    ),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun Friend.isPendingInvite(flags: FriendInviteFlags): Boolean {
    if (id in flags.pendingFriendRowIds) return true
    if (id in flags.acceptedFriendRowIds) return false
    return displayNameSnapshot.contains("(invited)", ignoreCase = true)
}

private fun Friend.matches(
    filter: FriendsListFilter,
    netByCurrency: Map<String, BigDecimal>,
    inviteFlags: FriendInviteFlags,
): Boolean {
    val nets = netByCurrency.values
    val pending = isPendingInvite(inviteFlags)
    return when (filter) {
        FriendsListFilter.ALL -> true
        FriendsListFilter.OUTSTANDING ->
            pending || nets.any { it.compareTo(BigDecimal.ZERO) != 0 }
        FriendsListFilter.YOU_OWE -> nets.any { it < BigDecimal.ZERO }
        FriendsListFilter.OWED_TO_YOU -> nets.any { it > BigDecimal.ZERO }
    }
}

@Composable
private fun FriendBalanceListItem(
    friend: Friend,
    pending: Boolean,
    balance: FriendBalanceUi?,
    isSettled: Boolean,
    currencyFallback: String,
    photoUrl: String?,
    onClick: () -> Unit,
    onCopyInvite: () -> Unit,
    onShareInvite: () -> Unit,
) {
    val contexts = balance?.contexts.orEmpty()
    val showBreakdown = contexts.size > 1
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SeAvatarBadge(
                name = friend.displayNameSnapshot,
                photoUrl = photoUrl,
                size = 56.dp,
                borderWidth = 0.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (pending) {
                            friend.displayNameSnapshot
                        } else {
                            friend.displayNameSnapshot
                                .replace(Regex("\\s*\\(invited\\)\\s*", RegexOption.IGNORE_CASE), "")
                                .trim()
                                .ifBlank { friend.displayNameSnapshot }
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                when {
                    pending ->
                        Text(
                            text =
                                "${friend.emailSnapshot} · ${stringResource(R.string.invite_pending_label)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    balance != null ->
                        FriendNetStatus(
                            netByCurrency = balance.netByCurrency,
                            currencyFallback = currencyFallback,
                        )
                    isSettled ->
                        Text(
                            text = stringResource(R.string.balances_settled_up).lowercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        Text(
                            text = stringResource(R.string.friends_no_expenses),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            if (pending) {
                Row {
                    IconButton(onClick = onCopyInvite) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_invite_link),
                            tint = SplitEaseColors.Primary,
                        )
                    }
                    IconButton(onClick = onShareInvite) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.cd_share_invite_again),
                            tint = SplitEaseColors.Primary,
                        )
                    }
                }
            }
        }
        if (showBreakdown) {
            FriendContextBreakdown(
                displayName = friend.displayNameSnapshot,
                contexts = contexts,
                currencyFallback = currencyFallback,
                modifier = Modifier.padding(start = 70.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun FriendContextBreakdown(
    displayName: String,
    contexts: List<FriendContextBalanceUi>,
    currencyFallback: String,
    modifier: Modifier = Modifier,
) {
    val shortName = remember(displayName) { shortDisplayName(displayName) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.5.dp.toPx() }
    val rows =
        remember(contexts) {
            contexts.flatMap { context ->
                context.netByCurrency.toSortedMap().mapNotNull { (currency, net) ->
                    if (net.compareTo(BigDecimal.ZERO) == 0) {
                        null
                    } else {
                        Triple(context.contextName, currency, net)
                    }
                }
            }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (contextName, currency, net) ->
            val isLast = index == rows.lastIndex
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(width = 20.dp, height = 28.dp)) {
                    val x = size.width * 0.35f
                    val midY = size.height / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, if (isLast) midY else size.height),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = lineColor,
                        start = Offset(x, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                FriendContextLine(
                    shortName = shortName,
                    contextName = contextName,
                    amount = net.abs(),
                    currencyCode = currency.ifBlank { currencyFallback },
                    youOwe = net < BigDecimal.ZERO,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FriendContextLine(
    shortName: String,
    contextName: String,
    amount: BigDecimal,
    currencyCode: String,
    youOwe: Boolean,
    modifier: Modifier = Modifier,
) {
    val money = MoneyFormat.format(amount, currencyCode)
    val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text =
            buildAnnotatedString {
                if (youOwe) {
                    withStyle(SpanStyle(color = body)) {
                        append("You owe $shortName ")
                    }
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                        append(money)
                    }
                    withStyle(SpanStyle(color = body)) {
                        append(" for '$contextName'")
                    }
                } else {
                    withStyle(SpanStyle(color = body)) {
                        append("$shortName owes you ")
                    }
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                        append(money)
                    }
                    withStyle(SpanStyle(color = body)) {
                        append(" for '$contextName'")
                    }
                }
            },
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FriendNetStatus(
    netByCurrency: Map<String, BigDecimal>,
    currencyFallback: String,
) {
    if (netByCurrency.isEmpty()) {
        SeMoneyText(
            amount = BigDecimal.ZERO,
            currencyCode = currencyFallback,
            tone = SeMoneyTone.SETTLED,
            prefix = stringResource(R.string.balances_settled_up).lowercase(),
        )
        return
    }
    netByCurrency.toSortedMap().forEach { (currency, net) ->
        val code = currency.ifBlank { currencyFallback }
        when {
            net < BigDecimal.ZERO ->
                SeMoneyText(
                    amount = net.abs(),
                    currencyCode = code,
                    tone = SeMoneyTone.YOU_OWE,
                    prefix = stringResource(R.string.friends_you_owe),
                )
            net > BigDecimal.ZERO ->
                SeMoneyText(
                    amount = net,
                    currencyCode = code,
                    tone = SeMoneyTone.OWED_TO_YOU,
                    prefix = stringResource(R.string.friends_owes_you),
                )
        }
    }
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
    var showValidation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val fieldsValid = name.isNotBlank() && contact.isNotBlank()
    val nameError = showValidation && name.isBlank()
    val contactError = showValidation && contact.isBlank()

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
        onDone()
    }

    SeScreen(
        title = stringResource(R.string.action_add_friend),
        onBack = onBack,
        actions = {
            SeTopBarActionButton(
                onClick = {
                    showValidation = true
                    if (!fieldsValid) return@SeTopBarActionButton
                    viewModel.addFriend(
                        name = name,
                        contact = contact,
                        groupId = groupId,
                    ) { onDone() }
                },
                enabled = !uiState.isSubmitting,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_done),
                    tint = SplitEaseColors.Primary,
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
                    isError = nameError,
                    supportingText =
                        if (nameError) stringResource(R.string.msg_name_required) else null,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = stringResource(R.string.label_phone_or_email),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isSubmitting,
                    isError = contactError,
                    supportingText =
                        if (contactError) stringResource(R.string.msg_contact_required) else null,
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
                        showValidation = true
                        if (!fieldsValid) return@SePrimaryButton
                        viewModel.addFriend(
                            name = name,
                            contact = contact,
                            groupId = groupId,
                        ) { onDone() }
                    },
                    enabled = !uiState.isSubmitting,
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
