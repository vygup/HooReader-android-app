package com.hooreader.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import com.hooreader.domain.model.ReadingPosition

@Entity(
    tableName = "reading_positions",
    primaryKeys = ["bookId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ReadingPositionEntity(@Embedded val position: ReadingPosition)
