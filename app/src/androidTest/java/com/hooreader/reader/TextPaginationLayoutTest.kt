package com.hooreader.reader

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.repository.ReaderContentRepository
import com.hooreader.data.repository.ReaderContentSession
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.pagination.PaginationProbe
import com.hooreader.pagination.ProbePageResult
import com.hooreader.ui.reader.pagination.PageIndex
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class TextPaginationLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fb2MeasuredPagesDrawAndRetainEveryUnicodeCharacter() = verify(BookFormat.FB2)

    @Test
    fun epubMeasuredPagesDrawAndRetainEveryUnicodeCharacter() = verify(BookFormat.EPUB)

    private fun verify(format: BookFormat) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = BookFileStorage(context)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val id = UUID.randomUUID().toString()
        val source = files.copyBook(
            id,
            format,
            assets.open("books/corpus/reader-appearance.${format.name.lowercase()}")
        )
        val book = Book(id, "d".repeat(64), format, source.path, "Fixture", "HooReader", state = BookState.READY)
        val repository = ReaderContentRepository(
            files,
            BookContentIndexStore(files),
            listOf(EpubBookParser(), Fb2BookParser())
        )
        try {
            repository.open(book).use { session ->
                val result = AtomicReference<ProbePageResult>()
                val anchor = LogicalAnchor(session.chapters.last().index, 0, 0)
                compose.setContent {
                    HooReaderTheme { PaginationProbe(session, files, anchor, onReady = result::set) }
                }
                compose.waitUntil(120_000) { result.get() != null }
                compose.onNodeWithTag("probe_page").assertIsDisplayed()
                val measured = result.get()
                assertTrue(measured.exactPageNumber > 1)
                assertTrue(measured.page.startAnchor <= anchor && anchor < measured.page.endAnchorExclusive)
                assertEquals(2, measured.sourcePasses)
                assertTrue(measured.residentFragments <= 128)
                verifyAllBoundaries(session, files, measured, anchor)
            }
        } finally {
            files.deleteBook(id)
        }
    }

    private suspend fun verifyAllBoundaries(
        session: ReaderContentSession,
        files: BookFileStorage,
        measured: ProbePageResult,
        anchor: LogicalAnchor,
    ) {
        val index = PageIndex(files, session.book.id, measured.key)
        var prefix = 0
        for (chapter in session.chapters) {
            val info = index.ensureChapter(chapter.index, prefix) { error("Must reuse measured index") }
            val text = mutableMapOf<Int, StringBuilder>()
            var previous: LogicalAnchor? = null
            val blocks = session.readRecords(
                session.recordIndex(chapter.index, 0),
                chapter.blockCount.coerceAtLeast(1)
            ).filter { it.chapterIndex == chapter.index }
            repeat(info.pageCount) { pageNumber ->
                val page = index.readPage(info, pageNumber)
                assertEquals(prefix + pageNumber + 1, page.globalPageNumber)
                if (previous != null) assertEquals(previous, page.startAnchor)
                previous = page.endAnchorExclusive
                page.validateGeometry(measured.key)
                page.fragments.filter { it.kind != BlockKind.IMAGE }.forEach { fragment ->
                    val block = blocks.firstOrNull { it.blockIndex == fragment.blockIndex }
                    if (block != null) {
                        val part = block.text.substring(fragment.startCharacter, fragment.endCharacterExclusive)
                        assertTrue(part.isEmpty() || !Character.isLowSurrogate(part.first()))
                        assertTrue(part.isEmpty() || !Character.isHighSurrogate(part.last()))
                        text.getOrPut(block.blockIndex) { StringBuilder() }.append(part)
                    }
                }
            }
            blocks.filter { it.kind != BlockKind.IMAGE }.forEach { block ->
                assertEquals(block.text, text[block.blockIndex].toString())
            }
            prefix += info.pageCount
        }
        assertEquals(
            measured.exactPageNumber,
            index.pageContaining(
                index.ensureChapter(
                    anchor.chapterIndex,
                    measured.exactPageNumber - 1
                ) { error("Missing prefix") },
                anchor
            ).globalPageNumber
        )
    }
}
