package com.hooreader.ui.reader

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.domain.model.logicalAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionResolverTest {
    @Test
    fun `source offsets clamp without splitting surrogate pairs and retain combining marks`() {
        val text = "A😀e\u0301漢字"
        assertEquals(0, ReaderPositionResolver.safeOffset(text, -1))
        assertEquals(1, ReaderPositionResolver.safeOffset(text, 2))
        assertEquals(3, ReaderPositionResolver.safeOffset(text, 3))
        assertEquals(4, ReaderPositionResolver.safeOffset(text, 4))
        assertEquals(text.length, ReaderPositionResolver.safeOffset(text, 999))
        val saved = ReadingPosition("book", 2, 7, 2)
        assertEquals(LogicalAnchor(2, 7, 2), saved.logicalAnchor())
        assertEquals(1, ReaderPositionResolver.characterOffset(saved, ContentBlock(2, 7, BlockKind.PARAGRAPH, text)))
    }

    @Test
    fun `missing coordinates choose nearest existing chapter and block`() {
        val saved = ReadingPosition("book", 7, 9, 99)
        val chapters = listOf(Chapter("book", 0, null, "one", 10), Chapter("book", 5, null, "two", 8))
        val chapter = ReaderPositionResolver.chapter(saved, chapters)
        assertEquals(5, chapter.index)
        assertEquals(7, ReaderPositionResolver.blockIndex(saved, chapter))
        val blocks = listOf(2, 6, 11).map { ContentBlock(5, it, BlockKind.PARAGRAPH, "text") }
        val block = ReaderPositionResolver.block(saved, blocks)
        assertEquals(11, block.blockIndex)
        assertEquals(4, ReaderPositionResolver.characterOffset(saved, block))
    }

    @Test
    fun `existing anchor and character survive and empty chapter has safe index`() {
        val saved = ReadingPosition("book", 0, 4, 2)
        val block = ContentBlock(0, 4, BlockKind.PARAGRAPH, "text")
        assertEquals(block, ReaderPositionResolver.block(saved, listOf(block)))
        assertEquals(2, ReaderPositionResolver.characterOffset(saved, block))
        assertEquals(0, ReaderPositionResolver.blockIndex(saved, Chapter("book", 0, null, "one", 0)))
    }
}
