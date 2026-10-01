package com.hooreader.reader

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderUiState
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

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
            compose.setContent { HooReaderTheme { ReaderScreen(reader) {} } }
            compose.waitUntil(15_000) { reader.state.value is ReaderUiState.Reading }
            compose.onNodeWithTag("reader_list").assertIsDisplayed()
            compose.onNodeWithText("Кириллица, café — «текст».").assertIsDisplayed()
            compose.onNodeWithText("Следующая глава").performClick()
            compose.waitUntil(15_000) {
                (reader.state.value as? ReaderUiState.Reading)?.position?.chapterIndex == 1
            }
            compose.onNodeWithText("Абзац для восстановления позиции.").assertIsDisplayed()
            reader.flushPosition()
            assertEquals(1, repository.getPosition(added.book.id)?.chapterIndex)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            repository.deleteBook(added.book.id)
            database.close()
        }
    }
}
