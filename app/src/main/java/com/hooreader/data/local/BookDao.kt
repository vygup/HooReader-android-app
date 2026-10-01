package com.hooreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE books SET state = 'READY' WHERE id = :id AND state = 'IMPORTING'")
    suspend fun markReady(id: String): Int

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
