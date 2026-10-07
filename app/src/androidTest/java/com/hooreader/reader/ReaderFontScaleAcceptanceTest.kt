package com.hooreader.reader

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.MainActivity
import com.hooreader.data.import.BookImportResult
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.logicalAnchor
import com.hooreader.navigation.ReaderDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** Run the full real-system-font matrix with scripts/verify-reader-font-scale.sh. */
class ReaderFontScaleAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dependencies = ReaderDependencies(context)
    private val arguments = InstrumentationRegistry.getArguments()
    private var caseName = ""
    private var bookId = ""

    @Test
    fun epubVerticalLight() = matrix("epub", ReadingMode.VERTICAL, ReaderTheme.LIGHT)

    @Test
    fun epubVerticalDark() = matrix("epub", ReadingMode.VERTICAL, ReaderTheme.DARK)

    @Test
    fun epubPaginatedLight() = matrix("epub", ReadingMode.PAGINATED, ReaderTheme.LIGHT)

    @Test
    fun epubPaginatedDark() = matrix("epub", ReadingMode.PAGINATED, ReaderTheme.DARK)

    @Test
    fun fb2VerticalLight() = matrix("fb2", ReadingMode.VERTICAL, ReaderTheme.LIGHT)

    @Test
    fun fb2VerticalDark() = matrix("fb2", ReadingMode.VERTICAL, ReaderTheme.DARK)

    @Test
    fun fb2PaginatedLight() = matrix("fb2", ReadingMode.PAGINATED, ReaderTheme.LIGHT)

    @Test
    fun fb2PaginatedDark() = matrix("fb2", ReadingMode.PAGINATED, ReaderTheme.DARK)

    private fun matrix(format: String, mode: ReadingMode, theme: ReaderTheme) = runBlocking {
        assumeTrue(arguments.getString("fontScaleAcceptance") == "true")
        val systemScale = requireNotNull(arguments.getString("systemScale")).toFloat()
        val readingScale = requireNotNull(arguments.getString("readingScale")).toFloat()
        val previous = dependencies.preferences.preferences.first()
        val previousApp = dependencies.preferences.appPreferences.first()
        val added = dependencies.importer.import("font-acceptance.$format") {
            fixture(format).inputStream()
        }
        // Never delete a user's duplicate fixture; this run owns only freshly imported books.
        assertTrue("Use a dedicated test installation without these fixtures", added is BookImportResult.Added)
        val book = (added as BookImportResult.Added).book
        bookId = book.id
        try {
            dependencies.preferences.setTheme(theme)
            dependencies.preferences.setFontScale(readingScale)
            dependencies.preferences.setReadingMode(mode)
            dependencies.preferences.setConfirmReaderExit(true)
            compose.onNodeWithText(book.title).performClick()
            awaitReader(mode)
            listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                .forEach { orientation ->
                    caseName = "$format-$mode-$theme-system$systemScale-reading$readingScale-$orientation"
                    rotate(orientation, mode)
                    assertEquals(systemScale, compose.activity.resources.configuration.fontScale, 0.01f)
                    if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) changeSystemFont(mode, systemScale)
                    readingActions(mode)
                    menus(mode)
                    exitActions()
                    appSettings(book.title, mode)
                }
        } finally {
            dependencies.repository.deleteBook(book.id)
            dependencies.preferences.setTheme(previous.theme)
            dependencies.preferences.setFontScale(previous.fontScale)
            dependencies.preferences.setReadingMode(previous.readingMode)
            dependencies.preferences.setConfirmReaderExit(previousApp.confirmReaderExit)
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun rotate(orientation: Int, mode: ReadingMode) {
        val before = savedAnchor()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(TIMEOUT) { compose.activity.resources.configuration.orientation == expected }
        awaitReader(mode)
        assertAnchor(before, savedAnchor(), mode)
    }

    private fun savedAnchor() = runBlocking {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.waitUntil(TIMEOUT) { runBlocking { dependencies.repository.getPosition(bookId) != null } }
        requireNotNull(dependencies.repository.getPosition(bookId)).logicalAnchor()
    }

    private fun changeSystemFont(mode: ReadingMode, requested: Float) {
        val before = savedAnchor()
        val alternate = if (requested == 1f) 2f else 1f
        try {
            listOf(alternate, requested).forEach { scale ->
                setSystemFont(scale)
                compose.waitUntil(TIMEOUT) { compose.activity.resources.configuration.fontScale == scale }
                awaitReader(mode)
                assertAnchor(before, savedAnchor(), mode)
            }
        } finally {
            setSystemFont(requested)
        }
    }

    private fun setSystemFont(scale: Float) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale").use { fd ->
                FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
    }

    private fun assertAnchor(
        before: com.hooreader.domain.model.LogicalAnchor,
        after: com.hooreader.domain.model.LogicalAnchor,
        mode: ReadingMode,
    ) {
        if (mode == ReadingMode.PAGINATED) {
            assertEquals(before, after)
        } else {
            assertEquals("Chapter drift: $before -> $after", before.chapterIndex, after.chapterIndex)
            assertTrue("More than one paragraph drift", abs(before.blockIndex - after.blockIndex) <= 1)
        }
    }

    private fun readingActions(mode: ReadingMode) {
        val viewport = compose.onNodeWithTag("reader_viewport").fetchSemanticsNode().boundsInRoot
        val chapter = compose.onNodeWithTag("reader_chapter_indicator").fetchSemanticsNode().boundsInRoot
        assertTrue(chapter.bottom <= viewport.top)
        val indicator = if (mode == ReadingMode.VERTICAL) "reader_progress_indicator" else "reader_page_indicator"
        compose.onNodeWithTag(indicator).assertIsDisplayed()
        if (mode == ReadingMode.PAGINATED) {
            assertTrue(compose.onNodeWithTag(indicator).fetchSemanticsNode().boundsInRoot.top >= viewport.bottom)
        }
        capture("reader_screen", "reading")
        toggle()
        assertActionsSeparate()
        assertEquals(viewport, compose.onNodeWithTag("reader_viewport").fetchSemanticsNode().boundsInRoot)
        capture("reader_screen", "controls")
        toggle()
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        val tag = if (mode == ReadingMode.VERTICAL) "reader_list" else "reader_pager"
        compose.onNodeWithTag(tag).performTouchInput {
            if (mode == ReadingMode.VERTICAL) swipeUp() else swipeLeft()
        }
        awaitReader(mode)
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
    }

    private fun menus(mode: ReadingMode) {
        toggle()
        compose.onNodeWithTag("reader_open_contents").performClick()
        capture("table_of_contents", "contents")
        compose.onNodeWithTag("toc_chapter_1").performScrollTo().assertIsDisplayed().performClick()
        awaitReader(mode)
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        toggle()
        compose.onNodeWithTag("reader_open_settings").performClick()
        compose.onNodeWithTag("reader_settings_sheet").performTouchInput { swipeUp() }
        compose.onNodeWithText("Увеличить текст").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Уменьшить текст").assertIsDisplayed()
        assertVisibleTextFits()
        val alternate = if (mode == ReadingMode.VERTICAL) ReadingMode.PAGINATED else ReadingMode.VERTICAL
        compose.onNodeWithTag("reading_mode_${alternate.name.lowercase()}")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(TIMEOUT) {
            runBlocking { dependencies.preferences.preferences.first().readingMode == alternate }
        }
        capture("reader_settings_sheet", "settings")
        compose.onNodeWithTag("reading_mode_${mode.name.lowercase()}")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(TIMEOUT) {
            runBlocking { dependencies.preferences.preferences.first().readingMode == mode }
        }
        Espresso.pressBack()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("reader_settings_sheet").fetchSemanticsNodes().isEmpty()
        }
        awaitReader(mode)
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
    }

    private fun exitActions() {
        Espresso.pressBack()
        capture("reader_exit_dialog", "exit")
        compose.onNodeWithTag("reader_continue_reading").assertIsDisplayed().performClick()
        compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithTag("reader_confirm_exit").assertIsDisplayed().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag("library_open_settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun appSettings(title: String, mode: ReadingMode) {
        compose.onNodeWithTag("library_open_settings").performClick()
        compose.onNodeWithTag("confirm_reader_exit").performScrollTo().assertIsDisplayed()
        capture("app_settings", "app-settings")
        Espresso.pressBack()
        compose.onNodeWithText(title).performClick()
        awaitReader(mode)
    }

    private fun assertActionsSeparate() {
        val tags = listOf("reader_exit", "reader_open_settings", "reader_open_contents")
        val bounds = tags.map { compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        bounds.forEachIndexed { index, rect ->
            bounds.drop(index + 1).forEach { other -> assertTrue("Overlapping actions", !rect.overlaps(other)) }
        }
        assertVisibleTextFits()
    }

    private fun assertVisibleTextFits() {
        val text = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val nodes = compose.onAllNodes(text, useUnmergedTree = true)
        nodes.fetchSemanticsNodes().indices.forEach { index ->
            if (nodes[index].isDisplayed()) {
                val results = mutableListOf<TextLayoutResult>()
                nodes[index].fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { layout ->
                    assertTrue("Clipped text: ${layout.layoutInput.text}", !layout.didOverflowHeight)
                    repeat(layout.lineCount) { line ->
                        // Intrinsic Text width is rounded to whole pixels; allow that rounding only.
                        assertTrue(
                            "Too wide: ${layout.layoutInput.text}",
                            layout.getLineRight(line) <= layout.size.width + 1f,
                        )
                    }
                }
            }
        }
    }

    private fun capture(tag: String, name: String) {
        assertVisibleTextFits()
        val directory = File(context.filesDir, "font-scale-evidence").apply { mkdirs() }
        File(directory, "$caseName-$name.png").outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun toggle() = compose.onNodeWithTag("reader_viewport").performTouchInput { click() }

    @Suppress("NestedBlockDepth") // Rewrite only fixture text, preserving the EPUB container.
    private fun fixture(format: String): ByteArray {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fun expand(text: String) = text
            .replace("Следующий абзац.", "Следующий абзац. ".repeat(100))
            .replace("Абзац для восстановления позиции.", "Абзац для восстановления позиции. ".repeat(100))
        return assets.open("books/structured.$format").use { source ->
            if (format == "fb2") {
                expand(source.bufferedReader().readText()).toByteArray()
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
                                    expand(bytes.toString(Charsets.UTF_8)).toByteArray()
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

    private fun awaitReader(mode: ReadingMode) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithTag(if (mode == ReadingMode.VERTICAL) "reader_list" else "reader_pager")
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("reader_preparing_pages").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
