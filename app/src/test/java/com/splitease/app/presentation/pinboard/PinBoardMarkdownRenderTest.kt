package com.splitease.app.presentation.pinboard

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinBoardMarkdownRenderTest {
    @Test
    fun wrap_selection_with_bold_markers() {
        val (text, cursor) = wrapPinBoardSelection("hello world", 6, 11, "**", "**")
        assertEquals("hello **world**", text)
        assertEquals(13, cursor)
    }

    @Test
    fun insert_checklist_at_blank_line_start() {
        val (text, cursor) = insertChecklistMarker("hello\n\nworld", 6)
        assertEquals("hello\n- [ ] \nworld", text)
        assertTrue(cursor > 6)
    }

    @Test
    fun toggle_checklist_line() {
        assertEquals("- [x] milk", toggleChecklistLine("- [ ] milk"))
        assertEquals("- [ ] milk", toggleChecklistLine("- [x] milk"))
    }

    @Test
    fun checklist_without_space_inside_brackets_is_recognized() {
        assertTrue(isChecklistLine("- []"))
        assertTrue(isChecklistLine("- [] milk"))
        assertFalse(isChecklistLineChecked("- []"))
        assertEquals("milk", checklistItemBody("- [] milk"))
        assertEquals("- [x] milk", toggleChecklistLine("- [] milk"))
    }

    @Test
    fun inline_transform_hides_bold_markers() {
        val transformed = transformPinBoardInline("**cjfific**")
        assertEquals("cjfific", transformed.text.text)
        assertTrue(transformed.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        val map = transformed.offsetMapping
        assertEquals(0, map.originalToTransformed(2))
        assertEquals(2, map.transformedToOriginal(0))
        assertEquals(transformed.text.text.length, map.originalToTransformed("**cjfific**".length))
        assertEquals("**cjfific**".length, map.transformedToOriginal(transformed.text.text.length))
    }

    @Test
    fun inline_transform_hides_italic_markers() {
        val transformed = transformPinBoardInline("_hello_")
        assertEquals("hello", transformed.text.text)
        assertTrue(transformed.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun inline_transform_hides_empty_bold_pair() {
        val transformed = transformPinBoardInline("****")
        assertEquals("", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(2))
    }

    @Test
    fun enter_on_checklist_continues_list() {
        val (text, cursor) = applyPinLineFieldChange("- [ ] milk", 0, "milk\n", 5)
        assertEquals("- [ ] milk\n- [ ] ", text)
        val (line, body) = lineBodyCursorFromFull(text, cursor)
        assertEquals(1, line)
        assertEquals(0, body)
    }

    @Test
    fun enter_on_empty_checklist_exits_list() {
        val (text, _) = applyPinLineFieldChange("- [ ] ", 0, "\n", 1)
        assertEquals("", text)
        assertFalse(parsePinTextLines(text).single().isChecklist)
    }

    @Test
    fun backspace_unwraps_checklist_then_merges_lines() {
        val unwrapped = mergePinLineBackward("- [ ] milk", 0)
        assertEquals("milk", unwrapped?.first)
        val merged = mergePinLineBackward("hello\nworld", 1)
        assertEquals("helloworld", merged?.first)
        assertEquals(5, merged?.second)
    }

    @Test
    fun typing_in_bracketless_checklist_canonicalizes_prefix() {
        val (text, _) = applyPinLineFieldChange("- []", 0, "eggs", 4)
        assertEquals("- [ ] eggs", text)
    }

    @Test
    fun toggling_another_checklist_keeps_caret_on_focused_line() {
        val text = "- [ ] milk\nhello\n- [ ] eggs"
        val cursorOnHello = fullCursorFromLineBody(text, 1, 2)
        val (updated, cursor) = cursorAfterPinLineCheckedToggle(text, cursorOnHello, 0)
        assertEquals("- [x] milk\nhello\n- [ ] eggs", updated)
        val (line, body) = lineBodyCursorFromFull(updated, cursor)
        assertEquals(1, line)
        assertEquals(2, body)
    }

    @Test
    fun toggling_focused_checklist_keeps_caret_in_that_body() {
        val text = "- [ ] milk\nhello"
        val cursorInMilk = fullCursorFromLineBody(text, 0, 3)
        val (updated, cursor) = cursorAfterPinLineCheckedToggle(text, cursorInMilk, 0)
        assertEquals("- [x] milk\nhello", updated)
        val (line, body) = lineBodyCursorFromFull(updated, cursor)
        assertEquals(0, line)
        assertEquals(3, body)
    }
}
