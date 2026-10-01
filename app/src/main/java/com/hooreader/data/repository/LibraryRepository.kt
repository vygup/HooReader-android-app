package com.hooreader.data.repository

import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class LibraryBook(val book: Book, val progressPercent: Double = 0.0) {
    val isRead: Boolean get() = progressPercent >= READ_THRESHOLD
}

class LibraryRepository(
    private val database: HooReaderDatabase,
    private val files: BookFileStorage,
) {
    // One LEFT JOIN observes both metadata and position changes, including books never opened.
    val books: Flow<List<LibraryBook>> = database.bookDao().observeLibrary().map { rows ->
        rows.map { row ->
            val cover = row.book.coverPath?.takeIf { path ->
                runCatching { files.requireOwnedPath(row.book.id, path).let { it.isFile && it.length() > 0 } }
                    .getOrDefault(false)
            }
            LibraryBook(row.book.copy(coverPath = cover), row.progressPercent ?: 0.0)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO + NonCancellable) {
        // Keep the record available for retry if local files cannot be removed.
        // Only the UUID directory owned by the app is touched, never the source URI.
        files.deleteBook(bookId)
        database.bookDao().delete(bookId)
    }
}

private const val READ_THRESHOLD = 98.0
