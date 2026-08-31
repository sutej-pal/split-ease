package com.splitease.app.presentation.pinboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.pinboard.normalizePinImagePath
import com.splitease.app.presentation.media.ImagePickPresets
import com.splitease.app.presentation.media.rememberImagePicker
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeActionChip
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.SeTopBarActionButton
import com.splitease.app.presentation.ui.seScreenSubtitleStyle
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PinBoardScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: PinBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bg = MaterialTheme.colorScheme.background

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = true,
        navigationBarDarkIcons = true,
    )

    var blocks by remember(groupId) { mutableStateOf<List<PinBlock>>(listOf(PinBlock.Text(value = ""))) }
    var textValues by remember(groupId) { mutableStateOf(mapOf<String, TextFieldValue>()) }
    var focusedTextId by remember(groupId) { mutableStateOf<String?>(null) }
    var editingTextId by remember(groupId) { mutableStateOf<String?>(null) }
    /** Snapshot used when the image picker opens (focus blurs before the crop returns). */
    var pendingImageInsert by remember(groupId) {
        mutableStateOf<Pair<String, Int>?>(null)
    }
    var seededForGroup by remember(groupId) { mutableStateOf(false) }

    fun resolveActiveTextBlockId(): String? {
        focusedTextId?.let { id ->
            if (blocks.any { it.id == id && it is PinBlock.Text }) return id
        }
        editingTextId?.let { id ->
            if (blocks.any { it.id == id && it is PinBlock.Text }) return id
        }
        return blocks.filterIsInstance<PinBlock.Text>().lastOrNull()?.id
            ?: blocks.filterIsInstance<PinBlock.Text>().firstOrNull()?.id
    }

    fun textValueFor(blockId: String): TextFieldValue {
        val block = blocks.filterIsInstance<PinBlock.Text>().firstOrNull { it.id == blockId }
        return textValues[blockId]
            ?: TextFieldValue(block?.value.orEmpty(), TextRange(block?.value.orEmpty().length))
    }

    fun commit(newBlocks: List<PinBlock>) {
        blocks = newBlocks
        val nextValues = textValues.toMutableMap()
        newBlocks.filterIsInstance<PinBlock.Text>().forEach { textBlock ->
            val existing = nextValues[textBlock.id]
            if (existing == null || existing.text != textBlock.value) {
                nextValues[textBlock.id] =
                    TextFieldValue(
                        text = textBlock.value,
                        selection = TextRange(textBlock.value.length),
                    )
            }
        }
        val liveIds = newBlocks.map { it.id }.toSet()
        nextValues.keys.retainAll(liveIds)
        textValues = nextValues
        viewModel.onContentChanged(serializePinBlocks(newBlocks))
    }

    LaunchedEffect(groupId, state.isLoading) {
        if (!state.isLoading && !seededForGroup) {
            val parsed = parsePinBlocks(state.content)
            blocks = parsed
            textValues =
                parsed
                    .filterIsInstance<PinBlock.Text>()
                    .associate { it.id to TextFieldValue(it.value, TextRange(it.value.length)) }
            focusedTextId = parsed.filterIsInstance<PinBlock.Text>().firstOrNull()?.id
            val emptyBoard =
                parsed.all { it is PinBlock.Text && it.value.isBlank() } &&
                    parsed.none { it is PinBlock.Image }
            if (emptyBoard) {
                editingTextId = focusedTextId
            }
            seededForGroup = true
        }
    }

    // After save (or cloud image upload), refresh blocks when server content replaces local paths.
    LaunchedEffect(state.content, state.isDirty, state.isSaving) {
        if (!state.isLoading && !state.isDirty && !state.isSaving && seededForGroup) {
            val parsed = parsePinBlocks(state.content)
            if (serializePinBlocks(parsed) != serializePinBlocks(blocks)) {
                blocks = parsed
                textValues =
                    parsed
                        .filterIsInstance<PinBlock.Text>()
                        .associate { block ->
                            val existing = textValues[block.id]
                            block.id to
                                (
                                    existing?.copy(text = block.value)
                                        ?: TextFieldValue(block.value, TextRange(block.value.length))
                                )
                        }
            }
        }
    }

    fun updateTextBlock(
        blockId: String,
        value: TextFieldValue,
    ) {
        textValues = textValues + (blockId to value)
        focusedTextId = blockId
        editingTextId = blockId
        val newBlocks =
            blocks.map { block ->
                if (block is PinBlock.Text && block.id == blockId) {
                    block.copy(value = value.text)
                } else {
                    block
                }
            }
        blocks = newBlocks
        viewModel.onContentChanged(serializePinBlocks(newBlocks))
    }

    fun applyAroundSelection(
        prefix: String,
        suffix: String,
    ) {
        val id = resolveActiveTextBlockId() ?: return
        editingTextId = id
        focusedTextId = id
        val current = textValueFor(id)
        val (newText, cursor) =
            wrapPinBoardSelection(
                text = current.text,
                selectionStart = current.selection.min,
                selectionEnd = current.selection.max,
                prefix = prefix,
                suffix = suffix,
            )
        updateTextBlock(id, TextFieldValue(newText, TextRange(cursor)))
    }

    fun applyChecklist() {
        val id = resolveActiveTextBlockId() ?: return
        editingTextId = id
        focusedTextId = id
        val current = textValueFor(id)
        val (newText, cursor) = insertChecklistMarker(current.text, current.selection.min)
        updateTextBlock(id, TextFieldValue(newText, TextRange(cursor)))
    }

    val imagePicker =
        rememberImagePicker(
            sourceTitle = stringResource(R.string.pin_board_image_source_title),
            sourceBody = stringResource(R.string.pin_board_image_source_body),
            cropTitle = stringResource(R.string.image_crop_title),
            cropBody = stringResource(R.string.image_crop_body),
            cropSpec = ImagePickPresets.Content,
        ) { croppedUri ->
            scope.launch {
                val localPath =
                    runCatching {
                        val dir =
                            File(context.filesDir, "pinboard/$groupId").apply { mkdirs() }
                        val dest = File(dir, "${UUID.randomUUID()}.jpg")
                        AvatarImageIO.copyScaledJpeg(
                            context = context,
                            photoUri = croppedUri,
                            destFile = dest,
                            maxSidePx = 1280,
                        )
                    }.getOrNull() ?: return@launch
                val displayPath = viewModel.ensureImageUploaded(groupId, localPath)
                val insert = pendingImageInsert
                pendingImageInsert = null
                val focusId = insert?.first ?: resolveActiveTextBlockId()
                val focusIndex =
                    blocks.indexOfFirst { it.id == focusId }.takeIf { it >= 0 }
                        ?: blocks.indexOfLast { it is PinBlock.Text }.coerceAtLeast(0)
                val cursor =
                    insert?.second
                        ?: focusId?.let { textValueFor(it).selection.min }
                        ?: 0
                commit(insertImageAt(blocks, focusIndex, cursor, displayPath))
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.pin_board_title),
                onBack = onBack,
                actions = {
                    SeTopBarActionButton(
                        onClick = { viewModel.save() },
                        enabled = state.isDirty && !state.isSaving && !state.isLoading,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SplitEaseColors.Primary,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.cd_save_pin_board),
                                tint =
                                    if (state.isDirty && !state.isSaving && !state.isLoading) {
                                        SplitEaseColors.Primary
                                    } else {
                                        SplitEaseColors.OutlineStrong
                                    },
                            )
                        }
                    }
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

                if (state.groupName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(SeLayout.screenTop))
                    Text(
                        text = state.groupName,
                        style = seScreenSubtitleStyle(),
                        color = SplitEaseColors.NavyMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SeLayout.screenHorizontal),
                    )
                    Spacer(modifier = Modifier.height(SeLayout.headerToContent))
                } else {
                    Spacer(modifier = Modifier.height(SeLayout.headerToContent))
                }

                MarkdownToolbar(
                    onBold = { applyAroundSelection("**", "**") },
                    onItalic = { applyAroundSelection("_", "_") },
                    onChecklist = { applyChecklist() },
                    onImage = {
                        val id = resolveActiveTextBlockId()
                        val cursor = id?.let { textValueFor(it).selection.min } ?: 0
                        if (id != null) {
                            pendingImageInsert = id to cursor
                            focusedTextId = id
                        }
                        imagePicker.launch()
                    },
                )

                Spacer(modifier = Modifier.height(SeLayout.itemGap))
                HorizontalDivider(color = SplitEaseColors.OutlineStrong)

                val boardScroll = rememberScrollState()
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(boardScroll)
                            .padding(horizontal = SeLayout.screenHorizontal, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val showPlaceholder =
                        blocks.all { block ->
                            block is PinBlock.Text && block.value.isBlank()
                        } && blocks.none { it is PinBlock.Image }

                    blocks.forEach { block ->
                        key(block.id) {
                            when (block) {
                                is PinBlock.Text -> {
                                    val autoFocus = editingTextId == block.id
                                    val focusRequester = remember(block.id) { FocusRequester() }
                                    LaunchedEffect(autoFocus) {
                                        if (autoFocus) focusRequester.requestFocus()
                                    }
                                    PinBoardTextBlockEditor(
                                        value = textValueFor(block.id),
                                        onValueChange = { updateTextBlock(block.id, it) },
                                        showPlaceholder = showPlaceholder && block.id == blocks.first().id,
                                        autoFocus = autoFocus,
                                        focusRequester = focusRequester,
                                        onFocused = {
                                            focusedTextId = block.id
                                            editingTextId = block.id
                                        },
                                    )
                                }
                                is PinBlock.Image -> {
                                    PinBoardImageBlock(
                                        path = block.path,
                                        onRemove = { commit(removeImageBlock(blocks, block.id)) },
                                    )
                                }
                            }
                        }
                    }
                }

                PinBoardFooter(
                    lastEditedBy = state.lastEditedBy,
                    isSaving = state.isSaving,
                )
            }
        }
    }
}

