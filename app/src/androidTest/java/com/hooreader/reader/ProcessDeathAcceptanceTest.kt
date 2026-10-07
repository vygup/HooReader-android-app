package com.hooreader.reader

import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.domain.model.logicalAnchor
import com.hooreader.navigation.ReaderDependencies
import com.hooreader.testing.ReaderTestActivity
import com.hooreader.ui.reader.ReaderPositionResolver
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

// Run the two phases with scripts/verify-reader-process-death.sh, which kills the process between them.
class ProcessDeathAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ReaderTestActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("process-death-acceptance", Context.MODE_PRIVATE)
    private val dependencies = ReaderDependencies(context)
    private val fixtures = listOf("epub", "fb2")
    private val readerPreferences = ReaderPreferencesRepository(context)

    @Test
    fun seed() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "seed")
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val previous = readerPreferences.preferences.first()
        val previousApp = readerPreferences.appPreferences.first()
        preferences.edit().putString("theme", previous.theme.name).putFloat("fontScale", previous.fontScale)
            .putString("readingMode", previous.readingMode.name)
            .putBoolean("confirmExit", previousApp.confirmReaderExit).commit()
        readerPreferences.setTheme(ReaderTheme.DARK)
        readerPreferences.setFontScale(1.5f)
        val args = InstrumentationRegistry.getArguments()
        readerPreferences.setReadingMode(ReadingMode.valueOf(requireNotNull(args.getString("readingMode"))))
        readerPreferences.setConfirmReaderExit(requireNotNull(args.getString("confirmExit")).toBooleanStrict())
        preferences.edit().putInt("pid", Process.myPid()).commit()
        for (name in fixtures) {
            val source = File(context.cacheDir, "process-death-$name")
            assets.open("books/corpus/reader-appearance.$name").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            val result = dependencies.importer.import("process-death.$name") { source.inputStream() }
            val book = when (result) {
                is BookImportResult.Added -> result.book
                is BookImportResult.Duplicate -> error("Use a dedicated test installation without these fixtures")
                is BookImportResult.Failed -> error("Import failed: ${result.reason}")
            }
            preferences.edit().putString(name, book.id).commit()
            dependencies.readerContent.open(book).use { session ->
                val block = session.readRecords(0).maxBy { it.text.length }
                assertTrue(block.text.length > 100_000)
                val offset = ReaderPositionResolver.safeOffset(block.text, 15_000)
                dependencies.repository.savePosition(
                    ReadingPosition(
                        book.id,
                        block.chapterIndex,
                        block.blockIndex,
                        offset,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                preferences.edit().putInt("$name-chapter", block.chapterIndex).putInt("$name-block", block.blockIndex)
                    .putInt("$name-offset", offset).commit()
            }
            withReader(book.id) { reader ->
                assertTrue((reader.state.value as ReaderUiState.Reading).position.characterOffset > 0)
                assertTrue(reader.flushPosition())
            }
            assertTrue(source.delete())
        }
    }

    @Test
    fun verify() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "verify")
        assertNotEquals(preferences.getInt("pid", 0), Process.myPid())
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        val args = InstrumentationRegistry.getArguments()
        val mode = ReadingMode.valueOf(requireNotNull(args.getString("readingMode")))
        assertEquals(ReaderPreferences(ReaderTheme.DARK, 1.5f, mode), readerPreferences.preferences.first())
        assertEquals(
            args.getString("confirmExit").toBoolean(),
            readerPreferences.appPreferences.first().confirmReaderExit
        )
        for (name in fixtures) {
            val id = requireNotNull(preferences.getString(name, null))
            assertFalse(File(context.cacheDir, "process-death-$name").exists())
            val derived = File(context.filesDir, "books/$id/derived")
            assertFalse("Derived cache must be absent before reopening", derived.exists())
            try {
                withReader(id) { reader ->
                    val state = reader.state.value as ReaderUiState.Reading
                    assertEquals(preferences.getInt("$name-chapter", -1), state.position.chapterIndex)
                    assertEquals(preferences.getInt("$name-block", -1), state.position.blockIndex)
                    assertEquals(preferences.getInt("$name-offset", -1), state.position.characterOffset)
                    assertEquals(mode, state.effectiveMode)
                    assertTrue(derived.exists())
                    if (mode == ReadingMode.PAGINATED) {
                        val page = requireNotNull(state.pages).displayedPage
                        assertTrue(page.startAnchor <= state.logicalPosition)
                        assertTrue(state.logicalPosition < page.endAnchorExclusive)
                        assertTrue(page.globalPageNumber > 0)
                    } else {
                        assertTrue(state.blocks.any { it.text.length > 100_000 })
                    }
                    assertTrue(reader.flushPosition())
                    assertEquals(state.logicalPosition, dependencies.repository.getPosition(id)?.logicalAnchor())
                }
            } finally {
                dependencies.repository.deleteBook(id)
            }
        }
        cleanupOwned()
        Unit
    }

    @Test
    fun clearDerived() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "clearDerived")
        fixtures.forEach { name ->
            val id = requireNotNull(preferences.getString(name, null))
            val book = requireNotNull(dependencies.repository.getBook(id))
            val derived = dependencies.repository.files.requireOwnedPath(id, File(book.localPath).parent + "/derived")
            assertTrue(derived.isDirectory)
            assertTrue(derived.deleteRecursively())
            assertTrue(File(book.localPath).isFile)
            assertTrue(requireNotNull(dependencies.repository.getPosition(id)).characterOffset > 0)
        }
    }

    @Test
    fun cleanup() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "cleanup")
        cleanupOwned()
    }

    private suspend fun cleanupOwned() {
        if (!preferences.contains("theme")) return
        fixtures.forEach { name ->
            preferences.getString(name, null)?.let { dependencies.repository.deleteBook(it) }
            File(context.cacheDir, "process-death-$name").delete()
        }
        preferences.getString("theme", null)?.let { readerPreferences.setTheme(ReaderTheme.valueOf(it)) }
        readerPreferences.setFontScale(preferences.getFloat("fontScale", 1f))
        preferences.getString("readingMode", null)?.let { readerPreferences.setReadingMode(ReadingMode.valueOf(it)) }
        readerPreferences.setConfirmReaderExit(preferences.getBoolean("confirmExit", true))
        preferences.edit().clear().commit()
    }

    private suspend fun withReader(id: String, check: suspend (ReaderViewModel) -> Unit) {
        val store = ViewModelStore()
        val current = readerPreferences.preferences.first()
        val reader = withContext(Dispatchers.Main) {
            ReaderViewModel(
                id,
                dependencies.repository,
                dependencies.parsers,
                dependencies.readerPages,
                current,
                dependencies.readerContent
            ).also {
                store.put("reader", it)
                ReaderTestActivity.content = { HooReaderTheme(true) { ReaderScreen(it, current.fontScale) {} } }
            }
        }
        try {
            val tag = if (current.readingMode == ReadingMode.VERTICAL) "reader_list" else "reader_pager"
            compose.waitUntil(120_000) {
                compose.onAllNodesWithTag(tag)
                    .fetchSemanticsNodes().isNotEmpty() && reader.state.value is ReaderUiState.Reading
            }
            compose.waitForIdle()
            check(reader)
        } finally {
            withContext(Dispatchers.Main) {
                ReaderTestActivity.content = null
                store.clear()
            }
        }
    }
}
