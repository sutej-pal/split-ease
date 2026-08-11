package com.splitease.app.presentation.pinboard

import com.splitease.app.data.pinboard.findPinBoardImages
import java.util.UUID

/**
 * Editable pin-board segment. Content is still stored as Markdown; image segments
 * are `![](path)` while text segments are raw Markdown source.
 */
internal sealed class PinBlock {
    abstract val id: String

    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val value: String,
    ) : PinBlock()

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val path: String,
    ) : PinBlock()
}

/** Parses Markdown [content] into interleaved text and image blocks. */
internal fun parsePinBlocks(content: String): List<PinBlock> {
    if (content.isEmpty()) return listOf(PinBlock.Text(value = ""))
    val blocks = mutableListOf<PinBlock>()
    var cursor = 0
    for (match in findPinBoardImages(content)) {
        if (match.range.first > cursor) {
            blocks += PinBlock.Text(value = content.substring(cursor, match.range.first))
        }
        blocks += PinBlock.Image(path = match.groupValues[2].trim())
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        blocks += PinBlock.Text(value = content.substring(cursor))
    }
    if (blocks.isEmpty()) {
        blocks += PinBlock.Text(value = "")
    }
    return ensureEditableEnds(blocks)
}

/** Serializes [blocks] back to Markdown suitable for pin_boards.content. */
internal fun serializePinBlocks(blocks: List<PinBlock>): String =
    buildString {
        blocks.forEach { block ->
            when (block) {
                is PinBlock.Text -> append(block.value)
                is PinBlock.Image -> append("![](${block.path})")
            }
        }
    }

/**
 * Inserts an image at [cursor] inside the text block at [blockIndex], splitting that
 * text block when needed.
 */
internal fun insertImageAt(
    blocks: List<PinBlock>,
    blockIndex: Int,
    cursor: Int,
    imagePath: String,
): List<PinBlock> {
    val safeIndex = blockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    if (blocks.isEmpty()) {
        return ensureEditableEnds(
            listOf(
                PinBlock.Text(value = ""),
                PinBlock.Image(path = imagePath),
                PinBlock.Text(value = ""),
            ),
        )
    }
    val target = blocks[safeIndex]
    if (target !is PinBlock.Text) {
        val insertAt = (safeIndex + 1).coerceAtMost(blocks.size)
        return ensureEditableEnds(
            blocks.toMutableList().apply {
                add(insertAt, PinBlock.Image(path = imagePath))
            },
        )
    }
    val clamped = cursor.coerceIn(0, target.value.length)
    val before = target.value.substring(0, clamped).trimEnd('\n')
    val after = target.value.substring(clamped).trimStart('\n')
    val rebuilt = mutableListOf<PinBlock>()
    blocks.forEachIndexed { index, block ->
        if (index != safeIndex) {
            rebuilt += block
            return@forEachIndexed
        }
        rebuilt +=
            PinBlock.Text(
                id = target.id,
                value = if (before.isEmpty()) "" else "$before\n",
            )
        rebuilt += PinBlock.Image(path = imagePath)
        rebuilt += PinBlock.Text(value = if (after.isEmpty()) "" else "\n$after")
    }
    return ensureEditableEnds(rebuilt)
}

/** Removes the image block with [imageId] and merges adjacent text blocks. */
internal fun removeImageBlock(
    blocks: List<PinBlock>,
    imageId: String,
): List<PinBlock> {
    val without = blocks.filterNot { it is PinBlock.Image && it.id == imageId }
    return ensureEditableEnds(mergeAdjacentText(without))
}

private fun mergeAdjacentText(blocks: List<PinBlock>): List<PinBlock> {
    if (blocks.isEmpty()) return listOf(PinBlock.Text(value = ""))
    val merged = mutableListOf<PinBlock>()
    for (block in blocks) {
        val last = merged.lastOrNull()
        if (block is PinBlock.Text && last is PinBlock.Text) {
            val left = last.value.trimEnd('\n')
            val right = block.value.trimStart('\n')
            val joined =
                when {
                    left.isEmpty() -> right
                    right.isEmpty() -> left
                    else -> "$left\n$right"
                }
            merged[merged.lastIndex] = last.copy(value = joined)
        } else {
            merged += block
        }
    }
    return merged
}

/** Ensures the board always starts and ends with a text block so the user can type. */
private fun ensureEditableEnds(blocks: List<PinBlock>): List<PinBlock> {
    if (blocks.isEmpty()) return listOf(PinBlock.Text(value = ""))
    val result = blocks.toMutableList()
    if (result.first() !is PinBlock.Text) {
        result.add(0, PinBlock.Text(value = ""))
    }
    if (result.last() !is PinBlock.Text) {
        result += PinBlock.Text(value = "")
    }
    return result
}
