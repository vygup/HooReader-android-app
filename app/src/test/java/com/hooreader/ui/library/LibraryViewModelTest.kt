package com.hooreader.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.import.BookImportError
import com.hooreader.data.import.BookImportResult
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository
import com.hooreader.data.repository.LibraryRepository
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private val store = ViewModelStore()
    private lateinit var database: HooReaderDatabase
    private lateinit var books: BookRepository
    private lateinit var library: LibraryRepository
    private val ids = mutableListOf<String>()
    private val modelJobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback).allowMainThreadQueries().build()
        books = BookRepository(database, files)
        library = LibraryRepository(database, files)
    }

    @After
    fun tearDown() = runBlocking {
        store.clear()
        modelJobs.joinAll()
        database.close()
        ids.forEach { files.deleteBook(it) }
        Dispatchers.resetMain()
    }

    @Test
    fun `empty library imports once and recovers from failed import`() = runBlocking {
        val result = CompletableDeferred<BookImportResult>()
        var calls = 0
        val model = model {
            calls++
            result.await()
        }
        await(model, LibraryStatus.EMPTY)
        model.import(Uri.EMPTY)
        model.import(Uri.EMPTY)
        assertEquals(LibraryStatus.IMPORTING, model.state.value.status)
        assertEquals(1, calls)
        result.complete(BookImportResult.Failed(BookImportError.EMPTY))
        await(model, LibraryStatus.ERROR)
        model.dismissResult()
        await(model, LibraryStatus.EMPTY)
        assertNull(model.state.value.importResult)
    }

    @Test
    fun `duplicate result keeps visible book and can be dismissed`() = runBlocking {
        val book = book()
        val model = model { BookImportResult.Duplicate(book) }
        await(model, LibraryStatus.CONTENT)
        model.import(Uri.EMPTY)
        assertEquals(BookImportResult.Duplicate(book), model.state.value.importResult)
        assertEquals(listOf(book.id), model.state.value.books.map { it.book.id })
        model.dismissResult()
        assertNull(model.state.value.importResult)
    }

    @Test
    fun `confirmed deletion updates library to empty`() = runBlocking {
        val book = book()
        val model = model { BookImportResult.Duplicate(book) }
        await(model, LibraryStatus.CONTENT)
        model.deleteBook(book.id)
        await(model, LibraryStatus.EMPTY)
        assertNull(books.getBook(book.id))
        assertNull(model.state.value.deletingBookId)
    }

    @Test
    fun `source IO error keeps library available and allows another import`() = runBlocking {
        val book = book()
        var calls = 0
        val model = model {
            if (calls++ == 0) throw java.io.IOException("Unavailable source")
            BookImportResult.Duplicate(book)
        }
        await(model, LibraryStatus.CONTENT)
        model.import(Uri.EMPTY)
        await(model, LibraryStatus.ERROR)
        assertEquals(1, model.state.value.books.size)
        model.import(Uri.EMPTY)
        await(model, LibraryStatus.CONTENT)
        assertEquals(BookImportResult.Duplicate(book), model.state.value.importResult)
    }

    private fun model(import: suspend (Uri) -> BookImportResult) =
        LibraryViewModel(library, import).also {
            store.put("library", it)
            modelJobs += it.viewModelScope.coroutineContext.job
        }

    private suspend fun await(model: LibraryViewModel, status: LibraryStatus) = withTimeout(5_000) {
        model.state.first { it.status == status && it.deletingBookId == null }
    }

    private suspend fun book(): Book {
        val id = UUID.randomUUID().toString().also(ids::add)
        val local = files.copyBook(id, BookFormat.FB2, "book".byteInputStream())
        val book = Book(id, "a".repeat(64), BookFormat.FB2, local.path, "Книга", "Автор", state = BookState.READY)
        books.addBook(book, listOf(Chapter(id, 0, null, "chapter", 1)))
        return book
    }
}
