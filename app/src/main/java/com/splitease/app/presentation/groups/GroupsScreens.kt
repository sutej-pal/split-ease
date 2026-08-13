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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
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
import com.splitease.app.presentation.common.shortDisplayName
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.expenses.LedgerBalanceSide
import com.splitease.app.presentation.expenses.LedgerListItem
import com.splitease.app.presentation.expenses.ledgerEntries
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeExtendedFab
import com.splitease.app.presentation.ui.SeGroupIconTile
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SePullRefreshBox
import com.splitease.app.presentation.ui.SeSectionHeader
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTypeChip
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.get
import androidx.core.net.toUri

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
    val photoPicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.group_photo_source_title),
            sourceBody = stringResource(R.string.group_photo_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.GroupPhoto,
        ) { photoUri = it }
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
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusManager.clearFocus() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                // Bottom-align so the photo tracks the outlined box (not the float-label inset).
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 1.dp,
                                color = SplitEaseColors.OutlineStrong,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .background(SplitEaseColors.Surface)
                            .clickable(enabled = !isSubmitting) { photoPicker.launch() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photoUri != null) {
                        SeGroupIconTile(
                            photoUrl = photoUri,
                            fallbackIcon = Icons.Filled.AddAPhoto,
                            fallbackTint = SplitEaseColors.Primary,
                            size = 56,
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
                    modifier = Modifier
                        .weight(1f)
                        .width(0.dp),
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

            Spacer(modifier = Modifier.height(24.dp))
            SePrimaryButton(
                text =
                    if (isSubmitting) {
                        stringResource(R.string.group_creating)
                    } else {
                        stringResource(R.string.action_create_group)
                    },
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
    val isSolo = membersReady && (members.size <= 1)
    val nets = groupBalance?.myNetByCurrency
    val iAmSettled =
        nets != null &&
            (nets.isEmpty() || nets.values.all { it.compareTo(BigDecimal.ZERO) == 0 })
    var showSettledExpenses by rememberSaveable(groupId) { mutableStateOf(value = false) }
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

    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedBannerHeight = statusBarHeight + GroupDetailBannerToolbarHeight
    val expandedBannerHeight =
        maxOf(GroupDetailBannerHeight, collapsedBannerHeight + GroupDetailBannerCollapseRange)
    val collapseRangePx =
        with(density) { (expandedBannerHeight - collapsedBannerHeight).toPx() }
            .coerceAtLeast(1f)
    var toolbarOffsetPx by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection =
        remember(collapseRangePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    _source: NestedScrollSource,
                ): Offset {
                    // Collapse the banner before the list scrolls up.
                    if (available.y >= 0f) return Offset.Zero
                    val previous = toolbarOffsetPx
                    val next = (previous + available.y).coerceIn(-collapseRangePx, 0f)
                    toolbarOffsetPx = next
                    return Offset(0f, next - previous)
                }

                override fun onPostScroll(
                    _consumed: Offset,
                    available: Offset,
                    _source: NestedScrollSource,
                ): Offset {
                    // Expand the banner after the list can no longer scroll down.
                    if (available.y <= 0f) return Offset.Zero
                    val previous = toolbarOffsetPx
                    val next = (previous + available.y).coerceIn(-collapseRangePx, 0f)
                    toolbarOffsetPx = next
                    return Offset(0f, next - previous)
                }
            }
        }
    val collapseFraction = (-toolbarOffsetPx / collapseRangePx).coerceIn(0f, 1f)
    val bannerHeight =
        lerpDp(expandedBannerHeight, collapsedBannerHeight, collapseFraction)
    var statusBarDarkIcons by remember(bannerColor) {
        mutableStateOf(bannerColor.luminance() > 0.5f)
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
            // Bottom of the screen is light content / FAB — keep dark system-nav glyphs.
            // (Previously used bannerColor + light icons, which leaked white glyphs onto
            // the main tab bottom bar after pop.)
            navigationBarColor = MaterialTheme.colorScheme.background,
            statusBarDarkIcons = statusBarDarkIcons,
            navigationBarDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                    .nestedScroll(nestedScrollConnection),
        ) {
            GroupDetailBanner(
                group = group,
                bannerColor = bannerColor,
                bannerHeight = bannerHeight,
                collapseFraction = collapseFraction,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onStatusBarDarkIconsChange = { statusBarDarkIcons = it },
            )

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
                    if (!(isSolo && ledger.isEmpty())) {
                        item {
                            GroupOverallBalanceBlock(
                                balance = groupBalance,
                                currencyFallback = group?.defaultCurrencyCode.orEmpty(),
                                currentUserId = me,
                            )
                        }
                    }

                    item {
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
                    }

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
                    } else if (iAmSettled && !showSettledExpenses) {
                        item {
                            GroupSettledUpState(
                                onClick = { showSettledExpenses = true },
                            )
                        }
                    } else {
                        if (iAmSettled) {
                            item {
                                Text(
                                    text = stringResource(R.string.group_all_settled_hide),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SplitEaseColors.NavyMuted,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { showSettledExpenses = false }
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
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

@Composable
private fun GroupDetailBanner(
    group: Group?,
    bannerColor: Color,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    bannerHeight: Dp = GroupDetailBannerHeight,
    collapseFraction: Float = 0f,
    onStatusBarDarkIconsChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val coverUrl = group?.coverUrl
    val coverStamp = remember(coverUrl) { localMediaContentStamp(coverUrl) }
    var coverBitmap by remember(coverUrl, coverStamp) { mutableStateOf<ImageBitmap?>(null) }
    var statusBarDarkIcons by remember(bannerColor) {
        mutableStateOf(bannerColor.luminance() > 0.5f)
    }
    val onStatusBarDarkIconsChangeUpdated by rememberUpdatedState(onStatusBarDarkIconsChange)
    LaunchedEffect(coverUrl, coverStamp, bannerColor) {
        val (bitmap, darkIcons) =
            withContext(Dispatchers.IO) {
                val decoded =
                    AvatarImageIO.decodeScaled(
                        context = context,
                        photoUrl = coverUrl,
                        maxSidePx = AvatarImageIO.COVER_PREVIEW_MAX_SIDE_PX,
                    )
                if (decoded != null) {
                    // Status strip is dimmed by [GroupDetailStatusBarDimAlpha]; use effective luminance.
                    val effective =
                        decoded.averageTopLuminance() * (1f - GroupDetailStatusBarDimAlpha)
                    decoded.asImageBitmap() to (effective > 0.5f)
                } else {
                    null to (bannerColor.luminance() > 0.5f)
                }
            }
        coverBitmap = bitmap
        statusBarDarkIcons = darkIcons
        onStatusBarDarkIconsChangeUpdated(darkIcons)
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
    val title = group?.name ?: stringResource(R.string.groups_title)
    // Sequential crossfade: large title fully gone before compact title appears.
    val expandedTitleAlpha = (1f - collapseFraction * 2f).coerceIn(0f, 1f)
    val collapsedTitleAlpha = ((collapseFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val expandedTitleBottomPad = lerpDp(28.dp, 8.dp, collapseFraction)
    // Solid white chips (dark glyphs) on bright regions; translucent light chips on dark covers.
    val solidChrome = coverBitmap == null || statusBarDarkIcons

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .clipToBounds(),
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
                            .background(Color.Black.copy(alpha = GroupDetailStatusBarDimAlpha)),
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerCircleIconButton(
                onClick = onBack,
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.cd_back),
                solid = solidChrome,
            )
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        shadow = GroupDetailTitleShadow,
                    ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer { alpha = collapsedTitleAlpha },
            )
            BannerCircleIconButton(
                onClick = onOpenSettings,
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_group_settings),
                solid = solidChrome,
            )
        }

        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineMedium.copy(
                    shadow = GroupDetailTitleShadow,
                ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = expandedTitleBottomPad)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = expandedTitleAlpha },
        )
    }
}

/** Soft drop shadow so the white group title stays readable on busy covers. */
private val GroupDetailTitleShadow =
    Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 2f),
        blurRadius = 6f,
    )

