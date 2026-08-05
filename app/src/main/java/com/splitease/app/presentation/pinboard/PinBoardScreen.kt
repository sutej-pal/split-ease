package com.splitease.app.presentation.pinboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeScreenSubtitleStyle
import com.splitease.app.presentation.ui.SeScreenTitleText
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTopBar
import java.io.File
import java.util.UUID

@Composable
fun PinBoardScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: PinBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = true,
        navigationBarDarkIcons = true,
    )

    // Keep selection local — do not key on state.content (that reset the cursor to 0).
    var tfValue by remember(groupId) { mutableStateOf(TextFieldValue("")) }
    var seededForGroup by remember(groupId) { mutableStateOf(false) }

    LaunchedEffect(groupId, state.isLoading) {
        if (!state.isLoading && !seededForGroup) {
            val text = state.content
            tfValue = TextFieldValue(text, TextRange(text.length))
            seededForGroup = true
        }
    }

    fun applyText(newValue: TextFieldValue) {
        tfValue = newValue
        viewModel.onContentChanged(newValue.text)
    }

    fun insertAroundSelection(
        prefix: String,
        suffix: String,
    ) {
        val sel = tfValue.selection
        val text = tfValue.text
        val selected = text.substring(sel.min, sel.max)
        val replacement = "$prefix$selected$suffix"
        val newText = text.replaceRange(sel.min, sel.max, replacement)
        val cursor = sel.min + prefix.length + selected.length
        applyText(TextFieldValue(newText, TextRange(cursor)))
    }

    fun insertAtCursor(snippet: String) {
        val sel = tfValue.selection
        val newText = tfValue.text.replaceRange(sel.min, sel.max, snippet)
        val cursor = sel.min + snippet.length
        applyText(TextFieldValue(newText, TextRange(cursor)))
    }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val path =
                runCatching {
                    val dir =
                        File(context.filesDir, "pinboard/$groupId").apply { mkdirs() }
                    val dest = File(dir, "${UUID.randomUUID()}.jpg")
                    AvatarImageIO.copyScaledJpeg(
                        context = context,
                        photoUri = uri.toString(),
                        destFile = dest,
                        maxSidePx = 1280,
                    )
                }.getOrNull() ?: return@rememberLauncherForActivityResult
            insertAtCursor("\n![]($path)\n")
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Back + Save only — title stacks in the body (same pattern as auth screens).
            SeTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    SeTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.save() },
                        enabled = state.isDirty && !state.isSaving && !state.isLoading,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        ) {
            if (state.isLoading) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = SplitEaseColors.Primary,
                )
            } else {
                state.errorMessage?.let {
                    SeErrorText(it, modifier = Modifier.padding(horizontal = SeLayout.screenHorizontal))
                }

                Spacer(modifier = Modifier.height(SeLayout.screenTop))
                SeScreenTitleText(
                    text = stringResource(R.string.pin_board_title),
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SeLayout.screenHorizontal),
                )
                if (state.groupName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(SeLayout.titleToSubtitle))
                    Text(
                        text = state.groupName,
                        style = SeScreenSubtitleStyle(),
                        color = SplitEaseColors.NavyMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SeLayout.screenHorizontal),
                    )
                }
                Spacer(modifier = Modifier.height(SeLayout.headerToContent))

                MarkdownToolbar(
                    onBold = { insertAroundSelection("**", "**") },
                    onItalic = { insertAroundSelection("_", "_") },
                    onChecklist = { insertAroundSelection("- [ ] ", "") },
                    onImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(SeLayout.itemGap))
                HorizontalDivider(color = SplitEaseColors.OutlineStrong)

                BasicTextField(
                    value = tfValue,
                    onValueChange = { applyText(it) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = SeLayout.screenHorizontal, vertical = 12.dp),
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = SplitEaseColors.Navy,
                        ),
                    cursorBrush = SolidColor(SplitEaseColors.Primary),
                    decorationBox = { inner ->
                        if (tfValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pin_board_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = SplitEaseColors.NavyMuted,
                            )
                        }
                        inner()
                    },
                )

                PinBoardFooter(
                    lastEditedBy = state.lastEditedBy,
                    isSaving = state.isSaving,
                )
            }
        }
    }
}

@Composable
private fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onChecklist: () -> Unit,
    onImage: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SeLayout.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeActionChip(
            label = stringResource(R.string.pin_board_bold),
            onClick = onBold,
            icon = Icons.Filled.FormatBold,
        )
        SeActionChip(
            label = stringResource(R.string.pin_board_italic),
            onClick = onItalic,
            icon = Icons.Filled.FormatItalic,
        )
        SeActionChip(
            label = stringResource(R.string.pin_board_checklist),
            onClick = onChecklist,
            icon = Icons.Filled.Checklist,
        )
        SeActionChip(
            label = stringResource(R.string.pin_board_image),
            onClick = onImage,
            icon = Icons.Filled.Image,
        )
    }
}

@Composable
private fun PinBoardFooter(
    lastEditedBy: String?,
    isSaving: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = SeLayout.screenHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = SplitEaseColors.NavyMuted,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.pin_board_saving),
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
            )
        } else if (lastEditedBy != null) {
            Text(
                text = stringResource(R.string.pin_board_last_edited, lastEditedBy),
                style = MaterialTheme.typography.labelSmall,
                color = SplitEaseColors.NavyMuted,
            )
        }
    }
}
