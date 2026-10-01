package com.hooreader.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import com.hooreader.domain.model.Chapter

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ChapterEntity(@Embedded val chapter: Chapter)
