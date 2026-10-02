package com.hooreader.ui.reader

import androidx.compose.ui.text.TextLayoutResult
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.domain.model.ReadingPosition
import kotlin.math.abs

/** Logical coordinates survive layout changes; pixel offsets are never persisted. */
object ReaderPositionResolver {
    fun chapter(saved: ReadingPosition, chapters: List<Chapter>): Chapter {
        require(chapters.isNotEmpty())
        return chapters.minBy { abs(it.index.toLong() - saved.chapterIndex) }
    }

    fun blockIndex(saved: ReadingPosition, chapter: Chapter): Int =
        saved.blockIndex.coerceIn(0, (chapter.blockCount - 1).coerceAtLeast(0))

    fun block(saved: ReadingPosition, blocks: List<ContentBlock>): ContentBlock {
        require(blocks.isNotEmpty())
        return blocks.minBy { abs(it.blockIndex.toLong() - saved.blockIndex) }
    }

    fun characterOffset(saved: ReadingPosition, block: ContentBlock): Int = safeOffset(
        block.text,
        saved.characterOffset
    )

    fun safeOffset(text: String, requested: Int): Int {
        val offset = requested.coerceIn(0, text.length)
        if (offset == 0 || offset == text.length) return offset
        return if (Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])
        ) {
            offset - 1
        } else {
            offset
        }
    }

    fun restoreScrollOffset(saved: ReadingPosition, block: ContentBlock, layout: TextLayoutResult?): Int {
        if (layout == null) return 0
        val offset = characterOffset(saved, block).coerceAtMost(layout.layoutInput.text.length)
        return layout.getLineTop(layout.getLineForOffset(offset)).toInt()
    }

    fun topVisibleAnchor(block: ContentBlock, layout: TextLayoutResult?, scrollOffset: Int): LogicalAnchor {
        val character = if (layout == null || block.text.isEmpty()) {
            0
        } else {
            val line = layout.getLineForVerticalPosition(scrollOffset.coerceAtLeast(0).toFloat())
            safeOffset(block.text, layout.getLineStart(line))
        }
        return LogicalAnchor(block.chapterIndex, block.blockIndex, character)
    }
}
