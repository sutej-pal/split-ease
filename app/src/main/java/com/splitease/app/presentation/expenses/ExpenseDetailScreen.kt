package com.splitease.app.presentation.expenses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.domain.model.ExpenseCommentKind
import com.splitease.app.domain.spending.GroupMonthSpending
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.common.shortDisplayName
import com.splitease.app.presentation.media.rememberAttachmentImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeLoadingOverlay
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTopBar
import java.math.BigDecimal
import java.text.DateFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CategoryPastels =
    listOf(
        Color(0xFFFFE0E6),
        Color(0xFFE0F5E9),
        Color(0xFFE8E0F5),
        Color(0xFFFFF3D6),
        Color(0xFFE0F0FF),
        Color(0xFFFFE8D6),
    )

/**
 * Expense detail: summary, payer/owes, attachments, comments, optional group spending trends.
 */
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    onBack: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onDeleted: () -> Unit,
    onOpenGroupSpending: ((groupId: String) -> Unit)? = null,
    onOpenAttachments: (expenseId: String, startIndex: Int) -> Unit = { _, _ -> },
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val detail by viewModel.observeExpenseDetail(expenseId).collectAsStateWithLifecycle()
    val comments by viewModel.observeExpenseComments(expenseId).collectAsStateWithLifecycle()
    val attachments by viewModel.observeExpensePhotos(expenseId).collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    val hasExpense = detail != null
    val me = viewModel.currentUserId()
    val bg = MaterialTheme.colorScheme.background
    val lightIconsOnBars = bg.luminance() > 0.5f

    LaunchedEffect(expenseId) {
        viewModel.refreshExpenseSideData(expenseId)
    }

    val attachmentPicker =
        rememberAttachmentImagePicker(
            sourceTitle = stringResource(R.string.expense_photo_source_title),
            sourceBody = stringResource(R.string.expense_photo_source_body),
        ) { uris ->
            viewModel.addExpenseAttachments(expenseId = expenseId, photoUris = uris)
        }

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = lightIconsOnBars,
        navigationBarDarkIcons = lightIconsOnBars,
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = bg,
        topBar = {
            val detailSnapshot = detail
            SeTopBar(
                title = "",
                onBack = onBack,
                navigationExtra = {
                    if (detailSnapshot != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        CategoryChip(
                            iconKey = detailSnapshot.categoryIconKey,
                            onClick = { onEdit(expenseId) },
                        )
                    }
                },
                actions = {
                    if (hasExpense) {
                        IconButton(
                            onClick = { attachmentPicker.launch() },
                            enabled = !uiState.isAttachingPhotos,
                        ) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = stringResource(R.string.cd_add_expense_photo),
                                tint = SplitEaseColors.Navy,
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_delete_expense),
                                tint = SplitEaseColors.Navy,
                            )
                        }
                        IconButton(onClick = { onEdit(expenseId) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit_expense),
                                tint = SplitEaseColors.Navy,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (hasExpense) {
                ExpenseCommentBar(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    onSend = {
                        val draft = commentDraft
                        viewModel.addExpenseComment(expenseId, draft) {
                            commentDraft = ""
                        }
                    },
                )
            }
        },
    ) { padding ->
        val snapshot = detail
        if (snapshot == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SeLayout.detailHorizontal),
            ) {
                SeErrorText(uiState.errorMessage ?: stringResource(R.string.expense_not_found))
                Spacer(modifier = Modifier.height(16.dp))
                SeTextButton(
                    text = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SeLayout.detailHorizontal)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                text = snapshot.expense.description,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    MoneyFormat.format(
                        snapshot.expense.amount,
                        snapshot.expense.currencyCode,
                    ),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.expense_added_by_on,
                        addedByDisplayName(snapshot.payerLabel),
                        formatExpenseAddedDate(snapshot.expense.expenseDateEpochMs),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
            Spacer(modifier = Modifier.height(20.dp))

            ExpensePaidOwesBlock(
                detail = snapshot,
                currentUserId = me,
            )

            if (!snapshot.expense.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.label_notes),
                    style = MaterialTheme.typography.labelMedium,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snapshot.expense.notes.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitEaseColors.Navy,
                )
            }

            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.expense_attachments_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExpenseAttachmentsPreviewRow(
                    attachments = attachments,
                    onOpenAttachment = { index -> onOpenAttachments(expenseId, index) },
                )
            }

            if (snapshot.spendingTrendMonths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(20.dp))
                ExpenseSpendingTrendsSection(
                    detail = snapshot,
                    onViewMoreCharts =
                        snapshot.expense.groupId?.let { gid ->
                            onOpenGroupSpending?.let { open -> { open(gid) } }
                        },
                )
            }

            if (comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.expense_comments_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(12.dp))
                comments.forEach { comment ->
                    ExpenseCommentRow(comment = comment)
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            val error = uiState.errorMessage
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SeErrorText(error)
            }
        }
    }

    SeLoadingOverlay(
        visible = uiState.isAttachingPhotos,
        text = stringResource(R.string.expense_attaching_photos),
    )

    if (showDeleteConfirm) {
        SeConfirmDialog(
            title = stringResource(R.string.expense_delete_title),
            body = stringResource(R.string.expense_delete_body),
            confirmLabel = stringResource(R.string.action_delete_expense),
            onDismissRequest = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteExpense(expenseId, onSuccess = onDeleted)
            },
            icon = Icons.Filled.Delete,
            tone = SeConfirmTone.Danger,
        )
    }
    }
}

