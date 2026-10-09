package com.hooreader.navigation

import android.content.Context
import com.hooreader.data.import.BookImportService
import com.hooreader.data.import.EpubBookParser
import com.hooreader.data.import.Fb2BookParser
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.local.HooReaderDatabase
import com.hooreader.data.local.PageIndexStore
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.data.repository.BookRepository
import com.hooreader.data.repository.LibraryRepository
import com.hooreader.data.repository.ReaderContentRepository
import com.hooreader.ui.reader.ReadingPositionSaver

class ReaderDependencies(
    context: Context,
    val positionSaverFactory: (BookRepository) -> ReadingPositionSaver = { ReadingPositionSaver(it::savePosition) },
) {
    private val application = context.applicationContext
    private val files = BookFileStorage(application)
    private val database = HooReaderDatabase.getInstance(application)
    val preferences = ReaderPreferencesRepository(application)
    val repository = BookRepository(database, files)
    val library = LibraryRepository(database, files)
    val parsers = listOf(EpubBookParser(), Fb2BookParser())
    val readerContent = ReaderContentRepository(files, BookContentIndexStore(files), parsers)
    val readerPages = PageIndexStore(files)
    val importer = BookImportService(files, repository, parsers)
    val contentResolver = application.contentResolver
}
