package com.splitease.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseColors
import kotlinx.coroutines.delay

private val OtpBoxShape = RoundedCornerShape(12.dp)
private val OtpBoxWidth = 48.dp
private val OtpBoxHeight = 56.dp

/**
 * Six-box numeric OTP entry with auto-advance, backspace navigation, and paste support.
 *
 * State is fully hoisted via [value] / [onValueChange]. When [value] reaches [length]
 * digits, [onComplete] is invoked once per completed value.
 */
@Composable
fun SegmentedOtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val digits = remember(value, length) { normalizeOtp(value, length) }
    val focusRequesters = remember(length) { List(length) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    var focusedIndex by remember { mutableStateOf(0) }
    val shakeOffset = remember { Animatable(0f) }
    var lastCompleted by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(digits, length) {
        if (digits.length == length && digits != lastCompleted) {
            lastCompleted = digits
            onComplete(digits)
            focusManager.clearFocus()
        }
        if (digits.length < length) {
            lastCompleted = null
        }
    }

    LaunchedEffect(isError) {
        if (!isError) return@LaunchedEffect
        shakeOffset.snapTo(0f)
        repeat(3) {
            shakeOffset.animateTo(8f, tween(40))
            shakeOffset.animateTo(-8f, tween(40))
        }
        shakeOffset.animateTo(0f, tween(40))
    }

    LaunchedEffect(enabled, digits.length, length) {
        if (!enabled) return@LaunchedEffect
        val target = digits.length.coerceIn(0, length - 1)
        delay(16)
        runCatching { focusRequesters[target].requestFocus() }
    }

    fun applyChange(index: Int, incoming: String) {
        val filtered = incoming.filter { it.isDigit() }
        val current = digits.getOrNull(index)?.toString().orEmpty()
        // Occupied box + one extra digit (IME append) → treat as replace, not paste.
        val effective =
            if (
                current.isNotEmpty() &&
                filtered.length == current.length + 1 &&
                filtered.startsWith(current)
            ) {
                filtered.last().toString()
            } else {
                filtered
            }
        when {
            effective.isEmpty() -> {
                if (index < digits.length) {
                    onValueChange(digits.removeRange(index, index + 1))
                }
            }
            effective.length > 1 -> {
                val merged = normalizeOtp(digits.take(index) + effective, length)
                onValueChange(merged)
                val next = merged.length.coerceAtMost(length - 1)
                focusRequesters[next].requestFocus()
            }
            else -> {
                val digit = effective.first()
                val nextValue =
                    if (index >= digits.length) {
                        normalizeOtp(digits + digit, length)
                    } else {
                        normalizeOtp(
                            digits.substring(0, index) + digit + digits.substring(index + 1),
                            length,
                        )
                    }
                onValueChange(nextValue)
                if (nextValue.length < length && index < length - 1) {
                    focusRequesters[(index + 1).coerceAtMost(length - 1)].requestFocus()
                }
            }
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(x = shakeOffset.value.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, horizontalAlignment),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until length) {
            val char = digits.getOrNull(index)?.toString().orEmpty()
            OtpDigitBox(
                value = char,
                enabled = enabled,
                isFocused = focusedIndex == index,
                isFilled = char.isNotEmpty(),
                isError = isError,
                focusRequester = focusRequesters[index],
                contentDescription =
                    stringResource(R.string.cd_otp_digit, index + 1, length),
                onFocusChanged = { focused ->
                    if (focused) focusedIndex = index
                },
                onBackspaceWhenEmpty = {
                    if (index > 0) {
                        onValueChange(digits.dropLast(1))
                        focusRequesters[index - 1].requestFocus()
                    }
                },
                onValueChange = { incoming -> applyChange(index, incoming) },
            )
        }
    }
}

@Composable
private fun OtpDigitBox(
    value: String,
    enabled: Boolean,
    isFocused: Boolean,
    isFilled: Boolean,
    isError: Boolean,
    focusRequester: FocusRequester,
    contentDescription: String,
    onFocusChanged: (Boolean) -> Unit,
    onBackspaceWhenEmpty: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(0, value.length))
        }
    }

    val borderColor =
        when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> SplitEaseColors.Primary
            isFilled -> SplitEaseColors.OutlineStrong
            else -> SplitEaseColors.OutlineStrong
        }
    val borderWidth = if (isFocused || isError) 2.dp else 1.dp

    Box(
        modifier =
            Modifier
                .width(OtpBoxWidth)
                .height(OtpBoxHeight)
                .clip(OtpBoxShape)
                .border(borderWidth, borderColor, OtpBoxShape)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                val incomingDigits = incoming.text.filter { it.isDigit() }
                val nextText = incomingDigits.take(1)
                fieldValue = TextFieldValue(nextText, TextRange(0, nextText.length))
                onValueChange(incomingDigits)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        onFocusChanged(state.isFocused)
                        if (state.isFocused && fieldValue.text.isNotEmpty()) {
                            fieldValue =
                                fieldValue.copy(
                                    selection = TextRange(0, fieldValue.text.length),
                                )
                        }
                    }.onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            fieldValue.text.isEmpty()
                        ) {
                            onBackspaceWhenEmpty()
                            true
                        } else {
                            false
                        }
                    },
            enabled = enabled,
            textStyle =
                TextStyle(
                    color = SplitEaseColors.Navy,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                ),
            singleLine = true,
            cursorBrush = SolidColor(SplitEaseColors.Primary),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    inner()
                }
            },
        )
    }
}

private fun normalizeOtp(raw: String, length: Int): String =
    raw.filter { it.isDigit() }.take(length)
