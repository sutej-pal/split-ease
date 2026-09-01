package com.splitease.app.presentation.pinboard

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/** Unchecked (`- []` / `- [ ]`) or checked (`- [x]`) markdown checklist line. */
private val CHECKLIST_LINE =
    Regex("""^(\s*)-\s*\[(\s*|x|X)\]\s*(.*)$""")

internal data class PinTextLine(
    val isChecklist: Boolean,
    val checked: Boolean,
    val body: String,
)

/** Whether [line] is a markdown checklist item. */
internal fun isChecklistLine(line: String): Boolean = CHECKLIST_LINE.matches(line)

/** Toggles a checklist line between unchecked and checked. */
internal fun toggleChecklistLine(line: String): String {
    val parsed = parsePinTextLine(line)
    if (!parsed.isChecklist) return line
    return serializePinTextLine(parsed.copy(checked = !parsed.checked))
}

/** Converts a checklist line to plain text, or a plain line to a checklist item. */
internal fun toggleChecklistBlock(line: String): String {
    val parsed = parsePinTextLine(line)
    return serializePinTextLine(parsed.copy(isChecklist = !parsed.isChecklist))
}

/** Checklist item label without the `- [ ]` prefix. */
internal fun checklistItemBody(line: String): String = parsePinTextLine(line).body

internal fun isChecklistLineChecked(line: String): Boolean = parsePinTextLine(line).checked

internal fun parsePinTextLine(line: String): PinTextLine {
    val match = CHECKLIST_LINE.matchEntire(line) ?: return PinTextLine(false, false, line)
    return PinTextLine(
        isChecklist = true,
        checked = match.groupValues[2].equals("x", ignoreCase = true),
        body = match.groupValues[3],
    )
}

internal fun parsePinTextLines(text: String): List<PinTextLine> {
    if (text.isEmpty()) return listOf(PinTextLine(false, false, ""))
    return text.split('\n').map(::parsePinTextLine)
}

internal fun serializePinTextLine(line: PinTextLine): String =
    when {
        !line.isChecklist -> line.body
        line.checked -> "- [x] ${line.body}"
        else -> "- [ ] ${line.body}"
    }

internal fun serializePinTextLines(lines: List<PinTextLine>): String =
    lines.joinToString("\n", transform = ::serializePinTextLine)

/** Line index and cursor offset inside that line's editable body. */
internal fun lineBodyCursorFromFull(
    text: String,
    cursor: Int,
): Pair<Int, Int> {
    val parts = if (text.isEmpty()) listOf("") else text.split('\n')
    val safe = cursor.coerceIn(0, text.length)
    var start = 0
    parts.forEachIndexed { index, raw ->
        val end = start + raw.length
        if (safe <= end) {
            val parsed = parsePinTextLine(raw)
            val prefixLen = (raw.length - parsed.body.length).coerceAtLeast(0)
            val bodyOffset = (safe - start - prefixLen).coerceIn(0, parsed.body.length)
            return index to bodyOffset
        }
        start = end + 1
    }
    val last = parsePinTextLine(parts.last())
    return parts.lastIndex to last.body.length
}

internal fun fullCursorFromLineBody(
    text: String,
    lineIndex: Int,
    bodyOffset: Int,
): Int {
    val parts = if (text.isEmpty()) listOf("") else text.split('\n')
    var start = 0
    parts.forEachIndexed { index, raw ->
        if (index == lineIndex) {
            val parsed = parsePinTextLine(raw)
            val prefixLen = (raw.length - parsed.body.length).coerceAtLeast(0)
            return start + prefixLen + bodyOffset.coerceIn(0, parsed.body.length)
        }
        start += raw.length + 1
    }
    return text.length
}

/**
 * Applies a per-line text-field change (typing, selection, or Enter) back onto
 * the full markdown source.
 *
 * @return Updated markdown and cursor in that full string.
 */
