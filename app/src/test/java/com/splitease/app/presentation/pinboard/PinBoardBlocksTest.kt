package com.splitease.app.presentation.pinboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinBoardBlocksTest {
    @Test
    fun parse_round_trips_text_and_image() {
        val markdown = "Hello\n![](/tmp/a.jpg)\nWorld"
        val blocks = parsePinBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is PinBlock.Text)
        assertTrue(blocks[1] is PinBlock.Image)
        assertEquals("/tmp/a.jpg", (blocks[1] as PinBlock.Image).path)
        assertTrue(blocks[2] is PinBlock.Text)
        assertEquals(markdown, serializePinBlocks(blocks))
    }

    @Test
    fun insert_image_splits_focused_text() {
        val start = listOf(PinBlock.Text(value = "ab"))
        val next = insertImageAt(start, blockIndex = 0, cursor = 1, imagePath = "/img.jpg")
        assertEquals("a\n![](/img.jpg)\nb", serializePinBlocks(next).trim())
        assertTrue(next.any { it is PinBlock.Image && it.path == "/img.jpg" })
    }

    @Test
    fun remove_image_merges_adjacent_text() {
        val blocks =
            parsePinBlocks("before\n![](/img.jpg)\nafter")
        val imageId = blocks.filterIsInstance<PinBlock.Image>().single().id
        val next = removeImageBlock(blocks, imageId)
        assertTrue(next.none { it is PinBlock.Image })
        assertEquals("before\nafter", serializePinBlocks(next))
    }
}