/** Darkening overlay on the status-bar blur strip (must match the drawn scrim). */
private const val GroupDetailStatusBarDimAlpha = 0.42f

/** Expanded height for the group detail header banner (includes status-bar inset). */
private val GroupDetailBannerHeight = 180.dp

/** Collapsed toolbar content height below the status bar (8 + 40 + 8). */
private val GroupDetailBannerToolbarHeight = 56.dp

/** Minimum shrink distance so the banner always has room to collapse. */
private val GroupDetailBannerCollapseRange = 96.dp

/**
 * Average relative luminance of the top strip of [this] (where status-bar icons sit).
 * Samples a coarse grid for speed.
 */
private fun android.graphics.Bitmap.averageTopLuminance(topFraction: Float = 0.2f): Float {
    val sampleHeight = (height * topFraction).toInt().coerceIn(1, height)
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (sampleHeight / 8).coerceAtLeast(1)
    var sum = 0.0
    var count = 0
    var y = 0
    while (y < sampleHeight) {
        var x = 0
        while (x < width) {
            val pixel = this[x, y]
            val r = ((pixel ushr 16) and 0xFF) / 255.0
            val g = ((pixel ushr 8) and 0xFF) / 255.0
            val b = (pixel and 0xFF) / 255.0
            sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) 0f else (sum / count).toFloat()
}

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
            path.toUri().path
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
    currentUserId: String? = null,
) {
    if (balance == null) return
    var expanded by rememberSaveable { mutableStateOf(value = true) }
    val youLabel = stringResource(R.string.you_label)
    val myDebts =
        remember(balance.simplifiedDebts, currentUserId, youLabel) {
            balance.simplifiedDebts.filter { debt ->
                if (currentUserId != null) {
                    debt.fromUserId == currentUserId || debt.toUserId == currentUserId
                } else {
                    debt.fromLabel.equals(youLabel, ignoreCase = true) ||
                        debt.toLabel.equals(youLabel, ignoreCase = true)
                }
            }
        }
    val nets = balance.myNetByCurrency
    val canExpand = myDebts.isNotEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(horizontal = SeLayout.detailHorizontal, vertical = 14.dp),
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
                    contentDescription = stringResource(R.string.cd_toggle_balance_details),
                    tint = SplitEaseColors.NavyMuted,
                )
            }
        }
        AnimatedVisibility(visible = canExpand && expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                myDebts.forEachIndexed { index, debt ->
                    GroupOverallDebtTreeRow(
                        debt = debt,
                        isLast = index == myDebts.lastIndex,
                        currentUserId = currentUserId,
                        youLabel = youLabel,
                    )
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
private fun GroupOverallDebtTreeRow(
    debt: LabeledDebt,
    isLast: Boolean,
    currentUserId: String?,
    youLabel: String,
) {
    val youOwe =
        if (currentUserId != null) {
            debt.fromUserId == currentUserId
        } else {
            debt.fromLabel.equals(youLabel, ignoreCase = true)
        }
    val otherLabel =
        shortDisplayName(
            if (youOwe) debt.toLabel else debt.fromLabel,
        )
    val accent = if (youOwe) SplitEaseColors.YouOwe else SplitEaseColors.OwedToYou
    val relation =
        if (youOwe) {
            stringResource(R.string.balances_you_owe_person, otherLabel)
        } else {
            stringResource(R.string.balances_person_owes_you, otherLabel)
        }
    val money = MoneyFormat.format(debt.amount, debt.currencyCode)
    val rowHeight = 44.dp
    val gutterWidth = 28.dp
    val avatarSize = 24.dp

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(gutterWidth)
                    .fillMaxSize(),
        ) {
            val branchColor = SplitEaseColors.OutlineStrong
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight(if (isLast) 0.5f else 1f)
                        .background(branchColor),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = gutterWidth / 2)
                        .width(gutterWidth / 2 - 2.dp)
                        .height(2.dp)
                        .background(branchColor),
            )
        }
        SeAvatarBadge(
            name = otherLabel,
            photoUrl = if (youOwe) debt.toPhotoUrl else debt.fromPhotoUrl,
            size = avatarSize,
            borderWidth = 0.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = relation,
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.Navy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = money,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun GroupSettledUpState(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                )
                .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_all_settled_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SplitEaseColors.Navy,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.group_all_settled_show),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitEaseColors.NavyMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        SettledCheckmarkGraphic(modifier = Modifier.size(120.dp))
    }
}

