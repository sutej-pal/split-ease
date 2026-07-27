package com.splitease.app.presentation.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InvitePreview
import com.splitease.app.domain.model.InvitePreviewMember
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeIconTile
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeTextButton

/**
 * Deep-link landing: who invited you, group members, join as someone new.
 */
@Composable
fun InviteLandingScreen(
    token: String,
    onJoinAsNew: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteJoinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(token) {
        viewModel.onInviteToken(token)
    }

    InviteLandingContent(
        uiState = uiState,
        onJoinAsNew = onJoinAsNew,
        onAlreadyHaveAccount = onAlreadyHaveAccount,
        onDismiss = {
            viewModel.dismissInvite()
            onDismiss()
        },
        modifier = modifier,
    )
}

@Composable
private fun InviteLandingContent(
    uiState: InviteJoinUiState,
    onJoinAsNew: () -> Unit,
    onAlreadyHaveAccount: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(SplitEaseColors.Background)
                .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = SplitEaseColors.Primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SplitEaseColors.Surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(SplitEaseColors.Accent)
                                .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SeIconTile(
                            icon = Icons.Filled.Home,
                            tint = SplitEaseColors.Primary,
                            size = 56,
                        )
                    }

                    Column(modifier = Modifier.padding(20.dp)) {
                        when {
                            uiState.isLoading && uiState.preview == null -> {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            uiState.preview != null -> {
                                val preview = uiState.preview
                                InviteMessage(preview = preview)
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.invite_select_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SplitEaseColors.Navy,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (preview.members.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.invite_members_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SplitEaseColors.NavyMuted,
                                    )
                                } else {
                                    preview.members.forEachIndexed { index, member ->
                                        InviteMemberRow(
                                            member = member,
                                            showDivider = index < preview.members.lastIndex,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                SeOutlinedButton(
                                    text = stringResource(R.string.invite_join_as_new),
                                    onClick = onJoinAsNew,
                                    enabled = !uiState.isLoading,
                                )
                            }

                            else -> {
                                Text(
                                    text =
                                        uiState.errorMessage
                                            ?: stringResource(R.string.invite_not_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SeOutlinedButton(
                                    text = stringResource(R.string.action_back_to_welcome),
                                    onClick = onDismiss,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.preview != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SeTextButton(
                    text = stringResource(R.string.invite_already_have_account),
                    onClick = onAlreadyHaveAccount,
                )
                SeTextButton(
                    text = stringResource(R.string.action_back_to_welcome),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun InviteMessage(preview: InvitePreview) {
    val groupLabel = preview.groupName?.takeIf { it.isNotBlank() }
    val annotated =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(preview.inviterName)
            }
            append(" ")
            append(stringResource(R.string.invite_has_invited_you))
            if (groupLabel != null) {
                append(" ")
                append(stringResource(R.string.invite_to_group_prefix))
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("\"$groupLabel\"")
                }
            }
            append(" ")
            append(stringResource(R.string.invite_in_app_suffix))
        }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = SplitEaseColors.Navy,
    )
}

@Composable
private fun InviteMemberRow(
    member: InvitePreviewMember,
    showDivider: Boolean,
) {
    val initial =
        member.displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
    SeListRow(
        title = member.displayName,
        subtitle =
            if (member.alreadyJoined) {
                stringResource(R.string.invite_already_joined)
            } else {
                stringResource(R.string.invite_pending_member)
            },
        leading = {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = SplitEaseColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        trailing =
            if (member.alreadyJoined) {
                {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SplitEaseColors.Positive,
                    )
                }
            } else {
                null
            },
        showDivider = showDivider,
    )
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun InviteLandingPreview() {
    SePreview {
        InviteLandingContent(
            uiState =
                InviteJoinUiState(
                    token = "abc",
                    preview =
                        InvitePreview(
                            token = "abc",
                            kind = InviteKind.GROUP,
                            email = "guest@example.com",
                            inviterName = "Alex",
                            groupName = "Roommates",
                            members =
                                listOf(
                                    InvitePreviewMember("Alex", alreadyJoined = true),
                                    InvitePreviewMember("Sam", alreadyJoined = true),
                                ),
                        ),
                ),
            onJoinAsNew = {},
            onAlreadyHaveAccount = {},
            onDismiss = {},
        )
    }
}
