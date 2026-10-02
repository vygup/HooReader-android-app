package com.hooreader.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.BookMetadata
import com.hooreader.data.import.ParsedBook
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookContentIndexStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val store = BookContentIndexStore(files)
    private val bookId = UUID.randomUUID().toString()
    private val hash = "a".repeat(64)

    @Test
    fun `random windows cross empty chapter without reparsing and retain coordinates`() = runBlocking {
        val document = Document(bookId)
        val index = store.ensure(bookId, hash, document)
        assertEquals(1, document.passes)
        assertEquals(260, index.recordCount)
        assertEquals(0, index.chapterDirectory[1].blockCount)
        val window = store.readWindow(index, 125, 10)
        assertEquals((125..129).toList(), window.take(5).map { it.blockIndex })
        assertEquals((0..4).toList(), window.drop(5).map { it.blockIndex })
        assertTrue(window.drop(5).all { it.chapterIndex == 2 })
        assertEquals(128, store.readWindow(index, 0).size)
        assertTrue(store.readWindow(index, 260).isEmpty())
        assertEquals(index, store.ensure(bookId, hash, document))
        assertEquals(1, document.passes)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.readWindow(index, 0, 129) }
        }
        files.deleteBook(bookId)
    }

    @Test
    fun `partial corrupt and deleted spool are rebuilt without touching original`() = runBlocking {
        val source = files.copyBook(bookId, BookFormat.FB2, "immutable source".byteInputStream())
        val document = Document(bookId)
        val target = files.derivedDirectory(bookId, "content", "$hash-1")
        val part = File(target.parentFile, "unfinished.part")
        assertTrue(part.mkdirs())
        File(part, "index.json").writeText("{\"complete\":true}")
        var index = store.ensure(bookId, hash, document)
        File(target, "blocks.bin").writeBytes(byteArrayOf(1, 2, 3))
        index = store.ensure(bookId, hash, document)
        assertEquals(2, document.passes)
        assertEquals("😀e\u0301 2:129", store.readWindow(index, 259, 1).single().text)
        File(target, "index.json").writeText("{}")
        index = store.ensure(bookId, hash, document)
        assertEquals(3, document.passes)
        store.invalidate(index)
        assertFalse(target.exists())
        store.ensure(bookId, hash, document)
        assertEquals(4, document.passes)
        assertEquals("immutable source", source.readText())
        files.deleteBook(bookId)
        assertFalse(source.exists())
        assertFalse(part.exists())
    }

    @Test
    fun `cancelled build is not published and leaves source available for retry`() = runBlocking {
        val document = Document(bookId, cancelAt = 80)
        var cancelled = false
        try {
            store.ensure(bookId, hash, document)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        val target = files.derivedDirectory(bookId, "content", "$hash-1")
        assertFalse(target.exists())
        assertTrue(requireNotNull(target.parentFile).listFiles().orEmpty().none { it.name.endsWith(".part") })
        assertTrue(store.ensure(bookId, hash, Document(bookId)).complete)
        files.deleteBook(bookId)
    }

    @Test
    fun `derived paths reject traversal and invalid ownership`() {
        assertThrows(IllegalArgumentException::class.java) { files.derivedDirectory(bookId, "content", "../other") }
        assertThrows(IllegalArgumentException::class.java) { files.derivedDirectory(bookId, "../content", hash) }
        assertThrows(IllegalArgumentException::class.java) { files.derivedDirectory("invalid", "content", hash) }
    }

    private class Document(bookId: String, private val cancelAt: Int = -1) : ParsedBook {
        override val metadata = BookMetadata("Test", null)
        override val chapters = listOf(
            Chapter(bookId, 0, "First", "0", 130),
            Chapter(bookId, 1, null, "1", 0),
            Chapter(bookId, 2, null, "2", 130),
        )
        var passes = 0
            private set

        override fun blocks(chapterIndex: Int, startBlockIndex: Int): Flow<ContentBlock> =
            error("Spool must use a single ordered traversal")

        override fun orderedBlocks(): Flow<ContentBlock> = flow {
            passes++
            var emitted = 0
            chapters.forEach { chapter ->
                repeat(chapter.blockCount) { block ->
                    if (emitted++ == cancelAt) throw CancellationException("Test cancellation")
                    emit(ContentBlock(chapter.index, block, BlockKind.PARAGRAPH, "😀e\u0301 ${chapter.index}:$block"))
                }
            }
        }

        override suspend fun openMedia(reference: String): InputStream? = null
        override fun close() = Unit
    }
}