@Composable
private fun SettledCheckmarkGraphic(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        tint = SplitEaseColors.Primary,
        modifier = modifier,
    )
}

@Preview(name = "Group Solo Empty State", showBackground = true)
@Composable
fun GroupSoloEmptyStatePreview() {
    GroupSoloEmptyState(
        onAddMembers = {},
        onShareLink = {},
    )
}

@Preview(name = "Group Settled Up State", showBackground = true)
@Composable
private fun GroupSettledUpStatePreview() {
    GroupSettledUpState(onClick = {})
}

@Preview(name = "Group Overall Balance Block", showBackground = true)
@Composable
fun GroupOverallBalanceBlockPreview() {
    GroupOverallBalanceBlock(
        balance =
            GroupBalanceUi(
                groupId = "1",
                groupName = "Group 1",
                myNetByCurrency = mapOf("INR" to BigDecimal("1200.50")),
                memberNetsByCurrency =
                    mapOf(
                        "1" to mapOf("INR" to BigDecimal("1200.50")),
                        "2" to mapOf("INR" to BigDecimal("-1200.50")),
                    ),
                simplifiedDebts =
                    listOf(
                        LabeledDebt(
                            fromUserId = "2",
                            fromLabel = "John Doe",
                            toUserId = "1",
                            toLabel = "You",
                            amount = BigDecimal("1200.50"),
                            currencyCode = "INR",
                        ),
                    ),
            ),
        currencyFallback = "INR",
        currentUserId = "1",
    )
}

