package com.hooreader.reader

import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.logicalAnchor
import com.hooreader.navigation.HooReaderNavHost
import com.hooreader.navigation.ReaderDependencies
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.math.sqrt

class ReaderScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()

    @Test
    fun importedBookRendersAndChapterNavigationKeepsPosition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        val files = BookFileStorage(context)
        val repository = BookRepository(database, files)
        val parsers = listOf(EpubBookParser(), Fb2BookParser())
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val added = BookImportService(files, repository, parsers).import("structured.fb2") {
            assets.open("books/structured.fb2")
        } as BookImportResult.Added
        val store = ViewModelStore()
        lateinit var reader: ReaderViewModel
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                reader = ReaderViewModel(added.book.id, repository, parsers)
                store.put("reader", reader)
            }
            compose.runOnUiThread { ReaderTestActivity.content = { HooReaderTheme { ReaderScreen(reader) {} } } }
            compose.waitUntil(15_000) { reader.state.value is ReaderUiState.Reading }
            compose.onNodeWithTag("reader_list").assertIsDisplayed()
            compose.onNodeWithText("Кириллица, café — «текст».").assertIsDisplayed()
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            compose.onNodeWithText("Следующая глава").performClick()
            compose.waitUntil(15_000) {
                (reader.state.value as? ReaderUiState.Reading)?.position?.chapterIndex == 1
            }
            compose.onNodeWithText("Абзац для восстановления позиции.").assertIsDisplayed()
            reader.flushPosition()
            assertEquals(1, repository.getPosition(added.book.id)?.chapterIndex)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ReaderTestActivity.content = null
                store.clear()
            }
            repository.deleteBook(added.book.id)
            database.close()
        }
    }

    @Test
    fun twentyTapsAndSubSlopJitterToggleOnceWithoutChangingViewportOrAnchor() = withLongReader { reader ->
        val viewport = compose.onNodeWithTag("reader_viewport")
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val anchor = reading(reader).position.logicalAnchor()
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        repeat(20) { index ->
            viewport.performTouchInput {
                down(center)
                moveTo(center + Offset(slop() / 4, slop() / 4))
                up()
            }
            if (index % 2 == 0) {
                compose.onNodeWithTag("reader_controls").assertIsDisplayed()
            } else {
                compose.onNodeWithTag("reader_controls").assertDoesNotExist()
            }
            assertEquals(bounds, viewport.fetchSemanticsNode().boundsInRoot)
            assertEquals(anchor, reading(reader).position.logicalAnchor())
        }
    }

    @Test
    fun hundredScrollCancelDiagonalReturnAndBoundaryGesturesNeverOpenControls() = withLongReader {
        val viewport = compose.onNodeWithTag("reader_viewport")
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        repeat(100) { index ->
            if (index % 7 == 6) compose.onNodeWithTag("reader_list").performScrollToIndex(0)
            viewport.performTouchInput {
                when (index % 7) {
                    0 -> swipeUp(durationMillis = 150)
                    1 -> {
                        down(center)
                        moveTo(center + Offset(slop(), 0f))
                        moveTo(center)
                        up()
                    }
                    2 -> {
                        down(center)
                        val diagonal = slop() / sqrt(2f) + 0.1f
                        moveTo(center + Offset(diagonal, diagonal))
                        up()
                    }
                    3 -> {
                        down(center)
                        moveTo(center + Offset(0f, -slop() * 2))
                        cancel()
                    }
                    4 -> {
                        down(0, center)
                        down(1, center + Offset(20f, 20f))
                        up(1)
                        up(0)
                    }
                    5 -> {
                        down(center)
                        advanceEventTime(ViewConfiguration.getLongPressTimeout().toLong() + 1)
                        up()
                    }
                    else -> {
                        down(center)
                        moveTo(center + Offset(0f, slop() * 3))
                        up()
                    }
                }
            }
            compose.onNodeWithTag("reader_controls").assertDoesNotExist()
            assertEquals(bounds, viewport.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun realDestinationMenusConsumeTouchesAndBackReturnsHiddenChrome() = runBlocking {
        val dependencies = ReaderDependencies(ApplicationProvider.getApplicationContext())
        val added = dependencies.importer.import("chrome-${UUID.randomUUID()}.fb2") {
            longBook().byteInputStream()
        } as BookImportResult.Added
        try {
            compose.runOnUiThread {
                ReaderTestActivity.content = {
                    HooReaderTheme {
                        HooReaderNavHost(libraryContent = { open ->
                            Button(onClick = { open(added.book.id) }) { Text("Открыть тест") }
                        })
                    }
                }
            }
            compose.onNodeWithText("Открыть тест").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("reader_list").fetchSemanticsNodes().isNotEmpty()
            }
            assertRapidMenuRequestsAreExclusive()
            repeat(3) {
                compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
                compose.onNodeWithTag("reader_open_contents").performTouchInput { click() }
                compose.onNodeWithTag("table_of_contents").assertIsDisplayed()
                compose.onNodeWithText("Размер текста: 100%").assertDoesNotExist()
                Espresso.pressBack()
                compose.onNodeWithTag("reader_controls").assertDoesNotExist()
                compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
                compose.onNodeWithTag("reader_open_settings").performTouchInput { click() }
                compose.onNodeWithText("Размер текста: 100%").assertIsDisplayed()
                compose.onNodeWithTag("table_of_contents").assertDoesNotExist()
                Espresso.pressBack()
                compose.onNodeWithTag("reader_controls").assertDoesNotExist()
                compose.onNodeWithTag("reader_list").assertIsDisplayed()
            }
        } finally {
            compose.runOnUiThread { ReaderTestActivity.content = null }
            dependencies.repository.deleteBook(added.book.id)
        }
    }

    private fun assertRapidMenuRequestsAreExclusive() {
        compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
        val bounds = compose.onNodeWithTag("reader_controls").fetchSemanticsNode().boundsInRoot
        val contents = compose.onNodeWithTag("reader_open_contents").fetchSemanticsNode().boundsInRoot.center
        val settings = compose.onNodeWithTag("reader_open_settings").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("reader_controls").performTouchInput {
            click(contents - bounds.topLeft)
            click(settings - bounds.topLeft)
        }
        val count = compose.onAllNodesWithTag("table_of_contents").fetchSemanticsNodes().size +
            compose.onAllNodesWithText("Размер текста: 100%").fetchSemanticsNodes().size
        assertEquals(1, count)
        Espresso.pressBack()
        compose.onNodeWithTag("reader_controls").assertDoesNotExist()
        compose.onNodeWithTag("reader_list").assertIsDisplayed()
    }

    private fun slop() = ViewConfiguration.get(compose.activity).scaledTouchSlop.toFloat()

    private fun reading(reader: ReaderViewModel) = reader.state.value as ReaderUiState.Reading

    private fun withLongReader(check: (ReaderViewModel) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        val files = BookFileStorage(context)
        val repository = BookRepository(database, files)
        val parsers = listOf(EpubBookParser(), Fb2BookParser())
        val added = BookImportService(files, repository, parsers).import("gestures.fb2") {
            longBook().byteInputStream()
        } as BookImportResult.Added
        val store = ViewModelStore()
        lateinit var reader: ReaderViewModel
        try {
            compose.runOnUiThread {
                reader = ReaderViewModel(added.book.id, repository, parsers)
                store.put("reader", reader)
                ReaderTestActivity.content = { HooReaderTheme { ReaderScreen(reader) {} } }
            }
            compose.waitUntil(15_000) { reader.state.value is ReaderUiState.Reading }
            compose.waitForIdle()
            check(reader)
        } finally {
            compose.runOnUiThread {
                ReaderTestActivity.content = null
                store.clear()
            }
            repository.deleteBook(added.book.id)
            database.close()
        }
    }

    private fun longBook() = buildString {
        append("<FictionBook><body><section><title><p>Жесты</p></title>")
        repeat(60) { append("<p>${"Длинный текст для жестов. ".repeat(20)}</p>") }
        append("</section><section><title><p>Вторая глава</p></title><p>Конец</p></section></body></FictionBook>")
    }
}
