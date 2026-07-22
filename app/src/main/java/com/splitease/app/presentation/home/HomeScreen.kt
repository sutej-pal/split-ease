package com.splitease.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.splitease.app.R

/**
 * Signed-in hub with shortcuts to Friends and Groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    displayName: String,
    onOpenFriends: () -> Unit,
    onOpenGroups: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Hi, $displayName",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            HubRow(
                title = stringResource(R.string.friends_title),
                subtitle = stringResource(R.string.friends_hub_subtitle),
                onClick = onOpenFriends,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HubRow(
                title = stringResource(R.string.groups_title),
                subtitle = stringResource(R.string.groups_hub_subtitle),
                onClick = onOpenGroups,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
}

@Composable
private fun HubRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
