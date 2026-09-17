package com.splitease.app.presentation.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.settings.AppCurrencies
import com.splitease.app.presentation.common.MoneyFormat
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.seEntityHeaderStyle
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTopBar
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

@Composable
fun DeletedExpenseDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    onRestored: (newExpenseId: String) -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val event by viewModel.observeActivityEvent(eventId).collectAsStateWithLifecycle()
    val comments by viewModel.observeExpenseComments(event?.relatedExpenseId ?: eventId).collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var commentDraft by remember { mutableStateOf("") }
    val bg = MaterialTheme.colorScheme.background
    val lightIconsOnBars = bg.luminance() > 0.5f

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = lightIconsOnBars,
        navigationBarDarkIcons = lightIconsOnBars,
    )

    Scaffold(
        containerColor = bg,
        topBar = {
            SeTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    Button(
                        onClick = {
                            viewModel.restoreExpenseFromActivity(eventId) { newExpenseId ->
                                onRestored(newExpenseId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SplitEaseColors.Primary),
                    ) {
                        Text(stringResource(R.string.action_restore), color = Color.White)
                    }
                },
            )
        },
        bottomBar = {
            if (event != null) {
                ExpenseCommentBar(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    onSend = {
                        val draft = commentDraft
                        val targetId = event?.relatedExpenseId ?: eventId
                        viewModel.addExpenseComment(targetId, draft) {
                            commentDraft = ""
                        }
                    },
                )
            }
        },
    ) { padding ->
        val snapshot = event
        if (snapshot == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SeLayout.detailHorizontal),
            ) {
                SeErrorText(stringResource(R.string.expense_not_found))
            }
            return@Scaffold
        }

        val description = snapshot.snapshotDescription ?: snapshot.title.removePrefix("Deleted: ").trim()
        val amountNum = snapshot.snapshotAmount?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: BigDecimal.ZERO
        val currency = snapshot.snapshotCurrency ?: AppCurrencies.DEFAULT
        val groupName = snapshot.snapshotGroupName ?: snapshot.subtitle.split(" · ").firstOrNull() ?: "Non-group"
        val addedDate = snapshot.snapshotCreatedAtEpochMs?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "—"
        val deletedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snapshot.sortEpochMs))

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SeLayout.detailHorizontal)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                text = description,
                style = seEntityHeaderStyle(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = MoneyFormat.format(amountNum, currency),
                style = MaterialTheme.typography.headlineLarge.copy(
                    textDecoration = TextDecoration.LineThrough,
                ),
                fontWeight = FontWeight.Bold,
                color = SplitEaseColors.YouOwe,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ParticipantChip(
                label = groupName,
                selected = true,
                onClick = {},
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = SplitEaseColors.Outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.activity_added_by, snapshot.snapshotCreatorUserId ?: "Someone", addedDate),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.NavyMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.activity_deleted_by, snapshot.actorUserId, deletedDate),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitEaseColors.YouOwe,
            )
            if (comments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = SplitEaseColors.Outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.expense_comments_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SplitEaseColors.Navy,
                )
                Spacer(modifier = Modifier.height(12.dp))
                comments.forEach { comment ->
                    ExpenseCommentRow(comment = comment)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
