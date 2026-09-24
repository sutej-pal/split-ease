package com.splitease.app.presentation.pinboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeErrorText
import com.splitease.app.presentation.ui.SeLayout
import com.splitease.app.presentation.ui.SeSystemBars
import com.splitease.app.presentation.ui.SeTextButton
import com.splitease.app.presentation.ui.SeTopBar
import com.splitease.app.presentation.ui.seScreenSubtitleStyle

@Composable
fun PinBoardScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: PinBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val bg = MaterialTheme.colorScheme.background
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshFromRemote()
                Lifecycle.Event.ON_PAUSE -> viewModel.saveImmediately()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SeSystemBars(
        statusBarColor = bg,
        navigationBarColor = bg,
        statusBarDarkIcons = true,
        navigationBarDarkIcons = true,
    )

    var textValue by remember(groupId) { mutableStateOf(TextFieldValue("")) }
    var seededForGroup by remember(groupId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(groupId, state.isLoading, state.contentRevision) {
        if (state.isLoading) return@LaunchedEffect
        if (!seededForGroup) {
            textValue = TextFieldValue(state.content, TextRange(state.content.length))
            seededForGroup = true
            if (state.content.isBlank()) {
                runCatching { focusRequester.requestFocus() }
                keyboardController?.show()
            }
            return@LaunchedEffect
        }
        if (state.saveState == SaveState.PENDING || state.saveState == SaveState.SAVING) {
            return@LaunchedEffect
        }
        if (textValue.text != state.content) {
            textValue = TextFieldValue(state.content, TextRange(state.content.length))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = stringResource(R.string.pin_board_title),
                onBack = onBack,
                actions = {
                    val saveState = state.saveState
                    SeTextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.saveImmediately() },
                        enabled = saveState == SaveState.PENDING || saveState == SaveState.ERROR,
                        isLoading = saveState == SaveState.SAVING,
                        emphasized = true,
                        color =
                            if (saveState == SaveState.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                null
                            },
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
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { focusManager.clearFocus() },
                ) {
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

                    HorizontalDivider(color = SplitEaseColors.OutlineStrong)
                }

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = SeLayout.screenHorizontal, vertical = 24.dp),
                ) {
                    val editorMinHeight = maxHeight
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = editorMinHeight)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        if (textValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pin_board_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = SplitEaseColors.NavyMuted,
                            )
                        }

                        BasicTextField(
                            value = textValue,
                            onValueChange = {
                                textValue = it
                                viewModel.onContentChanged(it.text)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = editorMinHeight)
                                    .focusRequester(focusRequester),
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = SplitEaseColors.Navy,
                                ),
                            cursorBrush = SolidColor(SplitEaseColors.Primary),
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default,
                                ),
                        )
                    }
                }

                PinBoardFooter(
                    lastEditedBy = state.lastEditedBy,
                    saveState = state.saveState,
                )
            }
        }
    }
}

@Composable
private fun PinBoardFooter(
    lastEditedBy: String?,
    saveState: SaveState,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = SeLayout.screenHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saveState == SaveState.SAVING) {
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
