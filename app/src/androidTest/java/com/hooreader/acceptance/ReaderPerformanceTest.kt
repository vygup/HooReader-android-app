package com.hooreader.acceptance

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.navigation.ReaderDependencies
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.library.LibraryScreen
import com.hooreader.ui.library.LibraryStatus
import com.hooreader.ui.library.LibraryUiState
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in measurements; thresholds are assessed in the report, never silently treated as passing. */
class ReaderPerformanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dependencies = ReaderDependencies(context)
    private val imported = mutableListOf<String>()

    @Test
    fun measureOpeningAndScrolling() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("phase7Performance") == "true")
        val source = makeLargeBook()
        val preferences = dependencies.preferences.preferences.first()
        val stores = mutableListOf<ViewModelStore>()
        try {
            dependencies.preferences.setTheme(ReaderPreferences().theme)
            dependencies.preferences.setFontScale(1f)
            val result = dependencies.importer.import(source.name) { source.inputStream() } as BookImportResult.Added
            val book = result.book
            imported += book.id
            assertEquals(BOOK_BYTES.toLong(), File(book.localPath).length())
            val opening = JSONArray()
            repeat(OPEN_RUNS) {
                val store = ViewModelStore().also { stores += it }
                lateinit var reader: ReaderViewModel
                val start = SystemClock.elapsedRealtimeNanos()
                compose.runOnUiThread {
                    reader = ReaderViewModel(book.id, dependencies.repository, dependencies.parsers)
                    store.put("reader", reader)
                    ReaderTestActivity.content = { HooReaderTheme { ReaderScreen(reader) {} } }
                }
                compose.waitUntil(OPEN_TIMEOUT_MS) { reader.state.value is ReaderUiState.Reading }
                compose.waitForIdle()
                val milliseconds = (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MS
                opening.put(milliseconds)
                if (it < OPEN_RUNS - 1) clearContent(store)
            }
            val readerFrames = scrollFrames("reader_list")
            stores.forEach { clearContent(it) }
            importLibrary()
            // The reader book is removed so this is exactly 100 READY records created by real imports.
            dependencies.repository.deleteBook(book.id)
            imported.remove(book.id)
            assertEquals(LIBRARY_SIZE, dependencies.repository.books.first().size)
            showLibrary()
            compose.waitUntil(OPEN_TIMEOUT_MS) {
                compose.onAllNodesWithTag("library_list").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            val libraryFrames = scrollFrames("library_list")
            val report = JSONObject().put("bookBytes", BOOK_BYTES).put("openingMs", opening)
                .put("librarySize", LIBRARY_SIZE).put("reader", readerFrames).put("library", libraryFrames)
                .put("build", "debug").put("openRuns", OPEN_RUNS).put("swipes", SWIPES)
                .put("swipeDurationMs", SWIPE_DURATION_MS)
            File(context.filesDir, "phase7-performance.json").writeText(report.toString(2))
        } finally {
            compose.runOnUiThread { ReaderTestActivity.content = null }
            stores.forEach { compose.runOnUiThread { it.clear() } }
            imported.forEach { dependencies.repository.deleteBook(it) }
            dependencies.preferences.setTheme(preferences.theme)
            dependencies.preferences.setFontScale(preferences.fontScale)
            source.delete()
        }
    }

    private fun showLibrary() = compose.runOnUiThread {
        ReaderTestActivity.content = {
            val entries by dependencies.library.books.collectAsStateWithLifecycle(emptyList())
            HooReaderTheme {
                LibraryScreen(LibraryUiState(LibraryStatus.CONTENT, entries), {}, {}, {})
            }
        }
    }

    private suspend fun importLibrary() {
        repeat(LIBRARY_SIZE) { index ->
            val xml = "<FictionBook><description><title-info><book-title>Замер $index</book-title>" +
                "</title-info></description><body><section><p>Книга $index.</p></section></body></FictionBook>"
            val added = dependencies.importer.import("library-$index.fb2") { xml.byteInputStream() }
                as BookImportResult.Added
            imported += added.book.id
        }
    }

    private fun clearContent(store: ViewModelStore) {
        compose.runOnUiThread {
            ReaderTestActivity.content = null
            store.clear()
        }
        compose.waitForIdle()
    }

    private fun scrollFrames(tag: String): JSONObject {
        val thread = HandlerThread("phase7-frame-metrics").apply { start() }
        val handler = Handler(thread.looper)
        val durations = mutableListOf<Long>()
        var dropped = 0
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
            dropped += lost
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                durations += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
        }
        val window = compose.activity.window
        compose.runOnUiThread { window.addOnFrameMetricsAvailableListener(listener, handler) }
        try {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val x = bounds.center.x.toInt()
            val from = (bounds.top + bounds.height * SWIPE_START).toInt()
            val to = (bounds.top + bounds.height * SWIPE_END).toInt()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            repeat(SWIPES) {
                // Real wall-clock gestures: Compose synthetic input can batch a swipe into only two frames.
                automation.executeShellCommand("input swipe $x $from $x $to $SWIPE_DURATION_MS").use { fd ->
                    FileInputStream(fd.fileDescriptor).use { it.readBytes() }
                }
                compose.waitForIdle()
            }
        } finally {
            compose.runOnUiThread { window.removeOnFrameMetricsAvailableListener(listener) }
            val drained = CountDownLatch(1)
            handler.post { drained.countDown() }
            check(drained.await(FRAME_DRAIN_SECONDS, TimeUnit.SECONDS))
            thread.quitSafely()
            thread.join()
        }
        assertTrue("No rendered scroll frames collected for $tag", durations.isNotEmpty())
        return JSONObject().put("totalDurationNs", JSONArray(durations)).put("droppedCallbacks", dropped)
    }

    private fun makeLargeBook(): File {
        val file = File(context.cacheDir, "performance-20mb.fb2")
        val header = "<FictionBook><description><title-info><book-title>Performance 20 MB</book-title>" +
            "</title-info></description><body><section>"
        val footer = "</section></body></FictionBook>"
        val paragraph = "<p>${"Ordinary reading paragraph with punctuation. ".repeat(PARAGRAPH_REPETITIONS)}</p>"
        file.bufferedWriter(Charsets.UTF_8).use { output ->
            output.write(header)
            var remaining = BOOK_BYTES - header.length - footer.length
            while (remaining >= paragraph.length + EMPTY_PARAGRAPH_BYTES) {
                output.write(paragraph)
                remaining -= paragraph.length
            }
            output.write("<p>${"x".repeat(remaining - EMPTY_PARAGRAPH_BYTES)}</p>")
            output.write(footer)
        }
        return file
    }

    private companion object {
        const val BOOK_BYTES = 20_000_000
        const val LIBRARY_SIZE = 100
        const val OPEN_RUNS = 3
        const val SWIPES = 12
        const val SWIPE_DURATION_MS = 600L
        const val OPEN_TIMEOUT_MS = 120_000L
        const val NANOS_PER_MS = 1_000_000.0
        const val FRAME_DRAIN_SECONDS = 5L
        const val PARAGRAPH_REPETITIONS = 12
        const val EMPTY_PARAGRAPH_BYTES = 7
        const val SWIPE_START = 0.8f
        const val SWIPE_END = 0.2f
    }
}
