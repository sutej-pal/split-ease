package com.splitease.app.presentation.friends

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTopBarActionButton

/**
 * Confirms selected contacts before inviting. Edit is optional per row.
 */
@Composable
fun ReviewFriendsScreen(
    onBack: () -> Unit,
    onEditEntry: (entry: ReviewFriendEntry) -> Unit,
    onDone: () -> Unit,
    viewModel: ReviewFriendsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)

    LaunchedEffect(uiState.pendingShareTexts) {
        val texts = uiState.pendingShareTexts
        if (texts.isEmpty()) return@LaunchedEffect
        // Share the first invite; remaining invites stay pending in Friends for resend.
        val text = texts.first()
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
        viewModel.consumeShareTexts()
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.review_friends_title),
                onBack = onBack,
                centered = true,
                actions = {
                    SeTopBarActionButton(
                        onClick = { viewModel.addFriends(onAllDone = onDone) },
                        enabled = !uiState.isSubmitting && entries.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.action_add_friends),
                            tint =
                                if (!uiState.isSubmitting && entries.isNotEmpty()) {
                                    SplitEaseColors.Primary
                                } else {
                                    SplitEaseColors.OutlineStrong
                                },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SePrimaryButton(
                    text = stringResource(R.string.action_add_friends),
                    onClick = { viewModel.addFriends(onAllDone = onDone) },
                    enabled = !uiState.isSubmitting && entries.isNotEmpty(),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                ReviewFriendRow(
                    entry = entry,
                    enabled = !uiState.isSubmitting,
                    onRemove = { viewModel.remove(entry.id) },
                    onEdit = { onEditEntry(entry) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.review_friends_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            uiState.errorMessage?.let { msg ->
                item {
                    SeErrorText(
                        msg,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewFriendRow(
    entry: ReviewFriendEntry,
    enabled: Boolean,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            SeIconTile(
                icon = if (entry.isEmail) Icons.Filled.Email else Icons.Filled.Person,
                tint = SplitEaseColors.IconOther,
                size = 48,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.OutlineStrong)
                        .clickable(enabled = enabled, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_review_friend),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName.ifBlank { entry.contactValue },
                style = MaterialTheme.typography.titleMedium,
                color = SplitEaseColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            if (entry.contactValue.isNotBlank()) {
                Text(
                    text = entry.contactValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = SplitEaseColors.NavyMuted,
                )
            } else {
                Text(
                    text = stringResource(R.string.review_friends_missing_contact),
                    style = MaterialTheme.typography.bodySmall,
                    color = SplitEaseColors.YouOwe,
                )
            }
        }
        TextButton(
            onClick = onEdit,
            enabled = enabled,
        ) {
            Text(
                text = stringResource(R.string.action_edit),
                color = SplitEaseColors.Primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
