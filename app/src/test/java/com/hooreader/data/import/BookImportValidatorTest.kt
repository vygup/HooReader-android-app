package com.hooreader.data.import

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookImportValidatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private lateinit var database: HooReaderDatabase
    private lateinit var repository: BookRepository
    private lateinit var importer: BookImportService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).build()
        repository = BookRepository(database, files)
        importer = BookImportService(files, repository, listOf(EpubBookParser(context), Fb2BookParser()))
    }

    @After
    fun tearDown() = runBlocking {
        repository.books.first().forEach { repository.deleteBook(it.id) }
        database.close()
    }

    @Test
    fun `empty files are rejected before parsing`() = runBlocking {
        assertRejected("empty.epub", BookImportError.EMPTY)
        assertRejected("empty.fb2", BookImportError.EMPTY)
    }

    @Test
    fun `unsupported content cannot be disguised with a supported extension`() = runBlocking {
        assertRejected("unsupported.pdf", BookImportError.UNSUPPORTED_FORMAT)
        val result = importer.import("pretend.epub") { context.assets.open("books/unsupported.pdf") }
        assertEquals(BookImportResult.Failed(BookImportError.UNSUPPORTED_FORMAT), result)
        assertNoPartialImport()
    }

    @Test
    fun `broken archives and XML leave no partial import`() = runBlocking {
        assertRejected("corrupt.epub", BookImportError.CORRUPT)
        assertRejected("corrupt.fb2", BookImportError.CORRUPT)
    }

    @Test
    fun `encrypted EPUB is rejected with a DRM reason`() = runBlocking {
        assertRejected("drm-marker.epub", BookImportError.DRM)
    }

    @Test
    fun `provider read errors are recoverable`() = runBlocking {
        assertEquals(
            BookImportResult.Failed(BookImportError.IO),
            importer.import("book.fb2") { throw IOException("Provider unavailable") },
        )
        assertNoPartialImport()
    }

    @Test
    fun `cancellation propagates and rolls back staged files`() = runBlocking {
        var cancelled = false
        try {
            importer.import("book.fb2") { throw CancellationException("Import cancelled") }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertNoPartialImport()
    }

    @Test
    fun `valid files become readable and exact duplicates keep their identity`() = runBlocking {
        for (name in listOf("structured.epub", "structured.fb2", "windows-1251.fb2")) {
            val added = importAsset(name) as BookImportResult.Added
            assertTrue(File(added.book.localPath).isFile)
            assertTrue(repository.getChapters(added.book.id).isNotEmpty())
            assertEquals(BookImportResult.Duplicate(added.book), importAsset(name))
        }
        assertEquals(3, repository.books.first().size)
        assertEquals(3, File(context.filesDir, "books").listFiles().orEmpty().size)
    }

    private suspend fun assertRejected(name: String, reason: BookImportError) {
        assertEquals(BookImportResult.Failed(reason), importAsset(name))
        assertNoPartialImport()
    }

    private suspend fun importAsset(name: String) = importer.import(name) { context.assets.open("books/$name") }

    private suspend fun assertNoPartialImport() {
        assertTrue(repository.books.first().isEmpty())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM books").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertFalse(File(context.filesDir, "books").walkTopDown().any { it.isFile })
    }
}
