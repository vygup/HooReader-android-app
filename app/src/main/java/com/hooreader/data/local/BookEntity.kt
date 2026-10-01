package com.hooreader.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import com.hooreader.domain.model.Book

@Entity(tableName = "books", primaryKeys = ["id"], indices = [Index(value = ["contentHash"], unique = true)])
data class BookEntity(@Embedded val book: Book)
