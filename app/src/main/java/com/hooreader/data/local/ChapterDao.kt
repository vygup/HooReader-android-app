package com.hooreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChapterDao {
    @Insert
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index`")
    suspend fun getForBook(bookId: String): List<ChapterEntity>
}
