package com.splitease.app.presentation.pinboard

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val CHECKLIST_LINE =
    Regex("""^(\s*)-\s*\[( |x|X)\]\s*(.*)$""")

/** Whether [line] is a markdown checklist item. */
internal fun isChecklistLine(line: String): Boolean = CHECKLIST_LINE.matches(line)

/** Toggles a checklist line between unchecked and checked. */
internal fun toggleChecklistLine(line: String): String {
    val match = CHECKLIST_LINE.matchEntire(line) ?: return line
    val indent = match.groupValues[1]
    val checked = match.groupValues[2] != " "
    val body = match.groupValues[3]
    val mark = if (checked) " " else "x"
    return "$indent- [$mark] $body"
}

/** Checklist item label without the `- [ ]` prefix. */
internal fun checklistItemBody(line: String): String =
    CHECKLIST_LINE.matchEntire(line)?.groupValues?.get(3).orEmpty()

internal fun isChecklistLineChecked(line: String): Boolean {
    val mark = CHECKLIST_LINE.matchEntire(line)?.groupValues?.get(2) ?: return false
    return mark.equals("x", ignoreCase = true)
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
            safeCursor == lineEnd -> "\n- [ ] "
            else -> "\n- [ ] "
        }
    val insertAt =
        when {
            linePrefix.isBlank() -> lineStart
            safeCursor == lineEnd -> lineEnd
            else -> safeCursor
        }
    val newText = text.replaceRange(insertAt, insertAt, insert)
    return newText to (insertAt + insert.length)
}

/** Renders inline markdown (**bold**, _italic_) for pin-board display. */
internal fun buildPinBoardAnnotatedString(text: String): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end > index + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                        continue
                    }
                }
                text.startsWith("_", index) -> {
                    val end = text.indexOf("_", index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                        continue
                    }
                }
            }
            append(text[index])
            index++
        }
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
