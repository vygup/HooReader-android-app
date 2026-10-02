package com.hooreader.ui.reader.pagination

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
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
import com.hooreader.domain.model.ContentBlock
import com.hooreader.ui.reader.BlockTextFactory
import com.hooreader.ui.reader.ReaderTypography
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextPaginatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val key = LayoutKey(
        LayoutContentIdentity("a".repeat(64)),
        LayoutViewport(240, 180, 1f, 1f, listOf(16f, 24f)),
        LayoutTypography(1f, "Default", "SDK28", "16/24", "24/32", "Simple", "ru", "Ltr", 24f, 16f),
    )
    private val typography = ReaderTypography(
        TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
        1f,
    )
    private val paginator = TextPaginator(
        TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr, 8),
        typography,
        Density(1f),
        LayoutDirection.Ltr,
        "Нет содержимого",
    )

    @Test
    fun `fb2 lines preserve unicode styles long paragraph media and empty chapter`() = verify(BookFormat.FB2)

    @Test
    fun `epub lines preserve unicode styles long paragraph media and empty chapter`() = verify(BookFormat.EPUB)

    @Test
    fun `empty chapter is one page while later chapters prove frontier is not eof`() =
        withSession(BookFormat.FB2) { session ->
            val chapter = session.chapters.first { it.blockCount == 0 }
            assertTrue(chapter.index < session.chapters.last().index)
            val pages = mutableListOf<PageSlice>()
            paginator.paginateChapter(session, chapter.index, key, 17) { pages += it }
            assertEquals(1, pages.size)
            assertEquals(17, pages.single().globalPageNumber)
            assertEquals(BlockKind.FALLBACK, pages.single().fragments.single().kind)
            assertTrue(pages.single().endAnchorExclusive.chapterIndex < session.chapters.last().index)
        }

    private fun verify(format: BookFormat) = withSession(format, ::verifyChapters)

    private fun withSession(format: BookFormat, check: suspend (ReaderContentSession) -> Unit) = runBlocking {
        val id = UUID.randomUUID().toString()
        val input = requireNotNull(
            javaClass.getResourceAsStream("/books/corpus/reader-appearance.${format.name.lowercase()}")
        )
        val source = files.copyBook(id, format, input)
        val book = Book(
            id,
            key.content.contentHash,
            format,
            source.path,
            "Fixture",
            "HooReader",
            state = BookState.READY
        )
        val repository = ReaderContentRepository(
            files,
            BookContentIndexStore(files),
            listOf(EpubBookParser(), Fb2BookParser())
        )
        try {
            repository.open(book).use { session ->
                check(session)
            }
        } finally {
            files.deleteBook(id)
        }
    }

    private suspend fun verifyChapters(session: ReaderContentSession) {
        var prefix = 0
        for (chapter in session.chapters) {
            val pages = mutableListOf<PageSlice>()
            paginator.paginateChapter(session, chapter.index, key, prefix + 1, pages::add)
            assertTrue(pages.isNotEmpty())
            assertEquals(chapter.index, pages.first().startAnchor.chapterIndex)
            assertEquals(0, pages.first().localPageIndex)
            pages.forEachIndexed { index, page ->
                page.validateGeometry(key)
                assertEquals(prefix + index + 1, page.globalPageNumber)
                if (index > 0) assertEquals(pages[index - 1].endAnchorExclusive, page.startAnchor)
            }
            val blocks = session.readRecords(session.recordIndex(chapter.index, 0))
                .filter { it.chapterIndex == chapter.index }
            for (block in blocks) {
                val fragments = pages.flatMap { it.fragments }.filter { it.blockIndex == block.blockIndex }
                verifyBlock(session, block, fragments)
            }
            prefix += pages.size
        }
        assertTrue(prefix > session.chapters.size)
    }

    private suspend fun verifyBlock(session: ReaderContentSession, block: ContentBlock, fragments: List<PageFragment>) {
        assertFalse(fragments.isEmpty())
        if (block.kind == BlockKind.IMAGE) {
            assertEquals(1, fragments.size)
            val metrics = session.imageMetrics(requireNotNull(block.mediaRef))
            if (!metrics.isFallback) {
                assertEquals(BlockKind.IMAGE, fragments.single().kind)
                assertEquals(
                    metrics.width.toFloat() / metrics.height,
                    fragments.single().width / fragments.single().height,
                    0.001f
                )
            }
        } else {
            verifyText(block, fragments)
        }
    }

    private fun verifyText(block: ContentBlock, fragments: List<PageFragment>) {
        val layout = paginator.measure(block, key)
        assertEquals(BlockTextFactory.create(block, "Нет содержимого"), layout.layoutInput.text)
        val reconstructed = fragments.joinToString("") { fragment ->
            val part = block.text.substring(fragment.startCharacter, fragment.endCharacterExclusive)
            assertTrue(part.isEmpty() || !Character.isLowSurrogate(part.first()))
            assertTrue(part.isEmpty() || !Character.isHighSurrogate(part.last()))
            assertEquals(layout.getLineTop(fragment.firstLine), fragment.sourceTop, 0.001f)
            assertEquals(block.kind == BlockKind.LIST, fragment.x > 0f)
            part
        }
        assertEquals(block.text, reconstructed)
        if (block.text.length > 200_000) assertTrue(fragments.size > 1)
    }
}
