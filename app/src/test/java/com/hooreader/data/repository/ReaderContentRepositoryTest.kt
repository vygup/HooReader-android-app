package com.hooreader.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderContentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val repository = ReaderContentRepository(
        files,
        BookContentIndexStore(files),
        listOf(EpubBookParser(), Fb2BookParser()),
    )

    @Test
    fun `both formats expose stable neighbouring chapter windows without repeated source passes`() = runBlocking {
        for (format in BookFormat.entries) {
            val book = fixture(format)
            repository.open(book).use { session ->
                val passes = session.sourcePassCount
                val all = session.readRecords(0)
                assertTrue(all.size <= 128)
                assertTrue(all.map { it.chapterIndex }.distinct().size > 1)
                val late = session.window(session.chapters.last().index, 0)
                assertTrue(late.any { it.chapterIndex == session.chapters.last().index })
                assertEquals(all, session.readRecords(0))
                assertEquals(passes, session.sourcePassCount)
                val keys = all.map { "${it.chapterIndex}:${it.blockIndex}" }
                assertEquals(keys.size, keys.distinct().size)
                val image = all.first { it.kind == BlockKind.IMAGE }
                val reference = requireNotNull(image.mediaRef)
                val metrics = session.imageMetrics(reference)
                assertEquals(1, metrics.width)
                assertEquals(1, metrics.height)
                assertFalse(metrics.isFallback)
                assertEquals(metrics, session.imageMetrics(reference))
                session.openMedia(reference).use { assertTrue(requireNotNull(it).read() >= 0) }
                assertTrue(session.imageMetrics("https://example.invalid/image").isFallback)
            }
            files.deleteBook(book.id)
        }
    }

    @Test
    fun `deleted derived index is lazily recreated without changing source`() = runBlocking {
        val book = fixture(BookFormat.FB2)
        repository.open(book).use { session ->
            val expected = session.readRecords(0)
            val passes = session.sourcePassCount
            val directory = files.derivedDirectory(book.id, "content", "${book.contentHash}-1")
            assertTrue(directory.deleteRecursively())
            assertEquals(expected, session.readRecords(0))
            assertEquals(passes + 1, session.sourcePassCount)
            assertTrue(File(book.localPath).exists())
        }
        repository.open(book).use { session ->
            assertTrue(session.readRecords(0).isNotEmpty())
            assertEquals(1, session.sourcePassCount)
        }
        files.deleteBook(book.id)
    }

    private suspend fun fixture(format: BookFormat): Book {
        val id = UUID.randomUUID().toString()
        val input = requireNotNull(
            javaClass.getResourceAsStream("/books/corpus/reader-appearance.${format.name.lowercase()}")
        )
        val source = files.copyBook(id, format, input)
        return Book(id, "b".repeat(64), format, source.path, "Fixture", "HooReader", state = BookState.READY)
    }
}
