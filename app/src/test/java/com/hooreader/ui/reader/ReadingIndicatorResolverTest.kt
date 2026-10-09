package com.hooreader.ui.reader

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.pagination.PageFragment
import com.hooreader.ui.reader.pagination.PageSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingIndicatorResolverTest {
    private val chapters = listOf(chapter(0, "Первая", 2), chapter(1, "  ", 2))

    @Test
    fun `vertical uses first visible chapter and floors whole book percentage`() {
        val result = ReadingIndicatorResolver.resolve(
            chapters,
            ReadingPosition("book", 1, 0, progressPercent = 59.99),
            ReadingMode.VERTICAL,
        )
        assertEquals(ChapterIndicator(null, 2), result.chapter)
        assertEquals(59, result.percent)
        assertNull(result.pageNumber)
        assertEquals(
            "Первая",
            ReadingIndicatorResolver.resolve(
                chapters,
                ReadingPosition("book"),
                ReadingMode.VERTICAL,
            ).chapter?.title
        )
    }

    @Test
    fun `paginated uses displayed fragment and exact global number rather than restore anchor`() {
        val page = PageSlice(
            1,
            2,
            37,
            LogicalAnchor(1, 0, 0),
            LogicalAnchor(1, 1, 0),
            listOf(PageFragment(1, 0, BlockKind.PARAGRAPH, 0, 10, 0, 1, 0f, 0f, 100f, 20f, 0f))
        )
        val result = ReadingIndicatorResolver.resolve(chapters, ReadingPosition("book"), ReadingMode.PAGINATED, page)
        assertEquals(ChapterIndicator(null, 2), result.chapter)
        assertEquals(37, result.pageNumber)
        assertNull(result.percent)
        assertNull(
            ReadingIndicatorResolver.resolve(chapters, ReadingPosition("book"), ReadingMode.PAGINATED).pageNumber
        )
        assertNull(
            ReadingIndicatorResolver.resolve(chapters, ReadingPosition("book"), ReadingMode.VERTICAL, page).pageNumber
        )
    }

    @Test
    fun `no sections means no chapter field`() {
        val result = ReadingIndicatorResolver.resolve(emptyList(), ReadingPosition("book"), ReadingMode.VERTICAL)
        assertNull(result.chapter)
        assertEquals(0, result.percent)
    }

    @Test
    fun `progress spans all chapters and does not finish at start of last paragraph`() {
        assertEquals(0.0, progress(0, 0, 0), 0.0)
        assertEquals(62.5, progress(1, 0, 5), 0.0)
        assertEquals(75.0, progress(1, 1, 0), 0.0)
        assertEquals(100.0, progress(1, 1, 10), 0.0)
        assertEquals(100.0, progress(1, 0, 5, atEnd = true), 0.0)
    }

    @Test
    fun `single paragraph distinguishes beginning middle and viewport end`() {
        val single = listOf(chapter(0, null, 1))
        val block = ContentBlock(0, 0, BlockKind.PARAGRAPH, "0123456789")
        assertEquals(0.0, ReadingIndicatorResolver.progress(single, block, 0, atEnd = true), 0.0)
        assertEquals(50.0, ReadingIndicatorResolver.progress(single, block, 5), 0.0)
        assertEquals(100.0, ReadingIndicatorResolver.progress(single, block, 5, atEnd = true), 0.0)
        assertEquals(100.0, ReadingIndicatorResolver.progress(single, block, 10), 0.0)
    }

    @Test
    fun `empty chapters and image endpoints stay finite and bounded`() {
        val empty = listOf(chapter(0, null, 0), chapter(1, null, 1))
        val image = ContentBlock(1, 0, BlockKind.IMAGE)
        assertEquals(100.0, ReadingIndicatorResolver.progress(empty, image, 0, atEnd = true), 0.0)
        assertEquals(0.0, ReadingIndicatorResolver.progress(emptyList(), image, 0), 0.0)
    }

    private fun progress(chapter: Int, block: Int, offset: Int, atEnd: Boolean = false) =
        ReadingIndicatorResolver.progress(
            chapters,
            ContentBlock(chapter, block, BlockKind.PARAGRAPH, "0123456789"),
            offset,
            atEnd
        )

    private fun chapter(index: Int, title: String?, count: Int) = Chapter("book", index, title, "chapter:$index", count)
}
