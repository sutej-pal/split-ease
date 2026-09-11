package com.splitease.app.presentation.settlements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeAvatarBadge
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun SettlePayerPickerScreen(
    groupId: String?,
    onBack: () -> Unit,
    onPayerSelected: (String) -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel(),
) {
    val members by viewModel.observeGroupMembers(groupId).collectAsStateWithLifecycle(emptyList())

    SeScreen(
        title = stringResource(R.string.settle_payer_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding.values)
        ) {
            items(members, key = { it.userId }) { member ->
                MemberPickerRow(
                    name = member.displayName,
                    photoUrl = member.photoUrl,
                    onClick = { onPayerSelected(member.userId) }
                )
            }
        }
    }
}

@Composable
fun SettleRecipientPickerScreen(
    groupId: String?,
    payerId: String,
    onBack: () -> Unit,
    onRecipientSelected: (String) -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel(),
) {
    val members by viewModel.observeGroupMembers(groupId).collectAsStateWithLifecycle(emptyList())
    val filtered = members.filter { it.userId != payerId }

    SeScreen(
        title = stringResource(R.string.settle_recipient_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding.values)
        ) {
            items(filtered, key = { it.userId }) { member ->
                MemberPickerRow(
                    name = member.displayName,
                    photoUrl = member.photoUrl,
                    onClick = { onRecipientSelected(member.userId) }
                )
            }
        }
    }
}

@Composable
private fun MemberPickerRow(
    name: String,
    photoUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeAvatarBadge(name = name, photoUrl = photoUrl, size = 48.dp, borderWidth = 0.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = SplitEaseColors.Navy
        )
    }
}
