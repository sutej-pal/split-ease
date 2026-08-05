package com.splitease.app.presentation.groups

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.data.balance.GroupBalanceUi
import com.splitease.app.data.balance.LabeledDebt
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.balances.BalancesViewModel
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.expenses.ledgerEntries
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeFab
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeLoadingOverlay
import com.splitease.app.presentation.ui.SeMoneyText
import com.splitease.app.presentation.ui.SeMoneyTone
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTypeChip
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GroupsListScreen(
    onBack: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SeScreen(
        title = stringResource(R.string.groups_title),
        onBack = onBack,
        floatingActionButton = {
            SeFab(
                onClick = onCreateGroup,
                contentDescription = stringResource(R.string.action_create_group),
                icon = Icons.Filled.Add,
            )
        },
        content = { padding ->
            SePullRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    uiState.errorMessage?.let {
                        SeErrorText(it, modifier = Modifier.padding(16.dp))
                    }
                    if (groups.isEmpty()) {
                        SeEmptyState(
                            message = stringResource(R.string.groups_empty),
                            modifier = Modifier.padding(horizontal = 20.dp),
                            actionLabel = stringResource(R.string.action_create_group),
                            onAction = onCreateGroup,
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                            items(groups, key = { it.id }) { group ->
                                SeListRow(
                                    title = group.name,
                                    leading = {
                                        SeGroupIconTile(
                                            photoUrl = group.photoUrl,
                                            fallbackIcon = group.groupType.icon(),
                                            fallbackTint = group.groupType.tint(),
                                            size = 48,
                                        )
                                    },
                                    onClick = { onOpenGroup(group.id) },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var groupType by rememberSaveable { mutableStateOf(GroupType.OTHER.name) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedType = runCatching { GroupType.valueOf(groupType) }.getOrDefault(GroupType.OTHER)
    val isSubmitting = uiState.isSubmitting
    val canCreate = name.isNotBlank() && !isSubmitting
    val photoPicker = rememberGroupPhotoPicker { photoUri = it }
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.create_group_title),
                onClose = if (isSubmitting) null else onBack,
                centered = true,
            )
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SePrimaryButton(
                    text = stringResource(R.string.action_create_group),
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.createGroup(
                            name = name,
                            groupType = selectedType,
                            photoUri = photoUri,
                            onSuccess = onCreated,
                        )
                    },
                    enabled = canCreate,
                    isLoading = isSubmitting,
                )
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { focusManager.clearFocus() },
                    ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .alpha(if (isSubmitting) 0.5f else 1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = SplitEaseColors.OutlineStrong,
                                    shape = RoundedCornerShape(14.dp),
                                ).background(SplitEaseColors.Surface)
                                .clickable(enabled = !isSubmitting) { photoPicker.launch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoUri != null) {
                            SeGroupIconTile(
                                photoUrl = photoUri,
                                fallbackIcon = Icons.Filled.AddAPhoto,
                                fallbackTint = SplitEaseColors.Primary,
                                size = 72,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = stringResource(R.string.cd_group_photo),
                                tint = SplitEaseColors.Primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    SeTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.label_group_name),
                        // width(0) + weight lets the field shrink; OutlinedTextField's
                        // intrinsic min width otherwise overflows the row.
                        modifier = Modifier.weight(1f).width(0.dp),
                        enabled = !isSubmitting,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                SeSectionHeader(text = stringResource(R.string.label_group_type))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GroupType.entries.forEach { type ->
                        SeTypeChip(
                            label = stringResource(type.labelRes()),
                            icon = type.icon(),
                            selected = selectedType == type,
                            onClick = { groupType = type.name },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SplitEaseColors.PrimarySoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = SplitEaseColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.create_group_invite_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = SplitEaseColors.Navy,
                    )
                }

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeErrorText(it)
                }
            }

            SeLoadingOverlay(
                visible = isSubmitting,
                text = stringResource(R.string.group_creating),
            )
        }
    }
}

private fun GroupType.icon() =
    when (this) {
        GroupType.FRIENDS -> Icons.Filled.Group
        GroupType.HOME -> Icons.Filled.Home
        GroupType.OTHER -> Icons.AutoMirrored.Filled.List
    }

private fun GroupType.tint() =
    when (this) {
        GroupType.FRIENDS -> SplitEaseColors.IconFriends
        GroupType.HOME -> SplitEaseColors.IconHome
        GroupType.OTHER -> SplitEaseColors.IconOther
    }

private fun GroupType.labelRes() =
    when (this) {
        GroupType.FRIENDS -> R.string.group_type_friends
        GroupType.HOME -> R.string.group_type_home
        GroupType.OTHER -> R.string.group_type_other
    }

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (expenseId: String) -> Unit,
    onOpenBalances: () -> Unit,
    onOpenTotals: () -> Unit,
    onOpenPinBoard: () -> Unit,
    onSettleDebt: (
        fromUserId: String,
        toUserId: String,
        amount: String,
        currency: String,
        counterpartyLabel: String,
    ) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
    expensesViewModel: ExpensesViewModel = hiltViewModel(),
    balancesViewModel: BalancesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expensesUi by expensesViewModel.uiState.collectAsStateWithLifecycle()
    val membersState by remember(groupId) { viewModel.observeMembers(groupId) }
        .collectAsStateWithLifecycle()
    val members = membersState.orEmpty()
    val membersReady = membersState != null
    val ledger by remember(groupId) { expensesViewModel.observeGroupLedger(groupId) }
        .collectAsStateWithLifecycle()
    val groupBalance by remember(groupId) { balancesViewModel.observeGroupBalance(groupId) }
        .collectAsStateWithLifecycle()
    val group by remember(groupId) { viewModel.observeGroup(groupId) }
        .collectAsStateWithLifecycle()
    val context = LocalContext.current
    val me = expensesViewModel.currentUserId()
    val isSolo = membersReady && members.size <= 1
    val lifecycleOwner = LocalLifecycleOwner.current
    val bannerColor = lerp((group?.groupType ?: GroupType.OTHER).tint(), SplitEaseColors.Navy, 0.28f)

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Flush local writes + pull remote so other members' expense/payment edits show up.
            expensesViewModel.refreshGroupFromCloud(groupId)
            // Keep Room fresh while this screen is visible (Supabase Realtime).
            expensesViewModel.observeGroupLiveUpdates(groupId)
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
        context.startActivity(Intent.createChooser(intent, shareInvite))
        viewModel.consumeShareText()
    }

    fun openSettle() {
        val debt =
            groupBalance?.simplifiedDebts?.firstOrNull { d ->
                me != null && (d.fromUserId == me || d.toUserId == me)
            }
        if (debt == null || me == null) {
            onOpenBalances()
            return
        }
        val label = if (me == debt.fromUserId) debt.toLabel else debt.fromLabel
        onSettleDebt(
            debt.fromUserId,
            debt.toUserId,
            debt.amount.toPlainString(),
            debt.currencyCode,
            label,
        )
    }

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
        SeSystemBars(
            statusBarColor = Color.Transparent,
            navigationBarColor = bannerColor,
            statusBarDarkIcons = false,
            navigationBarDarkIcons = false,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
        ) {
            GroupDetailBanner(
                group = group,
                bannerColor = bannerColor,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
            )

            if (!(isSolo && ledger.isEmpty())) {
                GroupOverallBalanceBlock(
                    balance = groupBalance,
                    currencyFallback = group?.defaultCurrencyCode.orEmpty(),
                    onClick = onOpenBalances,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeActionChip(
                    label = stringResource(R.string.action_settle_up),
                    onClick = { openSettle() },
                    icon = Icons.Filled.Payments,
                )
                SeActionChip(
                    label = stringResource(R.string.group_chip_balances),
                    onClick = onOpenBalances,
                    icon = Icons.Filled.AccountBalance,
                )
                SeActionChip(
                    label = stringResource(R.string.group_chip_totals),
                    onClick = onOpenTotals,
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                )
                SeActionChip(
                    label = stringResource(R.string.action_open_pin_board),
                    onClick = onOpenPinBoard,
                    icon = Icons.Filled.PushPin,
                )
            }

            SePullRefreshBox(
                isRefreshing = expensesUi.isRefreshing,
                onRefresh = { expensesViewModel.refreshGroupFromCloud(groupId) },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(SplitEaseColors.Surface),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (isSolo && ledger.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                GroupSoloEmptyState(
                                    onAddMembers = onOpenSettings,
                                    onShareLink = {
                                        viewModel.shareGroupLink(groupId)
                                    },
                                )
                            }
                        }
                    } else if (ledger.isEmpty()) {
                        item {
                            SeEmptyState(
                                message = stringResource(R.string.ledger_empty),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    } else {
                        ledgerEntries(ledger, onExpenseClick = onOpenExpense)
                    }
                    (expensesUi.errorMessage ?: uiState.errorMessage)?.let { msg ->
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            SeErrorText(msg, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                    }
                    (expensesUi.infoMessage ?: uiState.infoMessage)?.let { msg ->
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            SeInfoText(msg, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                    }
                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }
}


@Preview(name = "Group detail", showBackground = true)
@Composable
private fun GroupDetailScreenPreview() {
    val sampleGroup =
        Group(
            id = "g1",
            name = "Goa trip",
            defaultCurrencyCode = "INR",        
            groupType = GroupType.FRIENDS,
            createdByUserId = "u1",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
    val bannerColor = lerp(sampleGroup.groupType.tint(), SplitEaseColors.Navy, 0.28f)
    val sampleBalance =
        GroupBalanceUi(
            groupId = sampleGroup.id,
            groupName = sampleGroup.name,
            myNetByCurrency = mapOf("INR" to BigDecimal("-420.00")),
            memberNetsByCurrency = emptyMap(),
            simplifiedDebts =
                listOf(
                    LabeledDebt(
                        fromUserId = "u1",
                        toUserId = "u2",
                        fromLabel = "You",
                        toLabel = "Sam",
                        amount = BigDecimal("420.00"),
                        currencyCode = "INR",
                    ),
                ),
        )

    SePreview {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                SeExtendedFab(
                    text = stringResource(R.string.action_add_expense),
                    onClick = {},
                    icon = Icons.Filled.Receipt,
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
            ) {
                GroupDetailBanner(
                    group = sampleGroup,
                    bannerColor = bannerColor,
                    onBack = {},
                    onOpenSettings = {},
                )
                GroupOverallBalanceBlock(
                    balance = sampleBalance,
                    currencyFallback = sampleGroup.defaultCurrencyCode,
                    onClick = {},
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeActionChip(
                        label = stringResource(R.string.action_settle_up),
                        onClick = {},
                        icon = Icons.Filled.Payments,
                    )
                    SeActionChip(
                        label = stringResource(R.string.group_chip_balances),
                        onClick = {},
                        icon = Icons.Filled.AccountBalance,
                    )
                    SeActionChip(
                        label = stringResource(R.string.group_chip_totals),
                        onClick = {},
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                    )
                    SeActionChip(
                        label = stringResource(R.string.action_open_pin_board),
                        onClick = {},
                        icon = Icons.Filled.PushPin,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(SplitEaseColors.Surface)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    SeEmptyState(message = stringResource(R.string.ledger_empty))
                }
            }
        }
    }
}

@Composable
private fun GroupDetailBanner(
    group: Group?,
    bannerColor: Color,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val coverUrl = group?.coverUrl
    val coverStamp = remember(coverUrl) { localMediaContentStamp(coverUrl) }
    var coverBitmap by remember(coverUrl, coverStamp) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(coverUrl, coverStamp) {
        coverBitmap =
            withContext(Dispatchers.IO) {
                AvatarImageIO
                    .decodeScaled(
                        context = context,
                        photoUrl = coverUrl,
                        maxSidePx = AvatarImageIO.COVER_PREVIEW_MAX_SIDE_PX,
                    )?.asImageBitmap()
            }
    }
    val statusBarHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Extra room below the status bar so the blur can dissolve (no hard clip line).
    val statusBlurHeight = (statusBarHeight + 36.dp).coerceAtLeast(48.dp)
    val statusBlurFade =
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0.0f to Color.White,
                    0.45f to Color.White.copy(alpha = 0.85f),
                    0.75f to Color.White.copy(alpha = 0.35f),
                    1.0f to Color.Transparent,
                ),
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GroupDetailBannerHeight),
    ) {
        coverBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Bottom-weighted gradient so the white title stays readable.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0.0f to Color.Transparent,
                                        0.55f to Color.Black.copy(alpha = 0.12f),
                                        1.0f to Color.Black.copy(alpha = 0.45f),
                                    ),
                            ),
                        ),
            )
        } ?: run {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(bannerColor),
            )
        }

        // Status-bar blur that fades out (masked) — only when a cover photo is showing.
        coverBitmap?.let { bitmap ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(statusBlurHeight)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = statusBlurFade, blendMode = BlendMode.DstIn)
                        },
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .blur(radius = 24.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerCircleIconButton(
                onClick = onBack,
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.cd_back),
                solid = coverBitmap == null,
            )
            BannerCircleIconButton(
                onClick = onOpenSettings,
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_group_settings),
                solid = coverBitmap == null,
            )
        }

        Text(
            text = group?.name ?: stringResource(R.string.groups_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 28.dp)
                    .fillMaxWidth(),
        )
    }
}

