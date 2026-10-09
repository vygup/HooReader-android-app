package com.hooreader.ui.reader.pagination

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.local.BookFileStorage
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.LogicalAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class PageIndexTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val bookId = UUID.randomUUID().toString()
    private val key = layoutKey()

    @Test
    fun `streamed disk index supplies exact late page and reuses complete boundaries`() = runBlocking {
        val index = PageIndex(files, bookId, key)
        var builds = 0
        val info = index.ensureChapter(0, 17) { emit ->
            builds++
            repeat(300) { emit(page(it, prefix = 17)) }
        }
        assertEquals(300, info.pageCount)
        assertEquals(page(271, 17), index.readPage(info, 271))
        assertEquals(page(271, 17), index.pageContaining(info, LogicalAnchor(0, 271, 5)))
        assertEquals(info, PageIndex(files, bookId, key).ensureChapter(0, 17) { error("Must reuse") })
        assertEquals(1, builds)
        files.deleteBook(bookId)
    }

    @Test
    fun `corrupt cache rebuilds and cancellation never exposes incomplete chapter`() = runBlocking {
        val index = PageIndex(files, bookId, key)
        val info = index.ensureChapter(0, 0) { it(page(0)) }
        val root = files.derivedDirectory(bookId, "pages", key.hash)
        File(root, "0/pages.bin").writeBytes(byteArrayOf(1, 2))
        var rebuilt = false
        assertEquals(
            info,
            index.ensureChapter(0, 0) { emit ->
                rebuilt = true
                emit(page(0))
            }
        )
        assertTrue(rebuilt)
        var cancelled = false
        try {
            index.ensureChapter(1, 1) { throw CancellationException("test") }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(File(root, "1").exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
        files.deleteBook(bookId)
    }

    @Test
    fun `all geometry changes invalidate hash and at most two layouts remain`() = runBlocking {
        val keys = listOf(
            key,
            key.copy(viewport = key.viewport.copy(widthPx = 400)),
            key.copy(viewport = key.viewport.copy(systemFontScale = 2f, spConversionSamples = listOf(44f, 64f)))
        )
        keys.forEach { changed ->
            PageIndex(files, bookId, changed).ensureChapter(0, 0) { it(page(0)) }
        }
        assertEquals(3, keys.map { it.hash }.distinct().size)
        assertNotEquals(key.hash, key.copy(typography = key.typography.copy(fontIdentity = "Other font")).hash)
        val parent = requireNotNull(files.derivedDirectory(bookId, "pages", key.hash).parentFile)
        assertEquals(2, parent.listFiles().orEmpty().count { it.isDirectory })
        files.deleteBook(bookId)
    }

    @Test
    fun `prefix change cannot reuse chapter with stale global numbers`() = runBlocking {
        val index = PageIndex(files, bookId, key)
        index.ensureChapter(0, 5) { it(page(0, 5)) }
        var rebuilds = 0
        val rebuilt = index.ensureChapter(0, 12) {
            rebuilds++
            it(page(0, 12))
        }
        assertEquals(1, rebuilds)
        assertEquals(13, index.readPage(rebuilt, 0).globalPageNumber)
        files.deleteBook(bookId)
    }

    @Test
    fun `geometry content parser locale and nonlinear font samples each invalidate cache`() {
        val changes = listOf(
            key.copy(content = key.content.copy(contentHash = "d".repeat(64))),
            key.copy(content = key.content.copy(parserVersion = 2)),
            key.copy(content = key.content.copy(paginatorVersion = 2)),
            key.copy(content = key.content.copy(schemaVersion = 2)),
            key.copy(viewport = key.viewport.copy(heightPx = 500)),
            key.copy(viewport = key.viewport.copy(topStripPx = 30)),
            key.copy(viewport = key.viewport.copy(bottomStripPx = 30)),
            key.copy(viewport = key.viewport.copy(insetTopPx = 42)),
            key.copy(viewport = key.viewport.copy(insetBottomPx = 42)),
            key.copy(viewport = key.viewport.copy(insetLeftPx = 42)),
            key.copy(viewport = key.viewport.copy(insetRightPx = 42)),
            key.copy(viewport = key.viewport.copy(density = 2f)),
            key.copy(viewport = key.viewport.copy(spConversionSamples = listOf(11f, 16f, 24f, 48f))),
            key.copy(typography = key.typography.copy(locale = "ja")),
            key.copy(typography = key.typography.copy(layoutDirection = "Rtl")),
            key.copy(typography = key.typography.copy(readingScale = 2f)),
            key.copy(typography = key.typography.copy(systemFontVersion = "Next OS")),
        )
        changes.forEach { assertNotEquals(key.hash, it.hash) }
        assertEquals(changes.size, changes.map { it.hash }.distinct().size)
    }

    @Test
    fun `source boundaries and geometry reject plausible but inconsistent cache fragments`() {
        val valid = page(0)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(startAnchor = LogicalAnchor(0, 0, 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(endAnchorExclusive = LogicalAnchor(0, 1, 1))
        }
        val next = valid.fragments.single().copy(blockIndex = 1, y = 10f)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(endAnchorExclusive = LogicalAnchor(0, 2, 0), fragments = valid.fragments + next)
        }
    }

    private fun page(number: Int, prefix: Int = 0) = PageSlice(
        0,
        number,
        prefix + number + 1,
        LogicalAnchor(0, number, 0),
        LogicalAnchor(0, number + 1, 0),
        listOf(PageFragment(0, number, BlockKind.PARAGRAPH, 0, 10, 0, 1, 0f, 0f, 300f, 24f, 0f)),
    )

    private fun layoutKey() = LayoutKey(
        LayoutContentIdentity("c".repeat(64)),
        LayoutViewport(300, 600, 1f, 1f, listOf(11f, 16f, 24f, 32f)),
        LayoutTypography(1f, "Default", "Test API", "16sp/24sp", "24sp/32sp", "Simple", "ru", "Ltr", 24f, 16f),
    )
}