internal fun applyPinLineFieldChange(
    text: String,
    lineIndex: Int,
    fieldText: String,
    fieldCursor: Int,
): Pair<String, Int> {
    val lines = parsePinTextLines(text).toMutableList()
    if (lineIndex !in lines.indices) return text to text.length
    val current = lines[lineIndex]
    val sel = fieldCursor.coerceIn(0, fieldText.length)

    if ('\n' !in fieldText) {
        lines[lineIndex] = current.copy(body = fieldText)
        val newText = serializePinTextLines(lines)
        val cursor = fullCursorFromLineBody(newText, lineIndex, sel)
        return newText to cursor
    }

    val parts = fieldText.split('\n')
    val before = parts.first()
    val after = parts.drop(1)

    // Empty checklist item + Enter leaves the list (plain paragraph).
    if (current.isChecklist && before.isBlank() && after.all { it.isBlank() }) {
        lines[lineIndex] = PinTextLine(isChecklist = false, checked = false, body = "")
        val newText = serializePinTextLines(lines)
        return newText to fullCursorFromLineBody(newText, lineIndex, 0)
    }

    // Smart split: balance markers so styled blocks don't break across lines.
    val unclosed = findUnclosedMarkers(before)
    val closingSuffix = unclosed.reversed().joinToString("")
    val openingPrefix = unclosed.joinToString("")

    lines[lineIndex] = current.copy(body = before + closingSuffix)
    after.forEachIndexed { offset, part ->
        val lineBody = if (offset == 0) openingPrefix + part else part
        lines.add(
            lineIndex + 1 + offset,
            PinTextLine(
                isChecklist = current.isChecklist,
                checked = false,
                body = lineBody,
            ),
        )
    }
    val newText = serializePinTextLines(lines)
    val cursor =
        if (sel <= before.length) {
            fullCursorFromLineBody(newText, lineIndex, sel)
        } else if (after.size == 1) {
            fullCursorFromLineBody(
                newText,
                lineIndex + 1,
                (openingPrefix.length + sel - before.length - 1).coerceIn(0, (openingPrefix + after[0]).length),
            )
        } else {
            fullCursorFromLineBody(newText, lineIndex + after.size, after.last().length)
        }
    return newText to cursor
}

/** Returns the stack of opening markers that haven't been closed in [text]. */
private fun findUnclosedMarkers(text: String): List<String> {
    val stack = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = inlineMarkerEnd(text, i, "**")
            if (end != null) {
                // Skip the whole bold block if it's already closed on this side.
                // We'll process its inside recursively if needed, but for unclosed
                // check at the tail, a closed block is neutral.
                i = end + 2
                continue
            } else {
                stack.add("**")
                i += 2
            }
        } else if (text.startsWith("_", i)) {
            val end = inlineMarkerEnd(text, i, "_")
            if (end != null) {
                i = end + 1
                continue
            } else {
                stack.add("_")
                i += 1
            }
        } else {
            i++
        }
    }
    return stack
}

internal fun togglePinLineChecked(
    text: String,
    lineIndex: Int,
): String {
    val lines = parsePinTextLines(text).toMutableList()
    if (lineIndex !in lines.indices) return text
    val line = lines[lineIndex]
    if (!line.isChecklist) return text
    lines[lineIndex] = line.copy(checked = !line.checked)
    return serializePinTextLines(lines)
}

/**
 * Toggles the checklist at [toggledLineIndex] and remaps [originalCursor] onto the
 * same focused line so checking another row does not steal the caret.
 */
internal fun cursorAfterPinLineCheckedToggle(
    originalText: String,
    originalCursor: Int,
    toggledLineIndex: Int,
): Pair<String, Int> {
    val updated = togglePinLineChecked(originalText, toggledLineIndex)
    val (focusLine, focusBody) = lineBodyCursorFromFull(originalText, originalCursor)
    return updated to fullCursorFromLineBody(updated, focusLine, focusBody)
}

/**
 * Backspace at the start of a line: unwrap a checklist, otherwise merge into
 * the previous line.
 */
internal fun mergePinLineBackward(
    text: String,
    lineIndex: Int,
): Pair<String, Int>? {
    val lines = parsePinTextLines(text).toMutableList()
    if (lineIndex !in lines.indices) return null
    val current = lines[lineIndex]
    if (current.isChecklist) {
        lines[lineIndex] = current.copy(isChecklist = false, checked = false)
        val newText = serializePinTextLines(lines)
        return newText to fullCursorFromLineBody(newText, lineIndex, 0)
    }
    if (lineIndex == 0) return null
    val prev = lines[lineIndex - 1]
    val mergeAt = prev.body.length
    lines[lineIndex - 1] = prev.copy(body = prev.body + current.body)
    lines.removeAt(lineIndex)
    val newText = serializePinTextLines(lines)
    return newText to fullCursorFromLineBody(newText, lineIndex - 1, mergeAt)
}

/**
 * Applies [prefix]/[suffix] around the selection (or at the cursor when empty).
 *
 * @return Updated text and cursor position.
 */
internal fun wrapPinBoardSelection(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String,
    suffix: String,
): Pair<String, Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val selected = text.substring(start, end)
    val replacement = "$prefix$selected$suffix"
    val newText = text.replaceRange(start, end, replacement)
    val cursor = start + prefix.length + selected.length
    return newText to cursor
}

/**
 * Inserts a checklist marker at the current line (or starts a new checklist line).
 *
 * @return Updated text and cursor position.
 */
