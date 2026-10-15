package com.splitease.app.presentation.account

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.navigation.bottomBarScrollPadding
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.theme.SeBodyMedium
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeListSection
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

private data class FaqItem(
    @StringRes val question: Int,
    @StringRes val answer: Int,
)

private val faqItems =
    listOf(
        FaqItem(R.string.faq_add_expense_q, R.string.faq_add_expense_a),
        FaqItem(R.string.faq_invite_q, R.string.faq_invite_a),
        FaqItem(R.string.faq_balances_q, R.string.faq_balances_a),
        FaqItem(R.string.faq_settle_q, R.string.faq_settle_a),
        FaqItem(R.string.faq_notifications_q, R.string.faq_notifications_a),
        FaqItem(R.string.faq_offline_q, R.string.faq_offline_a),
        FaqItem(R.string.faq_currency_q, R.string.faq_currency_a),
        FaqItem(R.string.faq_delete_account_q, R.string.faq_delete_account_a),
    )

@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    HelpCenterContent(onBack = onBack)
}

@Composable
private fun HelpCenterContent(onBack: () -> Unit) {
    var expandedIndex by rememberSaveable { mutableIntStateOf(-1) }
    SeScreen(
        title = stringResource(R.string.help_center_title),
        onBack = onBack,
        content = { padding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.values.calculateTopPadding(),
                            start = padding.values.calculateStartPadding(layoutDirection),
                            end = padding.values.calculateEndPadding(layoutDirection),
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomBarScrollPadding(includeFab = false)),
            ) {
                SeListSection(
                    title = stringResource(R.string.help_center_intro),
                    horizontalPadding = SeLayout.listInset,
                ) {
                    faqItems.forEachIndexed { index, item ->
                        FaqRow(
                            question = stringResource(item.question),
                            answer = stringResource(item.answer),
                            expanded = expandedIndex == index,
                            onToggle = {
                                expandedIndex = if (expandedIndex == index) -1 else index
                            },
                            showDivider = index != faqItems.lastIndex,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FaqRow(
    question: String,
    answer: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    showDivider: Boolean,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "faqChevron",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        SeListRow(
            title = question,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SplitEaseColors.IconDefault,
                    modifier = Modifier.rotate(rotation),
                )
            },
            onClick = onToggle,
            showDivider = false,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            SeBodyMedium(
                text = answer,
                color = SplitEaseColors.NavyMuted,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = SplitEaseColors.Outline)
        }
    }
}

@Preview(name = "Help center", showBackground = true, heightDp = 780)
@Composable
private fun HelpCenterPreview() {
    SePreview {
        HelpCenterContent(onBack = {})
    }
}
