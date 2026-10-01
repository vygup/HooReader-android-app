package com.hooreader.ui.reader

import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
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

    fun characterOffset(saved: ReadingPosition, block: ContentBlock): Int =
        saved.characterOffset.coerceIn(0, block.text.length)
}
