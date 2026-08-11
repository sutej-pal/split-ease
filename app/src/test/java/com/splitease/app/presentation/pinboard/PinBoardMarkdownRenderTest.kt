package com.splitease.app.presentation.pinboard

import org.junit.jupiter.api.Assertions.assertEquals
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
}
