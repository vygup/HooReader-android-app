package com.hooreader.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.BookMetadata
import com.hooreader.data.import.BookParser
import com.hooreader.data.import.ParsedBook
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val store = ViewModelStore()
    private val id = UUID.randomUUID().toString()
    private lateinit var database: HooReaderDatabase
    private lateinit var repository: BookRepository
    private var chapters = listOf(Chapter(id, 0, "Long chapter", "one", 1000), Chapter(id, 1, "End", "two", 10))
    private val parser = object : BookParser {
        override val format = BookFormat.FB2
        override suspend fun open(bookId: String, file: File) = object : ParsedBook {
            override val metadata = BookMetadata("Book", "Author")
            override val chapters = this@ReaderViewModelTest.chapters
            override fun blocks(chapterIndex: Int, startBlockIndex: Int) = flow {
                for (index in startBlockIndex until chapters[chapterIndex].blockCount) {
                    emit(ContentBlock(chapterIndex, index, BlockKind.PARAGRAPH, "Paragraph $index"))
                }
            }
            override suspend fun openMedia(reference: String) = null
            override fun close() = Unit
        }
    }

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        repository = BookRepository(database, files)
        val file = files.copyBook(id, BookFormat.FB2, "fixture".byteInputStream())
        repository.addBook(
            Book(id, "a".repeat(64), BookFormat.FB2, file.path, "Book", "Author", state = BookState.READY),
            chapters
        )
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        store.clear()
        database.close()
        files.deleteBook(id)
        Dispatchers.resetMain()
    }

    @Test
    fun `restores the logical block in a bounded window and clamps character offset`() = runBlocking {
        repository.savePosition(ReadingPosition(id, 0, 500, 9999, 50.0))
        val reader = reader()
        val state = awaitReading(reader)
        assertEquals(500, state.position.blockIndex)
        assertEquals("Paragraph 500".length, state.position.characterOffset)
        assertTrue(state.blocks.size <= 128)
        assertTrue(state.blocks.first().blockIndex > 0)
        reader.onVisibleBlock(0, 560, 3)
        val moved = withTimeout(5000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first { it.blocks.first().blockIndex > 436 }
        }
        assertEquals(560, moved.position.blockIndex)
        assertTrue(moved.blocks.size <= 128)
        reader.flushPosition()
        assertEquals(560, repository.getPosition(id)?.blockIndex)
    }

    @Test
    fun `new book starts at the beginning and changing chapter saves its start`() = runBlocking {
        val reader = reader()
        assertEquals(0, awaitReading(reader).position.blockIndex)
        reader.selectChapter(1)
        val changed = withTimeout(5000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first { it.position.chapterIndex == 1 }
        }
        reader.onVisibleBlock(0, 8)
        reader.flushPosition()
        assertEquals(1, repository.getPosition(id)?.chapterIndex)
        assertEquals(0, changed.position.blockIndex)
        assertTrue(changed.position.progressPercent > 90.0)
    }

    @Test
    fun `empty chapter shows a fallback and next chapter remains reachable`() = runBlocking {
        chapters = chapters.map { if (it.index == 0) it.copy(blockCount = 0) else it }
        val reader = reader()
        val state = awaitReading(reader)
        assertEquals(BlockKind.FALLBACK, state.blocks.single { it.chapterIndex == 0 }.kind)
        assertEquals("Paragraph 0", state.blocks.first { it.chapterIndex == 1 }.text)
        assertTrue(state.blocks.size <= 128)
        reader.selectChapter(1)
        val next = withTimeout(5000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first { it.position.chapterIndex == 1 }
        }
        assertEquals("Paragraph 0", next.blocks.first { it.chapterIndex == 1 }.text)
        assertEquals(1, next.position.chapterIndex)
        reader.onVisibleBlock(0, 0)
        assertEquals(0, (reader.state.value as ReaderUiState.Reading).position.chapterIndex)
        reader.onVisibleBlock(1, 0)
        reader.flushPosition()
        assertEquals(1, repository.getPosition(id)?.chapterIndex)
    }

    private fun reader() = ReaderViewModel(id, repository, listOf(parser)).also { store.put("reader", it) }

    private suspend fun awaitReading(reader: ReaderViewModel) = withTimeout(5000) {
        reader.state.filterIsInstance<ReaderUiState.Reading>().first()
    }
}
