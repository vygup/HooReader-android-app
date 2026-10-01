package com.hooreader.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.local.BookEntity
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.ChapterEntity
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.local.ReadingPositionEntity
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = BookFileStorage(context)
    private lateinit var database: HooReaderDatabase
    private lateinit var repository: BookRepository
    private val createdIds = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HooReaderDatabase::class.java)
            .addCallback(HooReaderDatabase.ValidationCallback)
            .allowMainThreadQueries()
            .build()
        repository = BookRepository(database, files)
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        database.close()
        createdIds.forEach { files.deleteBook(it) }
    }

    @Test
    fun `duplicate hash keeps original book chapters and position`() = runBlocking<Unit> {
        val original = makeBook()
        repository.addBook(original, chapters(original))
        val saved = ReadingPosition(original.id, 1, 2, 3, 50.0)
        repository.savePosition(saved)
        val duplicate = makeBook()
        assertEquals(AddBookResult.Duplicate(original), repository.addBook(duplicate, chapters(duplicate)))
        assertEquals(listOf(original), repository.books.first())
        assertEquals(chapters(original), repository.getChapters(original.id))
        assertEquals(saved, repository.getPosition(original.id))
        assertNull(repository.getBook(duplicate.id))
    }

    @Test
    fun `SQLite enforces hash uniqueness even when bypassing repository`() = runBlocking<Unit> {
        val first = makeBook()
        database.bookDao().insert(BookEntity(first))
        val duplicate = makeBook()
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.bookDao().insert(BookEntity(duplicate)) }
        }
    }

    @Test
    fun `delete cascades chapters and position and removes only owned files`() = runBlocking<Unit> {
        val book = makeBook()
        val source = File(context.cacheDir, "original.fb2").apply { writeText("original") }
        repository.addBook(book, chapters(book))
        repository.savePosition(ReadingPosition(book.id))
        repository.deleteBook(book.id)
        assertNull(repository.getBook(book.id))
        assertTrue(repository.getChapters(book.id).isEmpty())
        assertNull(repository.getPosition(book.id))
        assertFalse(File(book.localPath).exists())
        assertEquals("original", source.readText())
        source.delete()
    }

    @Test
    fun `only one latest position exists and delayed saves cannot rewind it`() = runBlocking<Unit> {
        val book = makeBook()
        repository.addBook(book, chapters(book))
        val latest = ReadingPosition(book.id, 1, 2, 3, 100.0, updatedAt = 2000)
        repository.savePosition(ReadingPosition(book.id, updatedAt = 1000))
        repository.savePosition(latest)
        repository.savePosition(ReadingPosition(book.id, updatedAt = 1500))
        assertEquals(latest, repository.getPosition(book.id))
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reading_positions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun `SQLite rejects invalid coordinates on insert and update`() = runBlocking<Unit> {
        val book = makeBook()
        repository.addBook(book, chapters(book))
        val sql = database.openHelper.writableDatabase
        val invalidValues = listOf(
            arrayOf<Any>(-1, 0, 0, 0.0),
            arrayOf<Any>(0, -1, 0, 0.0),
            arrayOf<Any>(0, 0, -1, 0.0),
            arrayOf<Any>(0, 0, 0, -0.1),
            arrayOf<Any>(0, 0, 0, 100.1),
        )
        invalidValues.forEach { values ->
            assertThrows(SQLiteConstraintException::class.java) {
                sql.execSQL(
                    "INSERT INTO reading_positions VALUES (?, ?, ?, ?, ?, 0)",
                    arrayOf<Any>(book.id, *values),
                )
            }
        }
        repository.savePosition(ReadingPosition(book.id))
        listOf("chapterIndex = -1", "blockIndex = -1", "characterOffset = -1", "progressPercent = 101").forEach {
            assertThrows(SQLiteConstraintException::class.java) {
                sql.execSQL("UPDATE reading_positions SET $it WHERE bookId = ?", arrayOf(book.id))
            }
        }
        assertEquals(0.0, repository.getPosition(book.id)?.progressPercent)
    }

    @Test
    fun `position requires an existing book`() = runBlocking<Unit> {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.readingPositionDao().save(ReadingPositionEntity(ReadingPosition(UUID.randomUUID().toString())))
            }
        }
    }

    @Test
    fun `importing book is hidden until completion and incomplete import cannot be exposed`() = runBlocking<Unit> {
        val book = makeBook(state = BookState.IMPORTING)
        repository.addBook(book, chapters(book))
        assertTrue(repository.books.first().isEmpty())
        repository.completeImport(book.id)
        assertEquals(listOf(book.copy(state = BookState.READY)), repository.books.first())
        val incomplete = makeBook(hash = "b".repeat(64), state = BookState.IMPORTING)
        repository.addBook(incomplete, emptyList())
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.completeImport(incomplete.id) } }
        assertEquals(BookState.IMPORTING, repository.getBook(incomplete.id)?.state)
    }

    @Test
    fun `invalid chapter ownership leaves no partial metadata`() = runBlocking<Unit> {
        val book = makeBook()
        val invalidChapter = Chapter(UUID.randomUUID().toString(), 0, "Chapter", "source", 1)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addBook(book, listOf(invalidChapter)) }
        }
        assertNull(repository.getBook(book.id))
    }

    @Test
    fun `transaction rolls back partial metadata on foreign key failure`() = runBlocking<Unit> {
        val book = makeBook()
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.withTransaction {
                    database.bookDao().insert(BookEntity(book))
                    database.chapterDao().insertAll(
                        listOf(ChapterEntity(Chapter(UUID.randomUUID().toString(), 0, null, "source", 1))),
                    )
                }
            }
        }
        assertNull(repository.getBook(book.id))
    }

    @Test
    fun `book chapters and position survive database reopening`() = runBlocking<Unit> {
        database.close()
        val name = "test-${UUID.randomUUID()}.db"
        fun openDatabase(): HooReaderDatabase = Room.databaseBuilder(context, HooReaderDatabase::class.java, name)
            .addCallback(HooReaderDatabase.ValidationCallback)
            .allowMainThreadQueries()
            .build()
        database = openDatabase()
        repository = BookRepository(database, files)
        try {
            val book = makeBook()
            val position = ReadingPosition(book.id, 1, 2, 3, 50.0)
            repository.addBook(book, chapters(book))
            repository.savePosition(position)
            database.close()
            database = openDatabase()
            repository = BookRepository(database, files)
            assertEquals(book, repository.getBook(book.id))
            assertEquals(chapters(book), repository.getChapters(book.id))
            assertEquals(position, repository.getPosition(book.id))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun makeBook(hash: String = "a".repeat(64), state: BookState = BookState.READY): Book {
        val id = UUID.randomUUID().toString().also(createdIds::add)
        val local = files.copyBook(id, BookFormat.FB2, "fixture".byteInputStream())
        return Book(id, hash, BookFormat.FB2, local.path, "Тестовая книга", "Автор", state = state)
    }

    private fun chapters(book: Book): List<Chapter> = listOf(
        Chapter(book.id, 0, "Первая глава", "first", 10),
        Chapter(book.id, 1, "Вторая глава", "second", 10),
    )
}
