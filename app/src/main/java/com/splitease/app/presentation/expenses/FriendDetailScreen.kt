package com.splitease.app.presentation.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.FriendBalanceUi
import com.splitease.app.data.balance.FriendContextBalanceUi
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.groups.BannerCircleIconButton
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeSystemBars
import java.math.BigDecimal

@Composable
fun FriendDetailScreen(
    friendUserId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (expenseId: String) -> Unit,
    onSettleUp: (fromUserId: String, toUserId: String, amount: String, currency: String, label: String) -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
) {
    val ledger by remember(friendUserId) { viewModel.observeFriendLedger(friendUserId) }
        .collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val balance by remember(friendUserId) { balancesViewModel.observeFriendBalance(friendUserId) }
        .collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(friendUserId.take(8)) }
    val me = viewModel.currentUserId()
    val bannerColor = lerp(SplitEaseColors.IconFriends, SplitEaseColors.Navy, 0.28f)
    val displayName = title.removeSuffix(" (invited)").trim()
    val canSettle =
        me != null &&
            balance?.netByCurrency?.entries?.any {
                it.value.compareTo(BigDecimal.ZERO) != 0
            } == true

    LaunchedEffect(friendUserId) {
        title = viewModel.friendLabel(friendUserId)
        viewModel.refreshMyExpenses()
    }

    val lightIconsOnBars = bannerColor.luminance() > 0.5f
    SeSystemBars(
        statusBarColor = bannerColor,
        navigationBarColor = MaterialTheme.colorScheme.background,
        statusBarDarkIcons = lightIconsOnBars,
        navigationBarDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            SeExtendedFab(
                text = stringResource(R.string.action_add_expense),
                onClick = onAddExpense,
                icon = Icons.Filled.Receipt,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                FriendDetailBanner(
                    bannerColor = bannerColor,
                    onBack = onBack,
                    onOpenSettings = onOpenSettings,
                )
            }
            item {
                FriendDetailHeader(
                    title = displayName,
                    balance = balance,
                )
            }
            item {
                FriendDetailActions(
                    canSettle = canSettle,
                    onSettleUp = {
                        val settleCandidate =
                            balance?.netByCurrency?.entries?.firstOrNull {
                                it.value.compareTo(BigDecimal.ZERO) != 0
                            } ?: return@FriendDetailActions
                        if (me == null) return@FriendDetailActions
                        val net = settleCandidate.value
                        val currency = settleCandidate.key
                        if (net < BigDecimal.ZERO) {
                            onSettleUp(me, friendUserId, net.abs().toPlainString(), currency, title)
                        } else {
                            onSettleUp(friendUserId, me, net.toPlainString(), currency, title)
                        }
                    },
                )
            }
            uiState.errorMessage?.let { msg ->
                item {
                    SeErrorText(msg, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            if (ledger.isEmpty()) {
                item { FriendDetailEmptyState() }
            } else {
                ledgerEntries(
                    ledger,
                    onExpenseClick = onOpenExpense,
                    horizontalPadding = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun FriendDetailBanner(
    bannerColor: Color,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bannerColor)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerCircleIconButton(
                onClick = onBack,
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.cd_back),
            )
            BannerCircleIconButton(
                onClick = onOpenSettings,
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_friend_settings),
            )
        }
    }
}

@Composable
private fun FriendDetailHeader(
    title: String,
    balance: FriendBalanceUi?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(y = (-40).dp)
                .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                tint = SplitEaseColors.NavyMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SplitEaseColors.Navy,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FriendBalanceSummary(balance = balance, friendName = title)
    }
}