private val ExpenseAttachmentThumbSize = 112.dp
private const val ExpenseAttachmentVisibleCount = 2

@Composable
private fun ExpenseAttachmentsPreviewRow(
    attachments: List<ExpensePhotoUi>,
    onOpenAttachment: (Int) -> Unit,
) {
    val overflowCount = (attachments.size - ExpenseAttachmentVisibleCount).coerceAtLeast(0)
    val previewItems =
        if (overflowCount > 0) {
            attachments.take(ExpenseAttachmentVisibleCount) + null
        } else {
            attachments
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        previewItems.forEachIndexed { index, attachment ->
            if (attachment == null) {
                ExpenseAttachmentOverflowCard(
                    overflowCount = overflowCount,
                    onClick = { onOpenAttachment(ExpenseAttachmentVisibleCount) },
                )
            } else {
                ExpenseAttachmentThumbCard(
                    attachment = attachment,
                    onClick = { onOpenAttachment(index) },
                )
            }
        }
    }
}

private sealed interface ExpenseAttachmentThumbLoadState {
    data object Loading : ExpenseAttachmentThumbLoadState

    data class Ready(
        val bitmap: ImageBitmap,
    ) : ExpenseAttachmentThumbLoadState

    data object Failed : ExpenseAttachmentThumbLoadState
}

@Composable
private fun ExpenseAttachmentThumbCard(
    attachment: ExpensePhotoUi,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val loadState by produceState<ExpenseAttachmentThumbLoadState>(
        ExpenseAttachmentThumbLoadState.Loading,
        attachment.displayUri,
    ) {
        value = ExpenseAttachmentThumbLoadState.Loading
        value =
            withContext(Dispatchers.IO) {
                val decoded =
                    AvatarImageIO
                        .decodeScaled(
                            context = context,
                            photoUrl = attachment.displayUri,
                            maxSidePx = AvatarImageIO.ATTACHMENT_PREVIEW_MAX_SIDE_PX,
                        )?.asImageBitmap()
                if (decoded != null) {
                    ExpenseAttachmentThumbLoadState.Ready(decoded)
                } else {
                    ExpenseAttachmentThumbLoadState.Failed
                }
            }
    }

    Box(
        modifier =
            Modifier
                .size(ExpenseAttachmentThumbSize)
                .clip(RoundedCornerShape(12.dp))
                .background(SplitEaseColors.Outline.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        when (val imageLoad = loadState) {
            is ExpenseAttachmentThumbLoadState.Ready -> {
                Image(
                    bitmap = imageLoad.bitmap,
                    contentDescription = stringResource(R.string.cd_expense_attachment),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ExpenseAttachmentThumbLoadState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = SplitEaseColors.NavyMuted,
                )
            }
            ExpenseAttachmentThumbLoadState.Failed -> Unit
        }
    }
}

@Composable
private fun ExpenseAttachmentOverflowCard(
    overflowCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(ExpenseAttachmentThumbSize)
                .clip(RoundedCornerShape(12.dp))
                .background(SplitEaseColors.PrimarySoft)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.expense_attachments_more, overflowCount),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = SplitEaseColors.PrimaryDark,
        )
    }
}

