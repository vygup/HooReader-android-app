package com.hooreader.reader

import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hooreader.data.import.BookImportResult
import com.hooreader.navigation.ReaderDependencies
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// Run the two phases with scripts/verify-reader-process-death.sh, which kills the process between them.
class ProcessDeathAcceptanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("process-death-acceptance", Context.MODE_PRIVATE)
    private val dependencies = ReaderDependencies(context)
    private val fixtures = listOf("structured.epub", "structured.fb2")

    @Test
    fun seed() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "seed")
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        preferences.edit().putInt("pid", Process.myPid()).commit()
        for (name in fixtures) {
            val source = File(context.cacheDir, "process-death-$name")
            assets.open("books/$name").use { input -> source.outputStream().use { input.copyTo(it) } }
            val result = dependencies.importer.import(name) { source.inputStream() }
            val book = when (result) {
                is BookImportResult.Added -> result.book
                is BookImportResult.Duplicate -> result.book
                is BookImportResult.Failed -> error("Import failed: ${result.reason}")
            }
            withReader(book.id) { reader ->
                withContext(Dispatchers.Main) { reader.selectChapter(1) }
                withTimeout(15_000) {
                    reader.state.filterIsInstance<ReaderUiState.Reading>().first { it.position.chapterIndex == 1 }
                }
                withContext(Dispatchers.Main) { reader.onVisibleBlock(1, 1, 4) }
                assertTrue(reader.flushPosition())
            }
            preferences.edit().putString(name, book.id).commit()
            assertTrue(source.delete())
        }
    }

    @Test
    fun verify() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processDeathPhase") == "verify")
        assertNotEquals(preferences.getInt("pid", 0), Process.myPid())
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        for (name in fixtures) {
            val id = requireNotNull(preferences.getString(name, null))
            assertFalse(File(context.cacheDir, "process-death-$name").exists())
            try {
                withReader(id) { reader ->
                    val state = reader.state.value as ReaderUiState.Reading
                    assertEquals(1, state.position.chapterIndex)
                    assertEquals(1, state.position.blockIndex)
                    assertEquals(4, state.position.characterOffset)
                    assertTrue(state.blocks.any { "восстановления позиции" in it.text })
                    assertTrue(reader.flushPosition())
                }
            } finally {
                dependencies.repository.deleteBook(id)
            }
        }
        preferences.edit().clear().commit()
        Unit
    }

    private suspend fun withReader(id: String, check: suspend (ReaderViewModel) -> Unit) {
        val store = ViewModelStore()
        val reader = withContext(Dispatchers.Main) {
            ReaderViewModel(id, dependencies.repository, dependencies.parsers).also { store.put("reader", it) }
        }
        try {
            withTimeout(15_000) { reader.state.filterIsInstance<ReaderUiState.Reading>().first() }
            check(reader)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
