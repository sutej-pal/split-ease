package com.splitease.app.presentation.groups

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.splitease.app.R
import com.splitease.app.domain.spending.GroupMonthSpending
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.expenses.ExpensesViewModel
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeInlineLoader
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.seDetailHorizontal
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Group spending totals: monthly chart, total spent / your share, period control.
 * Back returns to group detail. No Pro upsell.
 */
@Composable
fun GroupTotalsScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupTotalsViewModel = hiltViewModel(),
    expensesViewModel: ExpensesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showTerms by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val bg = MaterialTheme.colorScheme.background
    val lightIcons = bg.luminance() > 0.5f

    LaunchedEffect(groupId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expensesViewModel.refreshGroupFromCloud(groupId)
        }
    }

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = lightIcons,
        navigationBarDarkIcons = lightIcons,
    )

    Scaffold(
        containerColor = bg,
        topBar = {
            SeTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showTerms = true }) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.cd_totals_help),
                            tint = SplitEaseColors.Navy,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!ui.isLoading) {
                TotalsPeriodBar(
                    allTime = ui.allTime,
                    year = ui.selectedYear,
                    month = ui.selectedMonth,
                    onAllTime = viewModel::selectAllTime,
                    onMonthMode = viewModel::selectMonthMode,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = SeLayout.detailHorizontal, vertical = 12.dp),
                )
            }
        },
    ) { padding ->
        if (ui.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                SeInlineLoader()
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                        .seDetailHorizontal(),
            ) {
                Text(
                    text = ui.groupName.ifBlank { stringResource(R.string.group_chip_totals) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        if (ui.allTime) {
                            stringResource(R.string.totals_subtitle_all_time)
                        } else {
                            stringResource(
                                R.string.totals_subtitle_month,
                                formatMonthYear(ui.selectedYear, ui.selectedMonth),
                            )
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitEaseColors.NavyMuted,
                )
                Spacer(modifier = Modifier.height(28.dp))
                TotalsMonthChart(
                    bars = ui.chartBars,
                    selectedYear = ui.selectedYear,
                    selectedMonth = ui.selectedMonth,
                    allTime = ui.allTime,
                    onSelectMonth = viewModel::selectChartMonth,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Spacer(modifier = Modifier.height(28.dp))
                TotalsStatRow(
                    label = stringResource(R.string.totals_total_spent),
                    amount = MoneyFormat.format(ui.totalSpent, ui.currencyCode),
                    amountColor = SplitEaseColors.Secondary,
                    pillColor = SplitEaseColors.Secondary,
                    onHelp = { showTerms = true },
                )
                Spacer(modifier = Modifier.height(20.dp))
                TotalsStatRow(
                    label = stringResource(R.string.totals_your_share),
                    amount = MoneyFormat.format(ui.yourShare, ui.currencyCode),
                    amountColor = SplitEaseColors.Navy,
                    pillColor = SplitEaseColors.Navy,
                    onHelp = { showTerms = true },
                    caption =
                        ui.sharePercent?.let {
                            stringResource(R.string.totals_share_percent, it)
                        } ?: stringResource(R.string.totals_share_percent_unknown),
                )
            }
        }
    }

    if (showTerms) {
        TotalsTermsDialog(onDismiss = { showTerms = false })
    }
}

@Composable
private fun TotalsMonthChart(
    bars: List<GroupMonthSpending>,
    selectedYear: Int,
    selectedMonth: Int,
    allTime: Boolean,
    onSelectMonth: (year: Int, month: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return
    val maxAmount =
        bars.maxOf { it.totalSpent.toDouble() }.coerceAtLeast(1.0)
    val trackColor = SplitEaseColors.SurfaceMuted
    val mutedFill = SplitEaseColors.NavyMuted.copy(alpha = 0.35f)
    val selectedFill = SplitEaseColors.Primary
    val guideColor = SplitEaseColors.Outline

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val guideCount = 4
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                for (i in 0 until guideCount) {
                    val y = size.height * i / (guideCount - 1).coerceAtLeast(1)
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val selected =
                        !allTime &&
                            bar.year == selectedYear &&
                            bar.month == selectedMonth
                    val fraction = (bar.totalSpent.toDouble() / maxAmount).toFloat().coerceIn(0f, 1f)
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false),
                                    role = Role.Button,
                                    onClick = { onSelectMonth(bar.year, bar.month) },
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .width(36.dp)
                                    .fillMaxHeight(0.92f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // Track
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(trackColor.copy(alpha = 0.55f)),
                            )
                            // Fill
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) selectedFill else mutedFill),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bars.forEach { bar ->
                val selected =
                    !allTime && bar.year == selectedYear && bar.month == selectedMonth
                Text(
                    text = shortMonthLabel(bar.month),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) SplitEaseColors.Navy else SplitEaseColors.NavyMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TotalsStatRow(
    label: String,
    amount: String,
    amountColor: Color,
    pillColor: Color,
    onHelp: () -> Unit,
    caption: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.HelpOutline,
                contentDescription = stringResource(R.string.cd_totals_help),
                tint = SplitEaseColors.NavyMuted,
                modifier =
                    Modifier
                        .size(18.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 12.dp),
                            role = Role.Button,
                            onClick = onHelp,
                        ),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .width(6.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(pillColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
        }
        if (!caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
    }
}

@Composable
private fun TotalsPeriodBar(
    allTime: Boolean,
    year: Int,
    month: Int,
    onAllTime: () -> Unit,
    onMonthMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(SplitEaseColors.SurfaceMuted),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .then(
                        if (allTime) {
                            Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = onAllTime),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.totals_all_time),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SplitEaseColors.Navy,
            )
        }
        Row(
            modifier =
                Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .then(
                        if (!allTime) {
                            Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious, enabled = !allTime) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_totals_prev_month),
                    tint = SplitEaseColors.Navy,
                )
            }
            Row(
                modifier = Modifier.clickable(onClick = onMonthMode),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMonthYear(year, month),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SplitEaseColors.Navy,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SplitEaseColors.NavyMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onNext, enabled = !allTime) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_totals_next_month),
                    tint = SplitEaseColors.Navy,
                )
            }
        }
    }
}

@Composable
private fun TotalsTermsDialog(onDismiss: () -> Unit) {
    SeModal(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = SplitEaseColors.Navy,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.totals_terms_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SplitEaseColors.Navy,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TermsLine(
            term = stringResource(R.string.totals_total_spent),
            definition = stringResource(R.string.totals_terms_total_spent_body),
        )
        Spacer(modifier = Modifier.height(12.dp))
        TermsLine(
            term = stringResource(R.string.totals_your_share),
            definition = stringResource(R.string.totals_terms_your_share_body),
        )
        Spacer(modifier = Modifier.height(24.dp))
        SePrimaryButton(
            text = stringResource(R.string.action_close),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TermsLine(
    term: String,
    definition: String,
) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SplitEaseColors.Navy)) {
                    append(term)
                }
                withStyle(SpanStyle(color = SplitEaseColors.NavyMuted)) {
                    append(" = ")
                    append(definition)
                }
            },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}

private fun shortMonthLabel(month: Int): String {
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
    return symbols.shortMonths.getOrNull(month)?.uppercase(Locale.getDefault()).orEmpty()
}

private fun formatMonthYear(year: Int, month: Int): String {
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
    val name = symbols.months.getOrNull(month).orEmpty()
    return "$name $year"
}
