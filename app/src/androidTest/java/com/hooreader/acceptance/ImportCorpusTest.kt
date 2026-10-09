package com.hooreader.acceptance

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.hooreader.data.local.PageIndexStore
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ImportCorpusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val parsers = listOf(EpubBookParser(), Fb2BookParser())

    @Test
    fun everyManifestFileIsImportedAndEveryValidChapterOpens() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        val repository = BookRepository(database, BookFileStorage(context))
        val importer = BookImportService(BookFileStorage(context), repository, parsers)
        val manifest = JSONArray(assets.open("books/corpus/manifest.json").bufferedReader().use { it.readText() })
        val results = JSONArray()
        try {
            for (index in 0 until manifest.length()) {
                val entry = manifest.getJSONObject(index)
                verifyIdentity(entry)
                results.put(checkEntry(entry, importer, repository))
            }
            val report = JSONObject().put("files", results)
            File(context.filesDir, "phase7-corpus.json").writeText(report.toString(2))
            assertEquals("Corpus has changed unexpectedly", CORPUS_FILES, results.length())
            assertTrue(
                "See files/phase7-corpus.json for individual failures",
                (0 until results.length()).all { results.getJSONObject(it).getBoolean("passed") },
            )
            assertTrue(repository.books.first().isEmpty())
        } finally {
            compose.runOnUiThread { ReaderTestActivity.content = null }
            repository.books.first().forEach { repository.deleteBook(it.id) }
            database.close()
        }
    }

    private suspend fun verifyIdentity(entry: JSONObject) = withContext(Dispatchers.IO) {
        val bytes = assets.open("books/${entry.getString("file")}").use { it.readBytes() }
        assertEquals(entry.getLong("bytes"), bytes.size.toLong())
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(entry.getString("sha256"), hash)
    }

    private suspend fun checkEntry(
        entry: JSONObject,
        importer: BookImportService,
        repository: BookRepository,
    ): JSONObject {
        val name = entry.getString("file")
        val expected = entry.getString("expected")
        val before = repository.books.first().map { it.id }
        val result = importer.import(name) { assets.open("books/$name") }
        val row = JSONObject().put("file", name).put("sha256", entry.getString("sha256"))
            .put("expected", expected)
        when (result) {
            is BookImportResult.Added -> {
                try {
                    val chapters = JSONObject()
                    for (mode in ReadingMode.entries) {
                        chapters.put(mode.name, openChapters(result.book.id, repository, mode))
                    }
                    row.put("actual", "READY").put("openedChapters", chapters).put("passed", expected == "READY")
                } finally {
                    repository.deleteBook(result.book.id)
                }
            }
            is BookImportResult.Failed -> {
                row.put("actual", result.reason.name).put("passed", expected == result.reason.name)
                assertEquals(before, repository.books.first().map { it.id })
            }
            is BookImportResult.Duplicate -> row.put("actual", "DUPLICATE").put("passed", false)
        }
        return row
    }

    private suspend fun openChapters(bookId: String, repository: BookRepository, mode: ReadingMode): Int {
        val store = ViewModelStore()
        lateinit var reader: ReaderViewModel
        compose.runOnUiThread {
            reader = ReaderViewModel(
                bookId, repository, parsers, PageIndexStore(repository.files),
                ReaderPreferences(readingMode = mode)
            )
            store.put("corpus", reader)
            ReaderTestActivity.content = { HooReaderTheme { ReaderScreen(reader) {} } }
        }
        try {
            compose.waitUntil(OPEN_TIMEOUT_MS) { reader.state.value is ReaderUiState.Reading }
            compose.runOnUiThread { reader.selectChapter(0) }
            val chapters = repository.getChapters(bookId)
            for (chapter in chapters) {
                compose.waitUntil(OPEN_TIMEOUT_MS) {
                    (reader.state.value as? ReaderUiState.Reading)?.position?.chapterIndex == chapter.index
                }
                compose.waitForIdle()
                val tag = if (mode == ReadingMode.VERTICAL) "reader_list" else "reader_pager"
                assertTrue(compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty())
                val state = reader.state.value as ReaderUiState.Reading
                assertEquals(mode, state.effectiveMode)
                if (mode == ReadingMode.VERTICAL) {
                    assertTrue(state.blocks.any { it.text.isNotBlank() })
                } else {
                    val page = requireNotNull(state.pages)
                    page.displayedPage.validateGeometry(page.key)
                    assertTrue(page.displayedPage.globalPageNumber > 0)
                }
                assertTrue(reader.flushPosition())
                if (chapter.index < chapters.lastIndex) {
                    compose.runOnUiThread { reader.selectChapter(chapter.index + 1) }
                }
            }
            return chapters.size
        } finally {
            compose.runOnUiThread {
                ReaderTestActivity.content = null
                store.clear()
            }
            compose.waitForIdle()
        }
    }

    private companion object {
        const val CORPUS_FILES = 26
        const val OPEN_TIMEOUT_MS = 15_000L
    }
}
