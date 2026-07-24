package com.splitease.app.presentation.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeListRow
import com.splitease.app.presentation.ui.SeOutlinedButton
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SePrimaryButton
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun AccountScreen(
    displayName: String,
    onOpenSettings: () -> Unit,
    onOpenSpending: () -> Unit,
    onOpenImport: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshPending() }

    SeScreen(
        title = stringResource(R.string.nav_account),
    ) { padding ->
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
                SeListRow(
                    title = stringResource(R.string.spending_title),
                    subtitle = stringResource(R.string.spending_hub_subtitle),
                    onClick = onOpenSpending,
                )
                SeListRow(
                    title = stringResource(R.string.import_title),
                    subtitle = stringResource(R.string.import_hub_subtitle),
                    onClick = onOpenImport,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SeSectionHeader(text = stringResource(R.string.sync_section))
                Text(
                    text =
                        stringResource(R.string.sync_pending_count, sync.pendingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SeOutlinedButton(
                    text =
                        if (sync.isSyncing) {
                            stringResource(R.string.sync_in_progress)
                        } else {
                            stringResource(R.string.action_sync_now)
                        },
                    onClick = viewModel::syncNow,
                    enabled = !sync.isSyncing,
                )
                sync.lastMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    SeInfoText(it)
                }
                Spacer(modifier = Modifier.height(24.dp))
                SePrimaryButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
            }
    }
}

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun AccountScreenPreview() {
    SePreview {
        Text("Preview")
    }
}
