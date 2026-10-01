package com.hooreader.data.local

import androidx.room.TypeConverter
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState

class BookConverters {
    @TypeConverter
    fun formatToString(format: BookFormat): String = format.name

    @TypeConverter
    fun stringToFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun stateToString(state: BookState): String = state.name

    @TypeConverter
    fun stringToState(value: String): BookState = BookState.valueOf(value)
}