/** Fixed height for the group detail header banner (includes status-bar inset). */
private val GroupDetailBannerHeight = 180.dp

/** Local file mtime used to bust Compose bitmap cache when a cover is overwritten. */
private fun localMediaContentStamp(path: String?): Long {
    if (path.isNullOrBlank()) return 0L
    if (
        path.startsWith("http://", ignoreCase = true) ||
        path.startsWith("https://", ignoreCase = true) ||
        path.startsWith("content:", ignoreCase = true)
    ) {
        return 0L
    }
    val filePath =
        if (path.startsWith("file:", ignoreCase = true)) {
            Uri.parse(path).path
        } else {
            path
        } ?: return 0L
    return File(filePath).takeIf { it.exists() }?.lastModified() ?: 0L
}

@Composable
internal fun BannerCircleIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    /** Solid white chip (no cover). Translucent when false (photo cover). */
    solid: Boolean = true,
) {
    val background = if (solid) Color.White else Color.White.copy(alpha = 0.22f)
    val border = if (solid) Color.Transparent else Color.White.copy(alpha = 0.40f)
    val iconTint = if (solid) SplitEaseColors.Navy else Color.White
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(background)
                .border(
                    width = 1.dp,
                    color = border,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun GroupOverallBalanceBlock(
    balance: GroupBalanceUi?,
    currencyFallback: String,
    onClick: (() -> Unit)? = null,
) {
    if (balance == null) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    val myDebts =
        balance.simplifiedDebts.filter { debt ->
            debt.fromLabel == "You" || debt.toLabel == "You"
        }
    val nets = balance.myNetByCurrency
    val canExpand = myDebts.isNotEmpty() && onClick == null
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SplitEaseColors.Surface)
                .clickable {
                    if (onClick != null) {
                        onClick()
                    } else if (myDebts.isNotEmpty()) {
                        expanded = !expanded
                    }
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GroupOverallHeadline(
                    nets = nets,
                    currencyFallback = currencyFallback.ifBlank { AppCurrencies.DEFAULT },
                )
            }
            if (canExpand) {
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        }
        AnimatedVisibility(visible = canExpand && expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                myDebts.forEach { debt ->
                    GroupOverallDebtLine(debt)
                }
            }
        }
    }
}