@Preview(name = "Group expense list", showBackground = true, heightDp = 720)
@Composable
private fun GroupExpenseListPreview() {
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
                    LabeledDebt(
                        fromUserId = "u3",
                        toUserId = "u1",
                        fromLabel = "Alex",
                        toLabel = "You",
                        amount = BigDecimal("80.00"),
                        currencyCode = "INR",
                    ),
                ),
        )
    val sampleLedger =
        listOf(
            LedgerListItem(
                id = "expense-1",
                isPayment = false,
                title = "Laxmikant for rent",
                subtitle = "You paid ₹5,500.00",
                sortEpochMs = 1_754_320_000_000L,
                categoryIconKey = "category_rent",
                currencyCode = "INR",
                balanceSide = LedgerBalanceSide.LENT,
                balanceAmount = BigDecimal("5500.00"),
            ),
            LedgerListItem(
                id = "expense-2",
                isPayment = false,
                title = "Maggie",
                subtitle = "You paid ₹27.00",
                sortEpochMs = 1_754_200_000_000L,
                categoryIconKey = "category_general",
                currencyCode = "INR",
                balanceSide = LedgerBalanceSide.LENT,
                balanceAmount = BigDecimal("27.00"),
            ),
            LedgerListItem(
                id = "expense-3",
                isPayment = false,
                title = "Pizza",
                subtitle = "You paid ₹550.00",
                sortEpochMs = 1_753_800_000_000L,
                categoryIconKey = "category_food",
                currencyCode = "INR",
                balanceSide = LedgerBalanceSide.LENT,
                balanceAmount = BigDecimal("366.67"),
            ),
            LedgerListItem(
                id = "expense-4",
                isPayment = false,
                title = "Burger king",
                subtitle = "Sam paid ₹414.00",
                sortEpochMs = 1_753_500_000_000L,
                categoryIconKey = "category_food",
                currencyCode = "INR",
                balanceSide = LedgerBalanceSide.BORROWED,
                balanceAmount = BigDecimal("207.00"),
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
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(SplitEaseColors.Surface),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        GroupOverallBalanceBlock(
                            balance = sampleBalance,
                            currencyFallback = sampleGroup.defaultCurrencyCode,
                            currentUserId = "u1",
                        )
                    }
                    item {
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
                    }
                    ledgerEntries(sampleLedger)
                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }
}
