package com.splitease.app.presentation.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.splitease.app.presentation.ui.SeModalBody
import com.splitease.app.presentation.ui.SeModalTitle
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
    val endColor = SplitEaseColors.Surface

    val gradient =
        Brush.verticalGradient(
            colors = listOf(startColor, midColor, endColor),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(gradient)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.welcome_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displayLarge,
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
            Spacer(modifier = Modifier.height(36.dp))
            SePrimaryButton(
                text = stringResource(R.string.action_get_started),
                onClick = onGetStarted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SeOutlinedButton(
                text = stringResource(R.string.action_log_in),
                onClick = onLogIn,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
        ) {
            SeModalTitle(stringResource(R.string.invite_paste_title))
            Spacer(modifier = Modifier.height(8.dp))
            SeModalBody(stringResource(R.string.invite_paste_body))
            Spacer(modifier = Modifier.height(16.dp))
            SeTextField(
                value = pasteValue,
                onValueChange = {
                    pasteValue = it
                    pasteError = false
                },
                label = stringResource(R.string.invite_paste_label),
                singleLine = false,
            )
            if (pasteError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.invite_paste_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SePrimaryButton(
                text = stringResource(R.string.invite_paste_continue),
                onClick = {
                    if (onOpenInviteLink(pasteValue)) {
                        showPasteInvite = false
                        pasteValue = ""
                        pasteError = false
                    } else {
                        pasteError = true
                    }
                },
            )
            SeTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = {
                    showPasteInvite = false
                    pasteError = false
                },
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