@Composable
private fun GroupOverallHeadline(
    nets: Map<String, BigDecimal>,
    currencyFallback: String,
) {
    when {
        nets.isEmpty() || nets.values.all { it.compareTo(BigDecimal.ZERO) == 0 } -> {
            Text(
                text = stringResource(R.string.balances_settled_overall),
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Settled,
                fontWeight = FontWeight.SemiBold,
            )
        }
        else -> {
            val (currency, net) =
                nets.entries.firstOrNull { it.value.compareTo(BigDecimal.ZERO) != 0 }
                    ?: return
            val code = currency.ifBlank { currencyFallback }
            val money = MoneyFormat.format(net.abs(), code)
            val youOwe = net < BigDecimal.ZERO
            val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
            val template =
                if (youOwe) {
                    stringResource(R.string.balances_you_owe_overall, money)
                } else {
                    stringResource(R.string.balances_you_are_owed_overall, money)
                }
            // Highlight the money substring inside the localized sentence.
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
            )
        }
    }
}

@Composable
private fun GroupOverallDebtLine(debt: LabeledDebt) {
    val youOwe = debt.fromLabel == "You"
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .background(
                        if (youOwe) {
                            SplitEaseColors.YouOwe.copy(alpha = 0.35f)
                        } else {
                            SplitEaseColors.OwedToYou.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(2.dp),
                    ),
        )
        SeMoneyText(
            amount = debt.amount,
            currencyCode = debt.currencyCode,
            tone = if (youOwe) SeMoneyTone.YOU_OWE else SeMoneyTone.OWED_TO_YOU,
            prefix =
                if (youOwe) {
                    stringResource(R.string.balances_you_owe_person, debt.toLabel)
                } else {
                    stringResource(R.string.balances_person_owes_you, debt.fromLabel)
                },
        )
    }
}

@Composable
private fun GroupSoloEmptyState(
    onAddMembers: () -> Unit,
    onShareLink: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_solo_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.Navy,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_add_group_members),
            onClick = onAddMembers,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SeOutlinedButton(
            text = stringResource(R.string.action_share_group_link),
            onClick = onShareLink,
        )
    }
}
