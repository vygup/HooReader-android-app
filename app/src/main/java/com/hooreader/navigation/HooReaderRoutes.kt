package com.hooreader.navigation

import android.net.Uri

object HooReaderRoutes {
    const val LIBRARY = "library"
    const val APP_SETTINGS = "app_settings"
    const val BOOK_ID = "bookId"
    const val READER = "reader/{bookId}"

    fun reader(bookId: String): String {
        require(bookId.isNotBlank())
        return "reader/${Uri.encode(bookId)}"
    }
}
