package com.hooreader.data.local

import android.content.Context
import com.hooreader.domain.model.BookFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BookFileStorage(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val root = File(context.applicationContext.filesDir, "books").canonicalFile

    suspend fun copyBook(bookId: String, format: BookFormat, source: InputStream): File = withContext(ioDispatcher) {
        source.use { input ->
            val directory = directory(bookId)
            check(directory.mkdirs() || directory.isDirectory)
            val target = File(directory, "book.${format.name.lowercase()}")
            check(!target.exists()) { "Book file already exists" }
            val staging = File.createTempFile("import-", ".part", directory)
            try {
                staging.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                check(staging.renameTo(target)) { "Cannot commit book file" }
                target
            } finally {
                staging.delete()
            }
        }
    }

    fun requireOwnedPath(bookId: String, path: String): File {
        val file = File(path).canonicalFile
        require(file.toPath().startsWith(directory(bookId).toPath()) && file != directory(bookId)) {
            "File must belong to app-specific book storage"
        }
        return file
    }

    suspend fun deleteBook(bookId: String) = withContext(ioDispatcher) {
        val directory = directory(bookId)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Cannot remove local book data")
        }
        Unit
    }

    private fun directory(bookId: String): File {
        require(UUID.fromString(bookId).toString() == bookId)
        val directory = File(root, bookId).canonicalFile
        require(directory.parentFile == root)
        return directory
    }
}