private val PinBoardChecklistSize = 20.dp
private val PinBoardChecklistRowHeight = 24.dp

@Composable
private fun PinBoardTextBlockEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showPlaceholder: Boolean,
    autoFocus: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    val lines = parsePinTextLines(value.text)
    val (focusLineIndex, bodyCursor) = lineBodyCursorFromFull(value.text, value.selection.min)
    val lineFocusRequesters = remember(lines.size) { List(lines.size) { FocusRequester() } }
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = SplitEaseColors.Navy,
        )
    val focusedChecklist = lines.getOrNull(focusLineIndex)?.isChecklist == true

    LaunchedEffect(autoFocus, focusedChecklist, focusLineIndex, lines.size) {
        if (autoFocus) {
            runCatching { lineFocusRequesters.getOrNull(focusLineIndex)?.requestFocus() }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEachIndexed { index, line ->
            key(index) {
                val requester = lineFocusRequesters.getOrNull(index)
                val fieldValue =
                    TextFieldValue(
                        text = line.body,
                        selection =
                            if (index == focusLineIndex) {
                                TextRange(bodyCursor.coerceIn(0, line.body.length))
                            } else {
                                TextRange(line.body.length)
                            },
                    )
                    val fieldModifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PinBoardChecklistSize)
                        .then(
                            if (requester != null) {
                                Modifier.focusRequester(requester)
                            } else {
                                Modifier
                            },
                        ).then(
                            if (index == focusLineIndex) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ).onFocusChanged { state ->
                            if (state.isFocused) onFocused()
                        }.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.Backspace) {
                                return@onPreviewKeyEvent false
                            }
                            if (!fieldValue.selection.collapsed || fieldValue.selection.start != 0) {
                                return@onPreviewKeyEvent false
                            }
                            val merged =
                                mergePinLineBackward(value.text, index)
                                    ?: return@onPreviewKeyEvent false
                            onValueChange(TextFieldValue(merged.first, TextRange(merged.second)))
                            true
                        }

                fun commitField(incoming: TextFieldValue) {
                    val (newText, cursor) =
                        applyPinLineFieldChange(
                            text = value.text,
                            lineIndex = index,
                            fieldText = incoming.text,
                            fieldCursor = incoming.selection.end,
                        )
                    val range =
                        if ('\n' in incoming.text || incoming.selection.collapsed) {
                            TextRange(cursor)
                        } else {
                            TextRange(
                                start =
                                    fullCursorFromLineBody(
                                        newText,
                                        index,
                                        incoming.selection.start.coerceIn(0, incoming.text.length),
                                    ),
                                end =
                                    fullCursorFromLineBody(
                                        newText,
                                        index,
                                        incoming.selection.end.coerceIn(0, incoming.text.length),
                                    ),
                            )
                        }
                    onValueChange(TextFieldValue(newText, range))
                }

                if (line.isChecklist) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = PinBoardChecklistRowHeight),
                    ) {
                        PinBoardChecklistBox(
                            checked = line.checked,
                            onCheckedChange = {
                                val (updated, cursor) =
                                    cursorAfterPinLineCheckedToggle(
                                        originalText = value.text,
                                        originalCursor = value.selection.min,
                                        toggledLineIndex = index,
                                    )
                                onValueChange(TextFieldValue(updated, TextRange(cursor)))
                            },
                        )
                        PinBoardLineTextField(
                            value = fieldValue,
                            onValueChange = ::commitField,
                            modifier = fieldModifier.weight(1f, fill = false),
                            textStyle =
                                textStyle.copy(
                                    textDecoration =
                                        if (line.checked) {
                                            TextDecoration.LineThrough
                                        } else {
                                            TextDecoration.None
                                        },
                                ),
                            showPlaceholder = false,
                            centerVertically = true,
                        )
                    }
                } else {
                    PinBoardLineTextField(
                        value = fieldValue,
                        onValueChange = ::commitField,
                        modifier = fieldModifier,
                        textStyle = textStyle,
                        showPlaceholder = showPlaceholder && index == 0 && line.body.isEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinBoardChecklistBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier.size(PinBoardChecklistRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier =
                    Modifier
                        .size(PinBoardChecklistSize)
                        .focusProperties { canFocus = false },
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = SplitEaseColors.Primary,
                        uncheckedColor = SplitEaseColors.NavyMuted,
                        checkmarkColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        }
    }
}

