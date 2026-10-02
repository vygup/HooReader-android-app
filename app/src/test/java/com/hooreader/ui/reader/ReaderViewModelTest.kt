package com.hooreader.ui.reader

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
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
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReadingMode
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.pagination.LayoutContentIdentity
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.LayoutTypography
import com.hooreader.ui.reader.pagination.LayoutViewport
import com.hooreader.ui.reader.pagination.TextPaginator
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val store = ViewModelStore()
    private val id = UUID.randomUUID().toString()
    private lateinit var database: HooReaderDatabase
    private lateinit var repository: BookRepository
    private var longParagraph = ""
    private var chapters = listOf(Chapter(id, 0, "Long chapter", "one", 1000), Chapter(id, 1, "End", "two", 10))
    private val parser = object : BookParser {
        override val format = BookFormat.FB2
        override suspend fun open(bookId: String, file: File) = object : ParsedBook {
            override val metadata = BookMetadata("Book", "Author")
            override val chapters = this@ReaderViewModelTest.chapters
            override fun blocks(chapterIndex: Int, startBlockIndex: Int) = flow {
                for (index in startBlockIndex until chapters[chapterIndex].blockCount) {
                    val text = if (chapterIndex == 0 && index == 500 && longParagraph.isNotEmpty()) {
                        longParagraph
                    } else {
                        "Paragraph $index"
                    }
                    emit(ContentBlock(chapterIndex, index, BlockKind.PARAGRAPH, text))
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

    @Test
    fun `actual reader keeps inner anchor through write failure system font and latest retry`() = runBlocking {
        longParagraph = "Кириллица 😀 𝄞 é. ".repeat(8000)
        val offset = ReaderPositionResolver.safeOffset(longParagraph, 15_000)
        repository.savePosition(ReadingPosition(id, 0, 500, offset))
        val failing = AtomicBoolean(true)
        val saver = ReadingPositionSaver(
            save = {
                if (failing.get()) throw IOException("Database unavailable")
                repository.savePosition(it)
            },
        )
        val reader = reader(saver, ReaderPreferences(readingMode = ReadingMode.PAGINATED))
        withTimeout(10_000) { reader.state.filterIsInstance<ReaderUiState.PreparingPages>().first() }
        reader.pagination.configure(environment(1f))
        val first = awaitReading(reader)
        assertEquals(offset, first.position.characterOffset)
        val requested = ReaderPreferences(fontScale = 2f, readingMode = ReadingMode.VERTICAL)
        assertFalse(reader.pagination.applyPreferences(requested, GeometryChangeOrigin.USER_PREFERENCE))
        assertEquals(first.layoutGeneration, (reader.state.value as ReaderUiState.Reading).layoutGeneration)
        reader.pagination.configure(environment(1.5f))
        reader.pagination.configure(environment(2f))
        val changed = withTimeout(10_000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first {
                it.pages?.key?.viewport?.systemFontScale == 2f
            }
        }
        assertEquals(first.logicalPosition, changed.logicalPosition)
        assertTrue(reader.saveFailed.value)
        val nextNumber = requireNotNull(changed.pages).displayedPage.globalPageNumber + 1
        val next = requireNotNull(reader.pagination.loadPage(nextNumber, changed.layoutGeneration))
        reader.pagination.settle(next.slice, changed.layoutGeneration)
        val navigated = withTimeout(10_000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first {
                it.logicalPosition == next.slice.startAnchor
            }
        }
        failing.set(false)
        reader.saveNow()
        val retried = withTimeout(10_000) {
            reader.state.filterIsInstance<ReaderUiState.Reading>().first { it.effectiveMode == ReadingMode.VERTICAL }
        }
        assertTrue(reader.flushPosition())
        assertEquals(navigated.logicalPosition, retried.logicalPosition)
        assertEquals(retried.position, repository.getPosition(id))
        assertFalse(reader.saveFailed.value)
    }

    private fun environment(systemScale: Float): PageMeasurementEnvironment {
        val density = Density(1f, systemScale)
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
        val typography = ReaderTypography(style, style, 1f)
        val key = LayoutKey(
            LayoutContentIdentity("a".repeat(64)),
            LayoutViewport(240, 240, 1f, systemScale, listOf(16f * systemScale, 24f * systemScale)),
            LayoutTypography(1f, "Default", "SDK28", "16/24", "16/24", "Simple", "ru", "Ltr", 24f, 16f),
        )
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 0)
        return PageMeasurementEnvironment(
            key,
            TextPaginator(measurer, typography, density, LayoutDirection.Ltr, "Нет текста"),
        )
    }

    private fun reader(
        saver: ReadingPositionSaver = ReadingPositionSaver(repository::savePosition),
        preferences: ReaderPreferences = ReaderPreferences(),
    ) = ReaderViewModel(id, repository, listOf(parser), initialPreferences = preferences, positionSaver = saver)
        .also { store.put("reader", it) }

    private suspend fun awaitReading(reader: ReaderViewModel) = withTimeout(5000) {
        reader.state.filterIsInstance<ReaderUiState.Reading>().first()
    }
}
