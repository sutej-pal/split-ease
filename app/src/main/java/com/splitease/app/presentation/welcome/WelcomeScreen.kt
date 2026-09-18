package com.splitease.app.presentation.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeModal
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTextField

/**
 * Launch screen with auth entry points and paste-invite fallback for emulators.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onLogIn: () -> Unit,
    onOpenInviteLink: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var showPasteInvite by rememberSaveable { mutableStateOf(false) }
    var pasteValue by rememberSaveable { mutableStateOf("") }
    var pasteError by rememberSaveable { mutableStateOf(false) }

    val startColor = SplitEaseColors.PrimarySoft
    val midColor = SplitEaseColors.Background

    val gradient =
        Brush.verticalGradient(
            colors = listOf(startColor, midColor, SplitEaseColors.Background),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(gradient),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-36).dp)
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(SplitEaseColors.Primary.copy(alpha = 0.12f)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-64).dp, y = 48.dp)
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(SplitEaseColors.Accent.copy(alpha = 0.16f)),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(SplitEaseColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.welcome_title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp),
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displayMedium,
                color = SplitEaseColors.Primary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitEaseColors.NavyMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.weight(1f))
            SePrimaryButton(
                text = stringResource(R.string.action_get_started),
                onClick = onGetStarted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SeOutlinedButton(
                text = stringResource(R.string.action_log_in),
                onClick = onLogIn,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SeTextButton(
                text = stringResource(R.string.action_have_invite_link),
                onClick = {
                    pasteError = false
                    showPasteInvite = true
                },
            )
        }
    }

    if (showPasteInvite) {
        SeModal(
            onDismissRequest = {
                showPasteInvite = false
                pasteError = false
            },
            title = stringResource(R.string.invite_paste_title),
            icon = Icons.Filled.Link,
            body = stringResource(R.string.invite_paste_body),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.invite_paste_continue),
            onConfirm = {
                if (pasteValue.isBlank() || !onOpenInviteLink(pasteValue)) {
                    pasteError = true
                } else {
                    showPasteInvite = false
                    pasteValue = ""
                    pasteError = false
                }
            },
        ) {
            SeTextField(
                value = pasteValue,
                onValueChange = {
                    pasteValue = it
                    pasteError = false
                },
                label = stringResource(R.string.invite_paste_label),
                singleLine = false,
                isError = pasteError,
                supportingText =
                    if (pasteError) stringResource(R.string.invite_paste_invalid) else null,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    SePreview {
        WelcomeScreen(onGetStarted = {}, onLogIn = {}, onOpenInviteLink = { true })
    }
}