@Composable
private fun PinBoardLineTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    showPlaceholder: Boolean,
    centerVertically: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        cursorBrush = SolidColor(SplitEaseColors.Primary),
        visualTransformation = PinBoardInlineVisualTransformation,
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment =
                    if (centerVertically) {
                        Alignment.CenterStart
                    } else {
                        Alignment.TopStart
                    },
            ) {
                if (showPlaceholder) {
                    Text(
                        text = stringResource(R.string.pin_board_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitEaseColors.NavyMuted,
                    )
                }
                inner()
            }
        },
    )
}

private sealed class PinImageLoadState {
    data object Loading : PinImageLoadState()

    data class Ready(
        val bitmap: ImageBitmap,
    ) : PinImageLoadState()

    data object Failed : PinImageLoadState()
}

@Composable
private fun PinBoardImageBlock(
    path: String,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val decodeTarget = remember(path) { normalizePinImagePath(path) }
    val fileStamp =
        remember(decodeTarget) {
            File(decodeTarget).takeIf { it.isFile }?.lastModified() ?: 0L
        }
    val loadState by produceState<PinImageLoadState>(
        PinImageLoadState.Loading,
        path,
        decodeTarget,
        fileStamp,
    ) {
        value = PinImageLoadState.Loading
        value =
            withContext(Dispatchers.IO) {
                val decoded =
                    AvatarImageIO
                        .decodeScaled(
                            context = context,
                            photoUrl = decodeTarget,
                            maxSidePx = 1280,
                        )?.asImageBitmap()
                if (decoded != null) {
                    PinImageLoadState.Ready(decoded)
                } else {
                    PinImageLoadState.Failed
                }
            }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SplitEaseColors.Outline.copy(alpha = 0.35f)),
    ) {
        when (val imageLoad = loadState) {
            is PinImageLoadState.Ready -> {
                Image(
                    bitmap = imageLoad.bitmap,
                    contentDescription = stringResource(R.string.pin_board_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PinImageLoadState.Loading -> {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    strokeWidth = 2.dp,
                    color = SplitEaseColors.NavyMuted,
                )
            }
            PinImageLoadState.Failed -> {
                Text(
                    text = stringResource(R.string.pin_board_image_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitEaseColors.NavyMuted,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                    ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.pin_board_remove_image),
                tint = SplitEaseColors.Navy,
            )
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