@Composable
private fun ExpenseCommentRow(comment: ExpenseCommentUi) {
    val isSystem = comment.kind == ExpenseCommentKind.SYSTEM
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        SeAvatarBadge(
            name = comment.authorLabel,
            photoUrl = comment.authorPhotoUrl,
            size = 36.dp,
            borderWidth = 0.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCommentTime(comment.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = SplitEaseColors.NavyMuted,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSystem) SplitEaseColors.NavyMuted else SplitEaseColors.Navy,
                fontStyle = if (isSystem) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

@Composable
private fun CategoryChip(
    iconKey: String,
    onClick: () -> Unit,
) {
    val pastel =
        CategoryPastels[
            iconKey.hashCode().mod(CategoryPastels.size).let { if (it < 0) it + CategoryPastels.size else it },
        ]
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                ).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pastel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(iconKey),
                contentDescription = stringResource(R.string.cd_expense_category),
                tint = SplitEaseColors.Navy.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = SplitEaseColors.NavyMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

private data class ExpenseOweTreeNode(
    val userId: String,
    val displayName: String,
    val amountLabel: String,
    val isViewer: Boolean,
    val photoUrl: String?,
)

@Composable
private fun ExpensePaidOwesBlock(
    detail: ExpenseDetailUi,
    currentUserId: String?,
) {
    val currency = detail.expense.currencyCode
    val paidMoney = MoneyFormat.format(detail.expense.amount, currency)
    val isViewerPayer = detail.expense.paidByUserId == currentUserId
    val paidLine =
        if (isViewerPayer) {
            stringResource(R.string.activity_you_paid, paidMoney)
        } else {
            stringResource(R.string.ledger_paid_by, detail.payerLabel, paidMoney)
        }
    val avatarName =
        if (isViewerPayer) {
            stringResource(R.string.you_label)
        } else {
            detail.payerLabel
        }
    val youLabel = stringResource(R.string.you_label)
    val owesWord = stringResource(R.string.expense_split_owes)

    val oweNodes =
        remember(detail.splits, currentUserId, currency, youLabel, owesWord) {
            buildList {
                detail.splits
                    .filter { it.owedAmount.compareTo(BigDecimal.ZERO) != 0 }
                    .sortedByDescending { it.userId == currentUserId }
                    .forEach { line ->
                        val isViewer = line.userId == currentUserId
                        val money = MoneyFormat.format(line.owedAmount, currency)
                        add(
                            ExpenseOweTreeNode(
                                userId = line.userId,
                                displayName =
                                    if (isViewer) {
                                        youLabel
                                    } else {
                                        shortDisplayName(line.participantLabel)
                                    },
                                amountLabel =
                                    if (isViewer) {
                                        money
                                    } else {
                                        "$owesWord $money"
                                    },
                                isViewer = isViewer,
                                photoUrl = line.photoUrl,
                            ),
                        )
                    }
            }
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeAvatarBadge(
                name = avatarName,
                photoUrl = detail.payerPhotoUrl,
                size = 48.dp,
                borderWidth = 0.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = paidLine,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
                modifier = Modifier.weight(1f),
            )
        }

        if (oweNodes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            oweNodes.forEachIndexed { index, node ->
                ExpenseOweTreeRow(
                    node = node,
                    isLast = index == oweNodes.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun ExpenseOweTreeRow(
    node: ExpenseOweTreeNode,
    isLast: Boolean,
) {
    val branchColor = SplitEaseColors.OutlineStrong
    val rowHeight = 40.dp
    val gutterWidth = 48.dp
    val avatarSize = 28.dp

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
            // Vertical trunk from payer avatar center.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight(if (isLast) 0.5f else 1f)
                        .background(branchColor),
            )
            // Horizontal branch to child avatar.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = gutterWidth / 2)
                        .width(gutterWidth / 2 - 4.dp)
                        .height(2.dp)
                        .background(branchColor),
            )
        }
        SeAvatarBadge(
            name = node.displayName,
            photoUrl = node.photoUrl,
            size = avatarSize,
            borderWidth = 0.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text =
                buildAnnotatedString {
                    if (node.isViewer) {
                        withStyle(SpanStyle(color = SplitEaseColors.NavyMuted)) {
                            append(stringResource(R.string.expense_you_owe_amount, node.amountLabel))
                        }
                    } else {
                        withStyle(SpanStyle(color = SplitEaseColors.NavyMuted)) {
                            append(node.displayName)
                            append(" ")
                        }
                        withStyle(
                            SpanStyle(
                                color = SplitEaseColors.NavyMuted,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(node.amountLabel)
                        }
                    }
                },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpenseSpendingTrendsSection(
    detail: ExpenseDetailUi,
    onViewMoreCharts: (() -> Unit)?,
) {
    val scopeLabel =
        when {
            !detail.groupName.isNullOrBlank() ->
                stringResource(
                    R.string.expense_spending_trends_for,
                    "${detail.groupName} :: ${detail.categoryName}",
                )
            else ->
                stringResource(
                    R.string.expense_spending_trends_for,
                    detail.categoryName,
                )
        }
    Text(
        text = scopeLabel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = SplitEaseColors.Navy,
    )
    Spacer(modifier = Modifier.height(14.dp))
    ExpenseTrendBars(
        months = detail.spendingTrendMonths,
        currencyCode = detail.expense.currencyCode,
    )
    if (onViewMoreCharts != null) {
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onViewMoreCharts,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = SplitEaseColors.Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Insights,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.expense_view_more_charts),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ExpenseTrendBars(
    months: List<GroupMonthSpending>,
    currencyCode: String,
) {
    if (months.isEmpty()) return
    val maxAmount =
        months.maxOf { it.totalSpent.toDouble() }.coerceAtLeast(0.01)
    val barColor = SplitEaseColors.OutlineStrong.copy(alpha = 0.45f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        months.forEach { month ->
            val fraction =
                (month.totalSpent.toDouble() / maxAmount).toFloat().coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortMonthLabel(month.month),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SplitEaseColors.Navy,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = MoneyFormat.format(month.totalSpent, currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.Navy,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCommentBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = SeLayout.detailHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.expense_add_comment),
                    color = SplitEaseColors.NavyMuted,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SplitEaseColors.OutlineStrong,
                    unfocusedBorderColor = SplitEaseColors.OutlineStrong,
                    focusedContainerColor = SplitEaseColors.Surface,
                    unfocusedContainerColor = SplitEaseColors.Surface,
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.cd_send_comment),
                tint =
                    if (value.isNotBlank()) {
                        SplitEaseColors.Primary
                    } else {
                        SplitEaseColors.NavyMuted
                    },
            )
        }
    }
}

private fun addedByDisplayName(payerLabel: String): String =
    if (payerLabel.equals("You", ignoreCase = true)) {
        "you"
    } else {
        payerLabel
    }

private fun formatExpenseAddedDate(epochMs: Long): String {
    val date =
        Instant
            .ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(date)
}

private fun formatCommentTime(epochMs: Long): String {
    val dateTime =
        Instant
            .ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault()).format(dateTime)
}

private fun shortMonthLabel(month: Int): String {
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
    val name = symbols.shortMonths.getOrNull(month).orEmpty()
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