@Composable
private fun FriendBalanceSummary(
    balance: FriendBalanceUi?,
    friendName: String,
) {
    val nets = balance?.netByCurrency.orEmpty()
    val contexts = balance?.contexts.orEmpty()
    val hasBalance = nets.values.any { it.compareTo(BigDecimal.ZERO) != 0 }
    var expanded by rememberSaveable { mutableStateOf(true) }

    if (!hasBalance) {
        Text(
            text = stringResource(R.string.friend_detail_no_expenses_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.NavyMuted,
            textAlign = TextAlign.Center,
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier =
                Modifier
                    .clickable(enabled = contexts.size > 1) { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FriendOverallHeadline(nets = nets)
            if (contexts.size > 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector =
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        }
        AnimatedVisibility(visible = expanded && contexts.isNotEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, SplitEaseColors.Outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                contexts.forEach { context ->
                    FriendContextLine(context = context, friendName = friendName)
                }
            }
        }
    }
}

@Composable
private fun FriendOverallHeadline(nets: Map<String, BigDecimal>) {
    val (currency, net) =
        nets.entries.firstOrNull { it.value.compareTo(BigDecimal.ZERO) != 0 }
            ?: return
    val money = MoneyFormat.format(net.abs(), currency.ifBlank { AppCurrencies.DEFAULT })
    val youOwe = net < BigDecimal.ZERO
    val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
    val template =
        if (youOwe) {
            stringResource(R.string.balances_you_owe_overall, money)
        } else {
            stringResource(R.string.balances_you_are_owed_overall, money)
        }
    Text(
        text =
            buildAnnotatedString {
                val start = template.indexOf(money)
                if (start < 0) {
                    append(template)
                } else {
                    append(template.substring(0, start))
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(money)
                    }
                    append(template.substring(start + money.length))
                }
            },
        style = MaterialTheme.typography.titleMedium,
        color = SplitEaseColors.Navy,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FriendContextLine(
    context: FriendContextBalanceUi,
    friendName: String,
) {
    val (currency, net) =
        context.netByCurrency.entries.firstOrNull { it.value.compareTo(BigDecimal.ZERO) != 0 }
            ?: return
    val money = MoneyFormat.format(net.abs(), currency.ifBlank { AppCurrencies.DEFAULT })
    val youOwe = net < BigDecimal.ZERO
    val firstName = friendName.substringBefore(" ").ifBlank { friendName }
    val isNonGroup = context.contextId.isBlank()
    val line =
        when {
            youOwe && isNonGroup ->
                stringResource(R.string.friend_balance_you_owe_non_group, firstName, money)
            !youOwe && isNonGroup ->
                stringResource(R.string.friend_balance_owes_you_non_group, firstName, money)
            youOwe ->
                stringResource(R.string.friend_balance_you_owe_in_group, firstName, money, context.contextName)
            else ->
                stringResource(R.string.friend_balance_owes_you_in_group, firstName, money, context.contextName)
        }
    val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 3.dp, end = 10.dp)
                    .width(3.dp)
                    .height(16.dp)
                    .background(accent.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
        Text(
            text =
                buildAnnotatedString {
                    val start = line.indexOf(money)
                    if (start < 0) {
                        append(line)
                    } else {
                        append(line.substring(0, start))
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                            append(money)
                        }
                        append(line.substring(start + money.length))
                    }
                },
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.Navy,
        )
    }
}

@Composable
private fun FriendDetailActions(
    canSettle: Boolean,
    onSettleUp: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeActionChip(
            label = stringResource(R.string.action_settle_up),
            onClick = onSettleUp,
            enabled = canSettle,
        )
        SeActionChip(
            label = stringResource(R.string.action_remind),
            onClick = { },
            icon = Icons.Filled.Notifications,
        )
        SeActionChip(
            label = stringResource(R.string.action_charts),
            onClick = { },
            icon = Icons.AutoMirrored.Filled.ShowChart,
        )
        SeActionChip(
            label = stringResource(R.string.action_convert_currency),
            onClick = { },
            enabled = false,
            icon = Icons.Filled.SwapHoriz,
        )
    }
}

@Composable
private fun FriendDetailEmptyState() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(y = (-12).dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.friend_detail_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SplitEaseColors.Navy,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.friend_detail_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.NavyMuted,
            textAlign = TextAlign.Center,
        )
    }
}
