package com.hooreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private lateinit var database: HooReaderDatabase
    private lateinit var books: BookRepository
    private lateinit var importer: BookImportService
    private val importedIds = mutableSetOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback)
            .allowMainThreadQueries()
            .build()
        books = BookRepository(database, files)
        importer = BookImportService(files, books, listOf(EpubBookParser(), Fb2BookParser()))
    }

    @After
    fun tearDown() = runBlocking {
        importedIds.forEach { files.deleteBook(it) }
        database.close()
    }

    @Test
    fun `library observes saved progress and read boundary without reopening`() = runBlocking {
        val book = importBook("missing-metadata.fb2")
        val library = LibraryRepository(database, files)
        assertEquals(0.0, library.books.first().single().progressPercent, 0.0)
        books.savePosition(ReadingPosition(book.id, progressPercent = 97.9, updatedAt = 1))
        assertFalse(library.books.first().single().isRead)
        val observed = CompletableDeferred<LibraryBook>()
        val collector = launch {
            observed.complete(library.books.first { it.single().isRead }.single())
        }
        try {
            books.savePosition(ReadingPosition(book.id, progressPercent = 98.0, updatedAt = 2))
            assertEquals(98.0, observed.await().progressPercent, 0.0)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `library uses placeholder when local cover is missing`() = runBlocking {
        val original = importBook("structured.fb2")
        val copyId = java.util.UUID.randomUUID().toString().also { importedIds += it }
        val local = files.copyBook(copyId, original.format, File(original.localPath).inputStream())
        val copy = original.copy(
            id = copyId,
            contentHash = "f".repeat(64),
            localPath = local.path,
            coverPath = File(local.parentFile, "missing-cover").path,
        )
        books.addBook(copy, books.getChapters(original.id).map { it.copy(bookId = copyId) })
        val entry = LibraryRepository(database, files).books.first().single { it.book.id == copyId }
        assertNull(entry.book.coverPath)
    }

    @Test
    fun `missing metadata uses source filename unknown author and no cover`() = runBlocking {
        val book = importBook("missing-metadata.fb2", "Путешествие.fb2")
        assertEquals("Путешествие.fb2", book.title)
        assertEquals("Неизвестный автор", book.author)
        assertNull(book.coverPath)
        assertEquals(listOf(book), books.books.first())
    }

    @Test
    fun `EPUB without author and cover remains available`() = runBlocking {
        val book = importBook("no-author-cover.epub")
        assertTrue(book.title.isNotBlank())
        assertEquals("Неизвестный автор", book.author)
        assertNull(book.coverPath)
        assertTrue(File(book.localPath).isFile)
    }

    @Test
    fun `duplicate under another filename keeps original metadata and progress`() = runBlocking {
        val book = importBook("missing-metadata.fb2", "Первая.fb2")
        val position = ReadingPosition(book.id, progressPercent = 98.0)
        books.savePosition(position)
        val duplicate = importer.import("Другая.fb2") { fixture("missing-metadata.fb2") }
        assertEquals(BookImportResult.Duplicate(book), duplicate)
        assertEquals(listOf(book), books.books.first())
        assertEquals(position, books.getPosition(book.id))
        val directory = File(book.localPath).parentFile!!.parentFile!!
        assertEquals(setOf(book.id), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun `removal deletes local cover chapters and progress but preserves source`() = runBlocking {
        val source = File(context.cacheDir, "library-original.fb2").apply {
            fixture("structured.fb2").use { input -> outputStream().use(input::copyTo) }
        }
        try {
            val result = importer.import(source.name, source::inputStream) as BookImportResult.Added
            val book = result.book.also { importedIds += it.id }
            books.savePosition(ReadingPosition(book.id, progressPercent = 40.0))
            val cover = File(File(book.localPath).parentFile, "extra-cover").apply { writeText("cover") }
            LibraryRepository(database, files).deleteBook(book.id)
            assertTrue(books.books.first().isEmpty())
            assertTrue(books.getChapters(book.id).isEmpty())
            assertNull(books.getPosition(book.id))
            assertFalse(File(book.localPath).exists())
            assertFalse(cover.exists())
            assertTrue(source.isFile)
            assertTrue(source.readText().contains("FictionBook"))
        } finally {
            source.delete()
        }
    }

    private suspend fun importBook(fixture: String, name: String = fixture) =
        (importer.import(name) { fixture(fixture) } as BookImportResult.Added).book.also { importedIds += it.id }

    private fun fixture(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream("books/$name"))
}
