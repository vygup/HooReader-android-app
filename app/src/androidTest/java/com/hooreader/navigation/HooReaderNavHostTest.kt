package com.hooreader.navigation

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.hooreader.data.import.BookImportResult
import com.hooreader.domain.model.ReadingMode
import com.hooreader.ui.reader.ReadingPositionSaver
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class HooReaderNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bookSelectionPassesIdAndReturnsToLibrary() {
        val id = "6d43d8d6-8cdf-4cbd-8050-1d41a2bbf2ca"
        compose.setContent {
            HooReaderTheme {
                HooReaderNavHost(
                    libraryContent = { openBook ->
                        Button(onClick = { openBook(id) }) { Text("Открыть книгу") }
                    },
                    readerContent = { bookId, goBack ->
                        Button(onClick = goBack) { Text("Назад: $bookId") }
                    },
                )
            }
        }
        compose.onNodeWithText("Открыть книгу").performClick()
        compose.onNodeWithText("Назад: $id").assertIsDisplayed().performClick()
        compose.onNodeWithText("Открыть книгу").assertIsDisplayed()
    }

    @Test
    @Suppress("LongMethod")
    fun realReaderPrioritizesMenusCancelsDialogAndRetriesFailedExit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fail = AtomicBoolean(false)
        val hold = AtomicBoolean(false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dependencies = ReaderDependencies(context) { repository ->
            ReadingPositionSaver({ position ->
                if (fail.get()) throw IOException("Injected position write failure")
                if (hold.get()) {
                    entered.complete(Unit)
                    release.await()
                }
                repository.savePosition(position)
            })
        }
        val old = dependencies.preferences.preferences.first()
        val oldApp = dependencies.preferences.appPreferences.first()
        val added = dependencies.importer.import("exit-test.fb2") {
            buildString {
                append("<FictionBook><description><title-info><book-title>Защита выхода</book-title>")
                append("</title-info></description><body><section><title><p>Глава</p></title>")
                repeat(50) { append("<p>Абзац $it. ${"Проверка позиции. ".repeat(20)}</p>") }
                append("</section></body></FictionBook>")
            }.byteInputStream()
        } as BookImportResult.Added
        try {
            dependencies.preferences.setReadingMode(ReadingMode.VERTICAL)
            dependencies.preferences.setConfirmReaderExit(true)
            compose.setContent {
                HooReaderTheme {
                    HooReaderNavHost(
                        readerDependencies = dependencies,
                        libraryContent = { open -> Button(onClick = { open(added.book.id) }) { Text("Открыть") } },
                    )
                }
            }
            compose.onNodeWithText("Открыть").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("reader_list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader_list").performScrollToIndex(12)
            compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            for (menu in listOf("reader_open_settings", "reader_open_contents")) {
                compose.onNodeWithTag(menu).performClick()
                pressBack()
                compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
                compose.onNodeWithTag("reader_controls").assertDoesNotExist()
                compose.onNodeWithTag("reader_viewport").performTouchInput { click() }
            }
            compose.onNodeWithTag("reader_exit").performClick()
            compose.onNodeWithTag("reader_exit_dialog").assertIsDisplayed()
            pressBack()
            awaitReaderFocus()
            compose.onNodeWithTag("reader_controls").assertIsDisplayed()
            compose.onNodeWithTag("block_0_12").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("reader_continue_reading").performClick()
            awaitReaderFocus()
            pressBack()
            compose.onNodeWithTag("reader_exit_dialog").assertIsDisplayed()
            tapOutsideDialog()
            compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
            awaitReaderFocus()
            compose.onNodeWithTag("block_0_12").assertIsDisplayed()
            fail.set(true)
            compose.onNodeWithTag("reader_list").performScrollToIndex(20)
            pressBack()
            compose.onNodeWithTag("reader_confirm_exit").performClick()
            compose.onNodeWithTag("reader_exit_retry").assertIsDisplayed()
            compose.onNodeWithTag("reader_screen").assertIsDisplayed()
            fail.set(false)
            hold.set(true)
            compose.onNodeWithTag("reader_exit_retry").performClick()
            compose.waitUntil(5_000) { entered.isCompleted }
            repeat(5) { pressBack() }
            compose.onNodeWithTag("reader_exit_dialog").assertDoesNotExist()
            compose.onNodeWithTag("reader_screen").assertIsDisplayed()
            compose.onNodeWithText("Открыть").assertDoesNotExist()
            release.complete(Unit)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("reader_screen").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("Открыть").assertIsDisplayed()
            assertEquals(20, dependencies.repository.getPosition(added.book.id)?.blockIndex)
        } finally {
            fail.set(false)
            release.complete(Unit)
            dependencies.repository.deleteBook(added.book.id)
            dependencies.preferences.setReadingMode(old.readingMode)
            dependencies.preferences.setConfirmReaderExit(oldApp.confirmReaderExit)
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun awaitReaderFocus() = compose.waitUntil(5_000) {
        var focused = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            focused = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it.window.decorView.hasWindowFocus() }
        }
        focused
    }

    private fun tapOutsideDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, 2f, 200f, 0)
            try {
                automation.injectInputEvent(event, true)
            } finally {
                event.recycle()
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("reader_exit_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun startupShowsLibrary() {
        compose.setContent { HooReaderTheme { HooReaderNavHost() } }
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        compose.onNodeWithText("Пока нет книг").assertIsDisplayed()
    }
}
