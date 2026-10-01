package com.hooreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    abstract suspend fun get(bookId: String): ReadingPositionEntity?

    @Upsert
    protected abstract suspend fun upsert(position: ReadingPositionEntity)

    @Transaction
    open suspend fun save(entity: ReadingPositionEntity) {
        val previous = get(entity.position.bookId)
        if (previous == null || previous.position.updatedAt <= entity.position.updatedAt) {
            upsert(entity)
        }
    }
}
