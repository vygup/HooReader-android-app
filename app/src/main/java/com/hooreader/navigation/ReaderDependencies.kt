package com.hooreader.navigation

import android.content.Context
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.repository.BookRepository

class ReaderDependencies(context: Context) {
    private val application = context.applicationContext
    private val files = BookFileStorage(application)
    val repository = BookRepository(HooReaderDatabase.getInstance(application), files)
    val parsers = listOf(EpubBookParser(), Fb2BookParser())
    val importer = BookImportService(files, repository, parsers)
    val contentResolver = application.contentResolver
}
