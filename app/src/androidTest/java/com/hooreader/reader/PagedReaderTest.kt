package com.hooreader.reader

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.domain.model.logicalAnchor
import com.hooreader.navigation.HooReaderNavHost
import com.hooreader.navigation.HooReaderRoutes
import com.hooreader.navigation.ReaderDependencies
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderPositionResolver
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Actual destination and input. PreparingPages never satisfies waitForPages. */
class PagedReaderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private lateinit var reader: ReaderViewModel
    private lateinit var controller: NavHostController

    @Test
    fun fb2OneSwipeOnePageCancelBoundariesContentsAndReopen() = pages(BookFormat.FB2)

    @Test
    fun epubOneSwipeOnePageCancelBoundariesContentsAndReopen() = pages(BookFormat.EPUB)

    @Test
    fun fb2ModeScaleAndRotationPreserveInnerParagraphAnchor() = geometry(BookFormat.FB2)

    @Test
    fun epubModeScaleAndRotationPreserveInnerParagraphAnchor() = geometry(BookFormat.EPUB)

    private fun pages(format: BookFormat) = withBook(format) { dependencies, book ->
        chooseMode("reading_mode_paginated")
        waitForPages()
        assertStablePagesAcrossTaps()
        val number = pageNumber()
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft(durationMillis = 150) }
        waitForPage(number + 1)
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeRight(durationMillis = 150) }
        waitForPage(number)
        compose.onNodeWithTag("reader_pager").performTouchInput {
            down(center)
            moveTo(center + Offset(-30f, 0f))
            cancel()
        }
        compose.waitForIdle()
        assertEquals(number, pageNumber())
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        selectChapter(0)
        waitForPage(1)
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, pageNumber())
        val last = dependencies.repository.getChapters(book.id).last().index
        selectChapter(last)
        waitForPages()
        val finalPage = pageNumber()
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(finalPage, pageNumber())
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeRight() }
        waitForPage(finalPage - 1)
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft() }
        waitForPage(finalPage)
        compose.activityRule.scenario.recreate()
        waitForPage(finalPage)
        reopenReader(dependencies, book, finalPage)
    }

    private fun assertStablePagesAcrossTaps() {
        val before = reader.state.value as ReaderUiState.Reading
        val viewport = compose.onNodeWithTag("reader_viewport")
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        repeat(20) { index ->
            viewport.performTouchInput { click() }
            if (index % 2 == 0) {
                compose.onNodeWithTag("reader_controls").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("reader_controls").assertDoesNotExist()
            }
            val after = reader.state.value as ReaderUiState.Reading
            assertEquals(before.logicalPosition, after.logicalPosition)
            assertEquals(before.layoutGeneration, after.layoutGeneration)
            assertEquals(before.pages, after.pages)
            assertEquals(bounds, viewport.fetchSemanticsNode().boundsInRoot)
        }
    }

    private suspend fun reopenReader(dependencies: ReaderDependencies, book: Book, number: Int) {
        val previous = reader
        val anchor = (previous.state.value as ReaderUiState.Reading).logicalPosition
        assertEquals(ReadingMode.PAGINATED, dependencies.preferences.preferences.first().readingMode)
        compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
        compose.onNodeWithTag("reader_exit").performClick()
        compose.onNodeWithTag("reader_confirm_exit").performClick()
        compose.waitUntil(TIMEOUT) { !exists("reader_screen") }
        compose.onNodeWithText("Открыть fixture").performClick()
        waitForPage(number)
        compose.runOnIdle {
            reader = ViewModelProvider(controller.getBackStackEntry(HooReaderRoutes.READER))
                .get(book.id, ReaderViewModel::class.java)
        }
        assertNotSame(previous, reader)
        val restored = reader.state.value as ReaderUiState.Reading
        assertEquals(ReadingMode.PAGINATED, restored.effectiveMode)
        assertEquals(anchor, restored.logicalPosition)
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
    }

    private fun geometry(format: BookFormat) = withBook(format) { dependencies, book ->
        val before = (reader.state.value as ReaderUiState.Reading).position
        assertTrue(before.characterOffset > 0)
        chooseMode("reading_mode_paginated")
        waitForPages()
        assertEquals(
            before.logicalAnchor(),
            requireNotNull(dependencies.repository.getPosition(book.id)).logicalAnchor()
        )
        openSettings()
        compose.onNodeWithText("Увеличить текст").performClick()
        back()
        waitForPages()
        assertEquals(
            before.logicalAnchor(),
            requireNotNull(dependencies.repository.getPosition(book.id)).logicalAnchor()
        )
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(TIMEOUT) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        waitForPages()
        compose.activityRule.scenario.recreate()
        waitForPages()
        assertEquals(
            before.logicalAnchor(),
            requireNotNull(dependencies.repository.getPosition(book.id)).logicalAnchor()
        )
        chooseMode("reading_mode_vertical")
        compose.waitUntil(TIMEOUT) { exists("reader_list") }
        val after = requireNotNull(dependencies.repository.getPosition(book.id))
        assertEquals(before.chapterIndex, after.chapterIndex)
        assertEquals(before.blockIndex, after.blockIndex)
        assertTrue(after.characterOffset > 0)
    }

    private fun chooseMode(tag: String) {
        openSettings()
        compose.onNodeWithTag(tag).performClick()
        back()
    }

    private fun openSettings() {
        compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
        compose.onNodeWithTag("reader_open_settings").performTouchInput { click() }
    }

    private fun selectChapter(index: Int) {
        compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
        compose.onNodeWithTag("reader_open_contents").performTouchInput { click() }
        compose.onNodeWithTag("toc_chapter_$index").performClick()
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun pageNumber(): Int = compose.onNodeWithTag("reader_page_indicator").fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text.toInt()

    private fun waitForPages() {
        compose.waitUntil(TIMEOUT) { exists("reader_pager") && !exists("reader_preparing_pages") }
        compose.onNodeWithTag("reader_page_indicator").assertIsDisplayed()
        compose.onNodeWithTag("reader_progress_indicator").assertDoesNotExist()
    }

    private fun waitForPage(number: Int) {
        compose.waitUntil(TIMEOUT) {
            exists("reader_page_indicator") && !exists("reader_preparing_pages") && pageNumber() == number
        }
    }

    private fun withBook(format: BookFormat, check: suspend (ReaderDependencies, Book) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = ReaderDependencies(context)
        val previous = dependencies.preferences.preferences.first()
        val previousMode = previous.readingMode
        val previousApp = dependencies.preferences.appPreferences.first()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val added = dependencies.importer.import("paged.${format.name.lowercase()}") {
            assets.open("books/corpus/reader-appearance.${format.name.lowercase()}")
        } as BookImportResult.Added
        try {
            dependencies.preferences.setConfirmReaderExit(true)
            prepareAnchor(dependencies, added.book)
            compose.runOnUiThread {
                ReaderTestActivity.content = {
                    HooReaderTheme {
                        controller = rememberNavController()
                        HooReaderNavHost(navController = controller, libraryContent = { open ->
                            Button(onClick = { open(added.book.id) }) { Text("Открыть fixture") }
                        })
                    }
                }
            }
            compose.onNodeWithText("Открыть fixture").performClick()
            compose.waitUntil(TIMEOUT) { exists("reader_list") }
            compose.waitForIdle()
            compose.runOnIdle {
                reader = ViewModelProvider(controller.getBackStackEntry(HooReaderRoutes.READER))
                    .get(added.book.id, ReaderViewModel::class.java)
            }
            check(dependencies, added.book)
        } finally {
            compose.runOnUiThread { ReaderTestActivity.content = null }
            dependencies.repository.deleteBook(added.book.id)
            dependencies.preferences.setConfirmReaderExit(previousApp.confirmReaderExit)
            dependencies.preferences.setReadingMode(previousMode)
            dependencies.preferences.setTheme(previous.theme)
            dependencies.preferences.setFontScale(previous.fontScale)
        }
    }

    private suspend fun prepareAnchor(dependencies: ReaderDependencies, book: Book) {
        dependencies.preferences.setReadingMode(ReadingMode.VERTICAL)
        dependencies.preferences.setFontScale(1f)
        dependencies.readerContent.open(book).use { session ->
            val block = session.readRecords(0).maxBy { it.text.length }
            assertTrue("Fixture must contain the long UTF-16 paragraph", block.text.length > 100_000)
            dependencies.repository.savePosition(
                ReadingPosition(
                    book.id,
                    block.chapterIndex,
                    block.blockIndex,
                    ReaderPositionResolver.safeOffset(block.text, 15_000),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private companion object {
        const val TIMEOUT = 120_000L
    }
}
