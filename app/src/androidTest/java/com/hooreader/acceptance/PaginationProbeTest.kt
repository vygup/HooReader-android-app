package com.hooreader.acceptance

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Debug
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.data.repository.ReaderContentRepository
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.pagination.ProbePageResult
import com.hooreader.pagination.ProductionPaginationProbe
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Opt-in; emulator data exercises the protocol, never certifies a physical-device time budget. */
class PaginationProbeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val args = InstrumentationRegistry.getArguments()
    private val files = BookFileStorage(context)
    private val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
        .addCallback(HooReaderDatabase.ValidationCallback).build()
    private val books = BookRepository(database, files)
    private var store = ViewModelStore()
    private var reader: ReaderViewModel? = null
    private val repository = ReaderContentRepository(
        files,
        BookContentIndexStore(files),
        listOf(EpubBookParser(), Fb2BookParser())
    )

    @Test
    fun measureFiveRuns() = runBlocking {
        assumeTrue(args.getString("phase2Pagination") == "true")
        val book = fixture()
        val raw = JSONArray()
        val mode = requireNotNull(args.getString("probeCache"))
        val profile = requireNotNull(args.getString("probeProfileId"))
        val scale = requireNotNull(args.getString("probeScale")).toFloat()
        configureOrientation()
        assertEquals(
            requireNotNull(args.getString("probeSystemFont")).toFloat(),
            context.resources.configuration.fontScale,
            FONT_TOLERANCE
        )
        try {
            if (mode != "cold_source") {
                // Source preparation is allowed for cold pages; target layout is measured only for warm.
                if (mode == "warm") show(book, scale) else repository.open(book).close()
                clearContent()
            }
            repeat(RUNS) { number ->
                if (mode != "warm") clearDerived(book, "pages")
                if (mode == "cold_source") clearDerived(book, "content")
                raw.put(measure(book, scale, number))
                clearContent()
            }
            val times = (0 until raw.length()).map { raw.getJSONObject(it).getDouble("readyFrameMs") }.sorted()
            val report = JSONObject().put("profileId", profile).put("case", args.getString("probeCase"))
                .put("cache", mode).put("runs", raw).put("minMs", times.first())
                .put("medianMs", times[RUNS / 2]).put("maxMs", times.last())
                .put(
                    "status",
                    if (args.getString("probePhysical") == "true") {
                        if (times.last() <= READY_BUDGET_MS) "PASS" else "FAIL"
                    } else {
                        "NOT_VERIFIED_DEVICE"
                    }
                )
            File(context.filesDir, "pagination-probe-series.json").writeText(report.toString(2))
        } finally {
            clearContent()
            books.deleteBook(book.id)
        }
    }

    @Test
    fun cancelObsoleteLayout() = runBlocking {
        assumeTrue(args.getString("phase2Pagination") == "true")
        val book = fixture()
        try {
            run {
                val model = newReader(book, 1f)
                val currentScale = mutableStateOf(1f)
                val result = AtomicReference<ProbePageResult>()
                val seen = mutableListOf<Float>()
                val started = AtomicReference<Float>()
                val cancelled = AtomicReference<Float>()
                val cancelRequestedAt = AtomicLong()
                val cancelledAt = AtomicLong()
                compose.runOnUiThread {
                    ReaderTestActivity.content = {
                        HooReaderTheme {
                            ProductionPaginationProbe(
                                model, currentScale.value,
                                onStarted = { started.set(it.typography.readingScale) },
                                onCancelled = {
                                    cancelledAt.set(SystemClock.elapsedRealtimeNanos())
                                    cancelled.set(it.typography.readingScale)
                                }
                            ) {
                                seen += it.key.typography.readingScale
                                result.set(it)
                            }
                        }
                    }
                }
                compose.waitUntil(TIMEOUT_MS) { started.get() == 1f }
                compose.runOnUiThread {
                    cancelRequestedAt.set(SystemClock.elapsedRealtimeNanos())
                    currentScale.value = 2f
                }
                compose.waitUntil(TIMEOUT_MS) { result.get()?.key?.typography?.readingScale == 2f }
                assertTrue(seen.all { it == 2f })
                assertEquals(1f, cancelled.get())
                clearContent()
                val pages = files.derivedDirectory(book.id, "pages", "inspection").parentFile!!
                assertTrue(pages.walkTopDown().none { it.name.endsWith(".part") })
                File(context.filesDir, "pagination-probe-cancellation.json").writeText(
                    JSONObject().put("profileId", args.getString("probeProfileId"))
                        .put("obsoletePublished", false).put("stagingFilesRemain", false)
                        .put("cancellationMs", (cancelledAt.get() - cancelRequestedAt.get()) / NANOS_PER_MS)
                        .put("newLayoutHash", result.get().layoutHash).toString(2)
                )
            }
        } finally {
            clearContent()
            books.deleteBook(book.id)
        }
    }

    private suspend fun measure(book: Book, scale: Float, number: Int): JSONObject {
        val sourcePresent = files.derivedDirectory(book.id, "content", "${book.contentHash}-1")
            .resolve("index.json").isFile
        val pagesPresent = files.derivedDirectory(book.id, "pages", "inspection").parentFile!!.exists()
        assertEquals(args.getString("probeCache") != "cold_source", sourcePresent)
        assertEquals(args.getString("probeCache") == "warm", pagesPresent)
        val memory = ProbeMemorySampler()
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            val ready = show(book, scale)
            assertTrue(ready.exactPageNumber > 0)
            val model = requireNotNull(reader)
            val metrics = memory.snapshot()
            return JSONObject().put("run", number + 1)
                .put("openingMs", (model.sourceOpenedAtNanos - start) / NANOS_PER_MS)
                .put("sourceSpoolPresentAtStart", sourcePresent).put("pageIndexPresentAtStart", pagesPresent)
                .put("firstReadableFrameMs", (ready.readyFrameAtNanos - start) / NANOS_PER_MS)
                .put("exactNumberByMs", (ready.exactNumberAtNanos - start) / NANOS_PER_MS)
                .put("readyFrameMs", (ready.readyFrameAtNanos - start) / NANOS_PER_MS)
                .put("pageNumber", ready.exactPageNumber).put("layoutHash", ready.layoutHash)
                .put("sourcePasses", ready.sourcePasses).put("residentFragments", ready.residentFragments)
                .put("mediaSourcePasses", model.mediaSourcePassCount)
                .put("totalSourceOperations", ready.sourcePasses + model.mediaSourcePassCount)
                .put("layoutCacheCapacity", ready.layoutCacheCapacity).put("contentWindowCapacity", 128)
                .put("memory", metrics).put("eofKnown", ready.eofKnown)
                .put("pipeline", "ReaderViewModel/ReaderScreen/PageIndexStore")
        } finally {
            memory.stop()
        }
    }

    private fun show(book: Book, scale: Float): ProbePageResult {
        val result = AtomicReference<ProbePageResult>()
        val model = newReader(book, scale)
        val anchor = LogicalAnchor(requireNotNull(args.getString("probeChapter")).toInt(), 0, 0)
        compose.runOnUiThread {
            ReaderTestActivity.content = {
                HooReaderTheme { ProductionPaginationProbe(model, scale, onReady = result::set) }
            }
        }
        compose.waitUntil(TIMEOUT_MS) { result.get() != null }
        return result.get().also {
            assertTrue(it.page.startAnchor <= anchor && anchor < it.page.endAnchorExclusive)
        }
    }

    private fun newReader(book: Book, scale: Float): ReaderViewModel {
        lateinit var model: ReaderViewModel
        compose.runOnUiThread {
            store = ViewModelStore()
            model = ReaderViewModel(
                book.id,
                books,
                listOf(EpubBookParser(), Fb2BookParser()),
                initialPreferences = ReaderPreferences(fontScale = scale, readingMode = ReadingMode.PAGINATED),
                content = repository,
            )
            store.put("probe-reader", model)
            reader = model
        }
        return model
    }

    private fun clearContent() {
        val disposed = reader
        compose.runOnUiThread {
            ReaderTestActivity.content = null
            store.clear()
            reader = null
        }
        compose.waitUntil(TIMEOUT_MS) { disposed?.pagination?.calculationInProgress != true }
        compose.waitForIdle()
    }

    private fun configureOrientation() {
        val landscape = args.getString("probeOrientation") == "landscape"
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        val expected = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        compose.waitUntil(TIMEOUT_MS) { compose.activity.resources.configuration.orientation == expected }
    }

    private suspend fun fixture(): Book {
        val format = BookFormat.valueOf(requireNotNull(args.getString("probeFormat")).uppercase())
        val source = File(context.cacheDir, "pagination-probe/reader-20mb.${format.name.lowercase()}")
        require(source.length() >= MIN_BOOK_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(hash == args.getString("probeExpectedHash"))
        val id = UUID.randomUUID().toString()
        val local = files.copyBook(id, format, source.inputStream())
        val book = Book(id, hash, format, local.path, "Probe", "HooReader", state = BookState.READY)
        repository.open(book).use { books.addBook(book, it.chapters) }
        books.savePosition(ReadingPosition(id, chapterIndex = requireNotNull(args.getString("probeChapter")).toInt()))
        return book
    }

    private fun clearDerived(book: Book, category: String) {
        val root = files.derivedDirectory(book.id, category, "inspection").parentFile!!
        files.requireOwnedPath(book.id, root.path)
        check(!root.exists() || root.deleteRecursively())
    }

    @After
    fun closeDatabase() {
        clearContent()
        database.close()
    }

    private companion object {
        const val RUNS = 5
        const val TIMEOUT_MS = 180_000L
        const val MIN_BOOK_BYTES = 20_000_000L
        const val NANOS_PER_MS = 1_000_000.0
        const val READY_BUDGET_MS = 1000.0
        const val FONT_TOLERANCE = 0.01f
    }
}

private class ProbeMemorySampler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var peakPssKb = 0L

    @Volatile private var peakHeapBytes = 0L

    @Volatile private var peakNativeBytes = 0L
    private val job = scope.launch {
        while (isActive) {
            peakPssKb = maxOf(peakPssKb, Debug.getPss())
            peakHeapBytes = maxOf(peakHeapBytes, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
            peakNativeBytes = maxOf(peakNativeBytes, Debug.getNativeHeapAllocatedSize())
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    fun snapshot(): JSONObject = JSONObject().put("sampledPeakPssKb", peakPssKb)
        .put("sampledPeakHeapBytes", peakHeapBytes).put("sampledPeakNativeBytes", peakNativeBytes)
        .put("residentPssKb", Debug.getPss()).put("samplingIntervalMs", SAMPLE_INTERVAL_MS)

    suspend fun stop() {
        job.cancelAndJoin()
        scope.cancel()
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 16L
    }
}