internal fun insertChecklistMarker(
    text: String,
    cursor: Int,
): Pair<String, Int> {
    val safeCursor = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', safeCursor - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', safeCursor).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    if (isChecklistLine(line)) {
        return text to safeCursor
    }
    val linePrefix = text.substring(lineStart, safeCursor)
    val insert =
        when {
            linePrefix.isBlank() -> "- [ ] "
            else -> "\n- [ ] "
        }
    val insertAt =
        when {
            linePrefix.isBlank() -> lineStart
            else -> safeCursor
        }
    val newText = text.replaceRange(insertAt, insertAt, insert)
    return newText to (insertAt + insert.length)
}

/** Renders inline markdown (**bold**, _italic_) for pin-board display. */
internal fun buildPinBoardAnnotatedString(text: String): AnnotatedString = transformPinBoardInline(text).text

/** Hides `**` / `_` markers in the editor while keeping styles. */
internal object PinBoardInlineVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = transformPinBoardInline(text.text)
}

internal fun transformPinBoardInline(source: String): TransformedText {
    if (source.isEmpty()) return TransformedText(AnnotatedString(""), OffsetMapping.Identity)

    val origToTrans = IntArray(source.length + 1)
    val transToOrig = ArrayList<Int>(source.length + 1)
    var trans = 0
    val annotated = AnnotatedString.Builder()

    fun processRange(
        start: Int,
        end: Int,
        currentStyle: SpanStyle,
    ) {
        var i = start
        while (i < end) {
            val boldEnd = inlineMarkerEnd(source, i, "**")
            if (boldEnd != null && boldEnd < end) {
                origToTrans[i] = trans
                origToTrans[i + 1] = trans
                processRange(i + 2, boldEnd, currentStyle.merge(SpanStyle(fontWeight = FontWeight.Bold)))
                origToTrans[boldEnd] = trans
                origToTrans[boldEnd + 1] = trans
                i = boldEnd + 2
                continue
            }
            val italicEnd = inlineMarkerEnd(source, i, "_")
            if (italicEnd != null && italicEnd < end) {
                origToTrans[i] = trans
                processRange(i + 1, italicEnd, currentStyle.merge(SpanStyle(fontStyle = FontStyle.Italic)))
                origToTrans[italicEnd] = trans
                i = italicEnd + 1
                continue
            }

            origToTrans[i] = trans
            transToOrig.add(i)
            annotated.withStyle(currentStyle) {
                append(source[i])
            }
            trans++
            i++
        }
    }

    processRange(0, source.length, SpanStyle())

    origToTrans[source.length] = trans
    transToOrig.add(source.length)
    val toOrig = transToOrig.toIntArray()

    return TransformedText(
        annotated.toAnnotatedString(),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, origToTrans.lastIndex)]

            override fun transformedToOriginal(offset: Int): Int = toOrig[offset.coerceIn(0, toOrig.lastIndex)]
        },
    )
}

private fun inlineMarkerEnd(
    source: String,
    index: Int,
    marker: String,
): Int? {
    if (!source.startsWith(marker, index)) return null
    val innerStart = index + marker.length
    val end = source.indexOf(marker, innerStart)
    if (end < innerStart) return null
    if ('\n' in source.substring(innerStart, end)) return null
    return end
}

/** Toggles the checklist item on [lineIndex] within [text]. */
internal fun toggleChecklistAtLine(
    text: String,
    lineIndex: Int,
): String {
    val lines = text.split('\n').toMutableList()
    if (lineIndex !in lines.indices) return text
    lines[lineIndex] = toggleChecklistLine(lines[lineIndex])
    return lines.joinToString("\n")
}

/** Converts the line at [lineIndex] between checklist and plain text. */
internal fun toggleChecklistBlockAtLine(
    text: String,
    lineIndex: Int,
): String {
    val lines = text.split('\n').toMutableList()
    if (lineIndex !in lines.indices) return text
    lines[lineIndex] = toggleChecklistBlock(lines[lineIndex])
    return lines.joinToString("\n")
}

/** Checks if the cursor at [cursor] is inside a bold/italic span. */
internal fun isStyleActive(
    text: String,
    selection: TextRange,
    marker: String,
): Boolean {
    if (text.isEmpty()) return false
    val start = selection.min
    val end = selection.max

    fun isActiveAt(index: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', index - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val relativeIndex = index - lineStart

        var searchIndex = 0
        while (searchIndex < line.length) {
            val markerEnd = inlineMarkerEnd(line, searchIndex, marker)
            if (markerEnd != null) {
                val innerStart = searchIndex + marker.length
                val innerEnd = markerEnd
                if (relativeIndex in innerStart..innerEnd) return true
                searchIndex = markerEnd + marker.length
            } else {
                searchIndex++
            }
        }
        return false
    }

    // Rule: active only if the entire selection is within the style.
    if (start == end) return isActiveAt(start)
    for (i in start until end) {
        if (!isActiveAt(i)) return false
    }
    return true
}
