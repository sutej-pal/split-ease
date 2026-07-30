package com.splitease.app.presentation.pinboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
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

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveNow() }
    }

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
            SeTopBar(
                title = stringResource(R.string.pin_board_title),
                onBack = onBack,
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
                    SeErrorText(it, modifier = Modifier.padding(16.dp))
                }

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

                HorizontalDivider(color = SplitEaseColors.OutlineStrong)

                BasicTextField(
                    value = tfValue,
                    onValueChange = { applyText(it) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    lastEditedAt = state.lastEditedAt,
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
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(Icons.Filled.FormatBold, stringResource(R.string.pin_board_bold), onBold)
        ToolbarButton(Icons.Filled.FormatItalic, stringResource(R.string.pin_board_italic), onItalic)
        ToolbarButton(Icons.Filled.Checklist, stringResource(R.string.pin_board_checklist), onChecklist)
        ToolbarButton(Icons.Filled.Image, stringResource(R.string.pin_board_image), onImage)
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = SplitEaseColors.Primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PinBoardFooter(
    lastEditedBy: String?,
    lastEditedAt: String?,
    isSaving: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 10.dp),
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
