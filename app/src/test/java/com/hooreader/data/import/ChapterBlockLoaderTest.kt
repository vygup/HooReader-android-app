package com.hooreader.data.import

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream

class ChapterBlockLoaderTest {
    @Test
    fun `loader parses only requested chapter and stops at page limit`() = runTest {
        var emitted = 0
        var requestedChapter: Int? = null
        val document = object : ParsedBook {
            override val metadata = BookMetadata(null, null)
            override val chapters = listOf(Chapter("book", 0, null, "one", 1000), Chapter("book", 1, null, "two", 1000))
            override fun blocks(chapterIndex: Int, startBlockIndex: Int): Flow<ContentBlock> = flow {
                requestedChapter = chapterIndex
                for (index in startBlockIndex until 1000) {
                    emitted++
                    emit(ContentBlock(chapterIndex, index, BlockKind.PARAGRAPH, "text"))
                }
            }
            override suspend fun openMedia(reference: String): InputStream? = null
            override fun close() = Unit
        }
        val loader = ChapterBlockLoader(document)
        assertEquals(0, emitted)
        val page = loader.load(1, startBlockIndex = 10, limit = 5)
        assertEquals(1, requestedChapter)
        assertEquals(5, emitted)
        assertEquals((10..14).toList(), page.map { it.blockIndex })
        loader.load(1, startBlockIndex = 15, limit = 5)
        assertEquals(10, emitted)
    }
}
