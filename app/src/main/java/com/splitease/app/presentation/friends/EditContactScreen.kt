package com.splitease.app.presentation.friends

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.social.InviteLinks
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeInfoText
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeTextField
import com.splitease.app.presentation.ui.SeTopBarActionButton

@Composable
fun EditContactScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onConfirmedForReview: () -> Unit = onDone,
    viewModel: EditContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.invite_email_subject)
    val shareInvite = stringResource(R.string.action_share_invite)
    val canSubmit =
        !uiState.isSubmitting &&
            !uiState.isLoading &&
            uiState.name.isNotBlank() &&
            selectedContactReady(uiState)
    var showValidation by rememberSaveable { mutableStateOf(false) }
    val nameError = showValidation && uiState.name.isBlank()
    val contactError = showValidation && !selectedContactReady(uiState)

    LaunchedEffect(uiState.pendingShareText) {
        val text = uiState.pendingShareText ?: return@LaunchedEffect
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
        viewModel.consumeShareText()
        onDone()
    }

    SeScreen(
        title = stringResource(R.string.edit_contact_title),
        onBack = onBack,
        centeredTitle = true,
        actions = {
            SeTopBarActionButton(
                onClick = {
                    showValidation = true
                    if (!canSubmit) return@SeTopBarActionButton
                    viewModel.submit(
                        onLinked = onDone,
                        onConfirmedForReview = onConfirmedForReview,
                    )
                },
                enabled = !uiState.isSubmitting && !uiState.isLoading,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_done),
                    tint = SplitEaseColors.Primary,
                )
            }
        },
        content = { padding ->
            if (uiState.isLoading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding.values),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(color = SplitEaseColors.Primary)
                }
                return@SeScreen
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SeTextField(
                    value = uiState.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.label_name),
                    enabled = !uiState.isSubmitting,
                    isError = nameError,
                    supportingText =
                        if (nameError) stringResource(R.string.msg_name_required) else null,
                    trailingIcon =
                        if (uiState.name.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setName("") }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.cd_clear_name),
                                        tint = SplitEaseColors.NavyMuted,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.label_phone_or_email),
                    style = MaterialTheme.typography.titleSmall,
                    color = SplitEaseColors.NavyMuted,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                uiState.options.forEach { option ->
                    ContactMethodRow(
                        option = option,
                        selected = option.id == uiState.selectedOptionId,
                        newPhone = uiState.newPhone,
                        newEmail = uiState.newEmail,
                        enabled = !uiState.isSubmitting,
                        showContactError = contactError,
                        onSelect = { viewModel.selectOption(option.id) },
                        onNewPhoneChange = viewModel::setNewPhone,
                        onNewEmailChange = viewModel::setNewEmail,
                    )
                }
                if (contactError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SeErrorText(stringResource(R.string.msg_contact_required))
                }

                if (uiState.confirmOnly) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.add_friend_review_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitEaseColors.NavyMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeErrorText(it)
                }
                uiState.infoMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    SeInfoText(it)
                }
            }
        },
    )
}

@Composable
private fun ContactMethodRow(
    option: ContactMethodOption,
    selected: Boolean,
    newPhone: String,
    newEmail: String,
    enabled: Boolean,
    showContactError: Boolean,
    onSelect: () -> Unit,
    onNewPhoneChange: (String) -> Unit,
    onNewEmailChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onSelect)
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = SplitEaseColors.Primary,
                        unselectedColor = SplitEaseColors.OutlineStrong,
                    ),
            )
            Spacer(modifier = Modifier.width(4.dp))
            when (option.kind) {
                ContactMethodKind.EXISTING_PHONE -> {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = null,
                        tint = SplitEaseColors.NavyMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = option.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                }
                ContactMethodKind.EXISTING_EMAIL -> {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = SplitEaseColors.NavyMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = option.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.Navy,
                    )
                }
                ContactMethodKind.NEW_PHONE -> {
                    if (selected) {
                        SeTextField(
                            value = newPhone,
                            onValueChange = onNewPhoneChange,
                            label = stringResource(R.string.label_phone_number),
                            enabled = enabled,
                            isError = showContactError && newPhone.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.edit_contact_enter_phone),
                            style = MaterialTheme.typography.bodyLarge,
                            color = SplitEaseColors.Navy,
                        )
                    }
                }
                ContactMethodKind.NEW_EMAIL -> {
                    if (selected) {
                        SeTextField(
                            value = newEmail,
                            onValueChange = onNewEmailChange,
                            label = stringResource(R.string.label_email),
                            enabled = enabled,
                            isError = showContactError && newEmail.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.edit_contact_enter_email),
                            style = MaterialTheme.typography.bodyLarge,
                            color = SplitEaseColors.Navy,
                        )
                    }
                }
            }
        }
    }
}

private fun selectedContactReady(state: EditContactUiState): Boolean {
    val selected = state.options.firstOrNull { it.id == state.selectedOptionId } ?: return false
    return when (selected.kind) {
        ContactMethodKind.EXISTING_PHONE, ContactMethodKind.EXISTING_EMAIL ->
            selected.value.isNotBlank()
        ContactMethodKind.NEW_PHONE -> state.newPhone.isNotBlank()
        ContactMethodKind.NEW_EMAIL -> state.newEmail.isNotBlank()
    }
}
