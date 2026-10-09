package com.hooreader.reader

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ReadingIndicatorsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private lateinit var reader: ReaderViewModel

    @Test
    fun epubVerticalLight() = indicators("epub", ReadingMode.VERTICAL, false)

    @Test
    fun epubVerticalDark() = indicators("epub", ReadingMode.VERTICAL, true)

    @Test
    fun fb2VerticalLight() = indicators("fb2", ReadingMode.VERTICAL, false)

    @Test
    fun fb2VerticalDark() = indicators("fb2", ReadingMode.VERTICAL, true)

    @Test
    fun epubPaginatedLight() = indicators("epub", ReadingMode.PAGINATED, false)

    @Test
    fun epubPaginatedDark() = indicators("epub", ReadingMode.PAGINATED, true)

    @Test
    fun fb2PaginatedLight() = indicators("fb2", ReadingMode.PAGINATED, false)

    @Test
    fun fb2PaginatedDark() = indicators("fb2", ReadingMode.PAGINATED, true)

    private fun indicators(format: String, mode: ReadingMode, dark: Boolean) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        val repository = BookRepository(database, BookFileStorage(context))
        val parsers = listOf(EpubBookParser(), Fb2BookParser())
        val added = BookImportService(repository.files, repository, parsers).import("indicators.$format") {
            fixture(format).inputStream()
        } as BookImportResult.Added
        val store = ViewModelStore()
        val scale = InstrumentationRegistry.getArguments().getString("indicatorReadingScale")?.toFloat() ?: 1f
        fun open() {
            compose.runOnUiThread {
                store.clear()
                reader = ReaderViewModel(
                    added.book.id, repository, parsers,
                    initialPreferences = ReaderPreferences(readingMode = mode, fontScale = scale)
                )
                store.put("reader", reader)
                ReaderTestActivity.content = { HooReaderTheme(dark) { ReaderScreen(reader, fontScale = scale) {} } }
            }
            awaitReading(mode)
        }
        try {
            open()
            assertIndicators(mode, LONG_TITLE)
            assertStableChrome()
            crossChapter(mode)
            selectChapter(0)
            awaitReading(mode)
            assertIndicators(mode, LONG_TITLE)
            selectChapter(1)
            awaitReading(mode)
            assertIndicators(mode, "Вторая глава")
            val anchor = reading().logicalPosition
            assertTrue(reader.flushPosition())
            open()
            assertEquals(anchor, reading().logicalPosition)
            assertIndicators(mode, "Вторая глава")
            selectChapter(0)
            awaitReading(mode)
            captureOrientations("$format-${mode.name}-${if (dark) "dark" else "light"}", mode)
        } finally {
            compose.runOnUiThread {
                ReaderTestActivity.content = null
                store.clear()
            }
            repository.deleteBook(added.book.id)
            database.close()
        }
    }

    private fun crossChapter(mode: ReadingMode) {
        if (mode == ReadingMode.PAGINATED) {
            repeat(40) {
                if (reading().position.chapterIndex == 1) return
                compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft(durationMillis = 150) }
                compose.waitForIdle()
                awaitReading(mode)
            }
        } else {
            // First visible source fragment, even when the following chapter is also on screen.
            compose.onNodeWithTag("reader_list").performScrollToIndex(1)
            compose.waitForIdle()
            assertIndicators(mode, LONG_TITLE)
            repeat(40) {
                if (reading().position.chapterIndex == 1) return
                compose.onNodeWithTag("reader_list").performTouchInput { swipeUp(durationMillis = 150) }
                compose.waitForIdle()
            }
        }
        assertEquals("Navigation must cross the chapter boundary", 1, reading().position.chapterIndex)
    }

    private fun assertIndicators(mode: ReadingMode, title: String) {
        compose.onNodeWithTag("reader_chapter_indicator").assertIsDisplayed().assertTextEquals(title)
        val viewport = compose.onNodeWithTag("reader_viewport").fetchSemanticsNode().boundsInRoot
        val chapter = compose.onNodeWithTag("reader_chapter_indicator").fetchSemanticsNode().boundsInRoot
        assertTrue("Chapter must be above the content", chapter.bottom <= viewport.top)
        if (mode == ReadingMode.VERTICAL) {
            compose.onNodeWithTag("reader_page_indicator").assertDoesNotExist()
            compose.onNodeWithTag("reader_progress_indicator").assertIsDisplayed()
                .assertTextEquals("${reading().indicator.percent}%")
            val percent = compose.onNodeWithTag("reader_progress_indicator").fetchSemanticsNode().boundsInRoot
            assertTrue(chapter.right <= percent.left && percent.bottom <= viewport.top)
            assertUnclipped("reader_progress_indicator")
        } else {
            compose.onNodeWithTag("reader_progress_indicator").assertDoesNotExist()
            val page = requireNotNull(reading().pages)
            compose.onNodeWithTag("reader_page_indicator").assertIsDisplayed()
                .assertTextEquals(page.displayedPage.globalPageNumber.toString())
            val footer = compose.onNodeWithTag("reader_page_indicator").fetchSemanticsNode().boundsInRoot
            assertTrue("Page number must be below the content", footer.top >= viewport.bottom)
            assertEquals(viewport.width.toInt(), page.key.viewport.widthPx)
            assertEquals(viewport.height.toInt(), page.key.viewport.heightPx)
            page.displayedPage.validateGeometry(page.key)
            assertUnclipped("reader_page_indicator")
        }
    }

    private fun assertUnclipped(tag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action?.invoke(layouts)
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertTrue("$tag height", !layout.didOverflowHeight)
            assertEquals(1, layout.lineCount)
            assertTrue("$tag ellipsis", !layout.isLineEllipsized(0))
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(0))
            assertTrue("$tag left edge", layout.getLineLeft(0) >= 0f)
            assertTrue("$tag right edge", layout.getLineRight(0) <= layout.size.width + 1f)
        }
    }

    private fun assertStableChrome() {
        val before = reading()
        val bounds = compose.onNodeWithTag("reader_viewport").fetchSemanticsNode().boundsInRoot
        repeat(2) {
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.waitForIdle()
            assertEquals(bounds, compose.onNodeWithTag("reader_viewport").fetchSemanticsNode().boundsInRoot)
            assertEquals(before.logicalPosition, reading().logicalPosition)
            assertEquals(before.pages, reading().pages)
            assertEquals(before.layoutGeneration, reading().layoutGeneration)
        }
    }

    private fun captureOrientations(name: String, mode: ReadingMode) {
        listOf(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        ).forEach { orientation ->
            compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(TIMEOUT) { compose.activity.resources.configuration.orientation == expected }
            awaitReading(mode)
            assertIndicators(mode, LONG_TITLE)
            assertStableChrome()
            val directory = File(compose.activity.filesDir, "indicator-evidence").apply { mkdirs() }
            val font = compose.activity.resources.configuration.fontScale
            val requested = InstrumentationRegistry.getArguments().getString("indicatorSystemScale")
            if (requested != null) assertEquals(requested.toFloat(), font, 0.01f)
            val filename = "$name-system$font-reading${reading().fontScale}-orientation$expected.png"
            File(directory, filename).outputStream().use {
                compose.onNodeWithTag("reader_screen").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun selectChapter(index: Int) {
        compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
        compose.onNodeWithTag("reader_open_contents").performClick()
        compose.onNodeWithTag("toc_chapter_$index").performClick()
    }

    private fun awaitReading(mode: ReadingMode) {
        compose.waitUntil(TIMEOUT) {
            reader.state.value is ReaderUiState.Reading &&
                compose.onAllNodesWithTag(if (mode == ReadingMode.PAGINATED) "reader_pager" else "reader_list")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun reading() = reader.state.value as ReaderUiState.Reading

    @Suppress("NestedBlockDepth") // Streaming rewrite preserves the original EPUB fixture structure.
    private fun fixture(format: String): ByteArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fun replace(text: String) = text.replace("Первая глава", LONG_TITLE)
            .replace("Следующий абзац.", "Следующий абзац. ".repeat(160))
        return assets.open("books/structured.$format").use { source ->
            if (format == "fb2") {
                replace(source.bufferedReader().readText()).toByteArray()
            } else {
                val output = ByteArrayOutputStream()
                ZipOutputStream(output).use { zip ->
                    ZipInputStream(source).use { input ->
                        var entry = input.nextEntry
                        while (entry != null) {
                            val bytes = input.readBytes()
                            zip.putNextEntry(ZipEntry(entry.name))
                            zip.write(
                                if (entry.name.endsWith("xhtml")) {
                                    replace(bytes.toString(Charsets.UTF_8)).toByteArray()
                                } else {
                                    bytes
                                }
                            )
                            zip.closeEntry()
                            entry = input.nextEntry
                        }
                    }
                }
                output.toByteArray()
            }
        }
    }

    private companion object {
        const val TIMEOUT = 30_000L
        const val LONG_TITLE = "Первая глава — длинное название о путешествии, встречах и возвращении домой"
    }
}
