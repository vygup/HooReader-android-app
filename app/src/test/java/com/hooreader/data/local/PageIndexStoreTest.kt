package com.hooreader.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.ui.reader.pagination.LayoutContentIdentity
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.LayoutTypography
import com.hooreader.ui.reader.pagination.LayoutViewport
import com.hooreader.ui.reader.pagination.PageFragment
import com.hooreader.ui.reader.pagination.PageSlice
import kotlinx.coroutines.CancellationException
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PageIndexStoreTest {
    private val files = BookFileStorage(ApplicationProvider.getApplicationContext<Context>())
    private val id = UUID.randomUUID().toString()
    private val store = PageIndexStore(files)
    private val key = LayoutKey(
        LayoutContentIdentity("e".repeat(64)),
        LayoutViewport(300, 600, 1f, 1f, listOf(16f, 24f)),
        LayoutTypography(1f, "Default", "Test", "16/24", "24/32", "Simple", "ru", "Ltr", 24f, 16f)
    )

    @Test
    fun `partial prefix resumes exactly and frontier cannot claim eof`() = runBlocking {
        try {
            val first = store.prepareThrough(id, key, 3, 0, ::build)
            assertEquals(1, first.completedPrefix)
            assertEquals(2, first.totalPages)
            assertFalse(first.eofKnown)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.readGlobal(first, 3) } }
            val built = mutableListOf<Int>()
            val completed = PageIndexStore(files).prepareThrough(id, key, 3, 2) { chapter, firstPage, emit ->
                built += chapter
                build(chapter, firstPage, emit)
            }
            assertEquals(listOf(1, 2), built)
            assertTrue(completed.eofKnown)
            assertEquals(6, completed.totalPages)
            assertEquals(5, store.pageContaining(completed, LogicalAnchor(2, 0, 5)).globalPageNumber)
            assertEquals(1, store.readGlobal(completed, 4).chapterIndex)
            assertEquals(completed, store.prepareThrough(id, key, 3, 0) { _, _, _ -> error("Must reuse") })
        } finally {
            files.deleteBook(id)
        }
    }

    @Test
    fun `cancel preserves validated prefix and corrupt chapter rebuilds without changing source`() = runBlocking {
        val source = files.copyBook(id, BookFormat.FB2, "original source".byteInputStream())
        try {
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    store.prepareThrough(id, key, 2, 1) { chapter, firstPage, emit ->
                        if (chapter == 1) throw CancellationException("cancel chapter")
                        build(chapter, firstPage, emit)
                    }
                }
            }
            val root = files.derivedDirectory(id, "pages", key.hash)
            assertTrue(File(root, "prefix.json").exists())
            assertFalse(File(root, "1").exists())
            assertTrue(root.walkTopDown().none { it.name.endsWith(".part") })
            File(root, "0/pages.bin").writeBytes(byteArrayOf(1))
            File(root, "prefix.json").writeText("corrupt")
            val built = mutableListOf<Int>()
            val prefix = store.prepareThrough(id, key, 2, 1) { chapter, firstPage, emit ->
                built += chapter
                build(chapter, firstPage, emit)
            }
            assertEquals(listOf(0, 1), built)
            assertTrue(prefix.eofKnown)
            store.invalidate(prefix)
            assertEquals("original source", source.readText())
            assertFalse(root.exists())
        } finally {
            files.deleteBook(id)
        }
    }

    @Test
    fun `layout eviction retains two most recently used keys`() = runBlocking {
        try {
            val other = key.copy(viewport = key.viewport.copy(widthPx = 301))
            val third = key.copy(viewport = key.viewport.copy(widthPx = 302))
            store.prepareThrough(id, key, 1, 0, ::build)
            store.prepareThrough(id, other, 1, 0, ::build)
            files.derivedDirectory(id, "pages", key.hash).setLastModified(1)
            files.derivedDirectory(id, "pages", other.hash).setLastModified(2)
            store.prepareThrough(id, key, 1, 0) { _, _, _ -> error("Reuse") }
            store.prepareThrough(id, third, 1, 0, ::build)
            assertTrue(files.derivedDirectory(id, "pages", key.hash).exists())
            assertTrue(files.derivedDirectory(id, "pages", third.hash).exists())
            assertFalse(files.derivedDirectory(id, "pages", other.hash).exists())
        } finally {
            files.deleteBook(id)
        }
    }

    private suspend fun build(chapter: Int, firstPage: Int, emit: suspend (PageSlice) -> Unit) {
        repeat(2) { local ->
            emit(
                PageSlice(
                    chapter,
                    local,
                    firstPage + local,
                    LogicalAnchor(chapter, local, 0),
                    LogicalAnchor(chapter, local + 1, 0),
                    listOf(PageFragment(chapter, local, BlockKind.PARAGRAPH, 0, 10, 0, 1, 0f, 0f, 300f, 24f, 0f))
                )
            )
        }
    }
}
