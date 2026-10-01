package com.hooreader.data.repository

import androidx.room.withTransaction
import com.hooreader.data.local.BookEntity
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.ChapterEntity
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.local.ReadingPositionEntity
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.Chapter
import com.hooreader.domain.model.ReadingPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface AddBookResult {
    data class Added(val book: Book) : AddBookResult
    data class Duplicate(val existing: Book) : AddBookResult
}

class BookRepository(
    private val database: HooReaderDatabase,
    private val files: BookFileStorage,
) {
    val books: Flow<List<Book>> = database.bookDao().observeReady().map { entities -> entities.map { it.book } }

    suspend fun getBook(bookId: String): Book? = database.bookDao().get(bookId)?.book

    suspend fun findByHash(hash: String): Book? = database.bookDao().findByHash(hash)?.book

    suspend fun getChapters(bookId: String): List<Chapter> =
        database.chapterDao().getForBook(bookId).map { it.chapter }

    suspend fun addBook(book: Book, chapters: List<Chapter>): AddBookResult {
        require(book.state != BookState.FAILED)
        require(chapters.all { it.bookId == book.id })
        require(chapters.map { it.index } == chapters.indices.toList())
        require(book.state != BookState.READY || chapters.isNotEmpty())
        withContext(Dispatchers.IO) {
            require(files.requireOwnedPath(book.id, book.localPath).isFile)
            book.coverPath?.let { files.requireOwnedPath(book.id, it) }
        }
        return database.withTransaction {
            val existing = findByHash(book.contentHash)
            if (existing != null) {
                AddBookResult.Duplicate(existing)
            } else {
                database.bookDao().insert(BookEntity(book))
                database.chapterDao().insertAll(chapters.map(::ChapterEntity))
                AddBookResult.Added(book)
            }
        }
    }

    suspend fun completeImport(bookId: String) = database.withTransaction {
        check(getChapters(bookId).isNotEmpty()) { "Cannot expose an incomplete import" }
        check(database.bookDao().markReady(bookId) == 1) { "Book must be importing" }
    }

    suspend fun getPosition(bookId: String): ReadingPosition? = database.readingPositionDao().get(bookId)?.position

    suspend fun savePosition(position: ReadingPosition) = database.withTransaction {
        check(getBook(position.bookId)?.state == BookState.READY) { "Book is not ready for reading" }
        database.readingPositionDao().save(ReadingPositionEntity(position))
    }

    suspend fun deleteBook(bookId: String) {
        database.bookDao().delete(bookId)
        files.deleteBook(bookId)
    }
}
