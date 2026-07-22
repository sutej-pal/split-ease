package com.splitease.app.presentation.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun AccountScreen(
    displayName: String,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    SeScreen(
        title = stringResource(R.string.nav_account),
        content = { padding ->
            androidx.compose.foundation.layout.Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(text = displayName, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(20.dp))
                SeSectionHeader(text = stringResource(R.string.settings_title))
                SeListRow(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_hub_subtitle),
                    onClick = onOpenSettings,
                )
                Spacer(modifier = Modifier.height(24.dp))
                SePrimaryButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
            }
        },
    )
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun AccountScreenPreview() {
    SePreview {
        AccountScreen(displayName = "Alex", onOpenSettings = {}, onSignOut = {})
    }
}
