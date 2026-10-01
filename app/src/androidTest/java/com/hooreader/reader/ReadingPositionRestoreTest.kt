package com.hooreader.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ReadingPositionRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun epubPositionSurvivesRecreatedReaderAndDatabaseWithoutOriginal() = verifyRestore("structured.epub")

    @Test
    fun fb2PositionSurvivesRecreatedReaderAndDatabaseWithoutOriginal() = verifyRestore("structured.fb2")

    // No retained ViewModel, repository, parser or database is shared across the two sessions.
    // A separate device acceptance run exercises actual OS process death.
    private fun verifyRestore(asset: String) = runBlocking {
        val databaseName = "restore-${UUID.randomUUID()}.db"
        val files = BookFileStorage(context)
        val source = File(context.cacheDir, "${UUID.randomUUID()}-$asset")
        assets.open("books/$asset").use { input -> source.outputStream().use(input::copyTo) }
        var database = openDatabase(databaseName)
        var store = ViewModelStore()
        var importedId: String? = null
        try {
            var repository = BookRepository(database, files)
            val parsers = listOf(EpubBookParser(), Fb2BookParser())
            val result = BookImportService(files, repository, parsers).import(asset) { source.inputStream() }
            val book = (result as BookImportResult.Added).book
            importedId = book.id
            val first = createReader(store, book.id, repository)
            awaitReading(first)
            withContext(Dispatchers.Main) { first.selectChapter(1) }
            withTimeout(TIMEOUT_MS) {
                first.state.filterIsInstance<ReaderUiState.Reading>().first { it.position.chapterIndex == 1 }
            }
            withContext(Dispatchers.Main) { first.onVisibleBlock(1, 1, 4) }
            first.flushPosition()
            val saved = requireNotNull(repository.getPosition(book.id))
            assertEquals(1, saved.chapterIndex)
            assertEquals(1, saved.blockIndex)
            assertEquals(4, saved.characterOffset)
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
            assertTrue(source.delete())

            database = openDatabase(databaseName)
            repository = BookRepository(database, files)
            store = ViewModelStore()
            val second = createReader(store, book.id, repository)
            val restored = awaitReading(second)
            assertEquals(saved.chapterIndex, restored.position.chapterIndex)
            assertEquals(saved.blockIndex, restored.position.blockIndex)
            assertEquals(saved.characterOffset, restored.position.characterOffset)
            assertEquals(saved.progressPercent, restored.position.progressPercent, 0.001)
            assertTrue(restored.blocks.any { it.text.contains("восстановления позиции") })
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            database.close()
            context.deleteDatabase(databaseName)
            importedId?.let { files.deleteBook(it) }
            source.delete()
        }
    }

    private suspend fun createReader(store: ViewModelStore, id: String, repository: BookRepository): ReaderViewModel =
        withContext(Dispatchers.Main) {
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ReaderViewModel(id, repository, listOf(EpubBookParser(), Fb2BookParser())) as T
                }
            )[ReaderViewModel::class.java]
        }

    private suspend fun awaitReading(reader: ReaderViewModel): ReaderUiState.Reading = withTimeout(TIMEOUT_MS) {
        reader.state.filterIsInstance<ReaderUiState.Reading>().first()
    }

    private fun openDatabase(name: String) = Room.databaseBuilder(context, HooReaderDatabase::class.java, name)
        .addCallback(HooReaderDatabase.ValidationCallback).build()

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
