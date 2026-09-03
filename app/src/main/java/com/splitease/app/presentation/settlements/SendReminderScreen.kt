package com.splitease.app.presentation.settlements

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextButton

/**
 * Compose + send a balance reminder email (editable template).
 */
@Composable
fun SendReminderScreen(
    fromUserId: String,
    toUserId: String,
    fromLabel: String,
    toLabel: String,
    amount: String,
    currencyCode: String,
    groupId: String?,
    groupName: String?,
    onBack: () -> Unit,
    onSent: () -> Unit,
    viewModel: SendReminderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sentToast = stringResource(R.string.msg_reminder_sent)
    var showValidation by rememberSaveable { mutableStateOf(false) }
    val bodyError = showValidation && uiState.body.isBlank()

    LaunchedEffect(fromUserId, toUserId, amount, currencyCode, groupName) {
        viewModel.prepare(
            fromUserId = fromUserId,
            toUserId = toUserId,
            fromLabel = fromLabel,
            toLabel = toLabel,
            amount = amount,
            currencyCode = currencyCode,
            groupName = groupName,
        )
    }

    LaunchedEffect(uiState.sent) {
        if (uiState.sent) {
            Toast.makeText(context, sentToast, Toast.LENGTH_SHORT).show()
            onSent()
        }
    }

    SeScreen(
        title = stringResource(R.string.remind_compose_title),
        onBack = onBack,
        actions = {
            SeTextButton(
                text = stringResource(R.string.action_send),
                onClick = {
                    showValidation = true
                    if (uiState.body.isBlank()) return@SeTextButton
                    viewModel.send()
                },
                enabled =
                    uiState.isReady &&
                        !uiState.isSending &&
                        !uiState.sent,
                isLoading = uiState.isSending,
                emphasized = true,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values),
            ) {
                BasicTextField(
                    value = uiState.body,
                    onValueChange = viewModel::updateBody,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    textStyle =
                        TextStyle(
                            color = SplitEaseColors.Navy,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                        ),
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                    enabled = uiState.isReady && !uiState.isSending,
                )
                if (bodyError) {
                    SeErrorText(
                        text = stringResource(R.string.msg_reminder_body_required),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                uiState.errorMessage?.let { msg ->
                    SeErrorText(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(SplitEaseColors.SurfaceMuted)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.remind_compose_footer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
            }
        },
    )
}
