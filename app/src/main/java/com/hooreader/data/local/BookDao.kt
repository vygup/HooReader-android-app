package com.hooreader.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.hooreader.domain.model.Book
import kotlinx.coroutines.flow.Flow

data class LibraryBookRow(@Embedded val book: Book, val progressPercent: Double?)

@Dao
interface BookDao {
    @Insert
    suspend fun insert(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :hash")
    suspend fun findByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books WHERE state = 'READY' ORDER BY title COLLATE NOCASE, id")
    fun observeReady(): Flow<List<BookEntity>>

    @Query(
        "SELECT books.*, reading_positions.progressPercent FROM books " +
            "LEFT JOIN reading_positions ON books.id = reading_positions.bookId " +
            "WHERE books.state = 'READY' ORDER BY books.title COLLATE NOCASE, books.id",
    )
    fun observeLibrary(): Flow<List<LibraryBookRow>>

    @Query("UPDATE books SET state = 'READY' WHERE id = :id AND state = 'IMPORTING'")
    suspend fun markReady(id: String): Int

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
