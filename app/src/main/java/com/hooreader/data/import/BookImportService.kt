package com.hooreader.data.import

import com.hooreader.data.local.BookFileStorage
import com.hooreader.data.repository.AddBookResult
import com.hooreader.data.repository.BookRepository
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookFormat
import com.hooreader.domain.model.BookState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

sealed interface BookImportResult {
    data class Added(val book: Book) : BookImportResult
    data class Duplicate(val book: Book) : BookImportResult
    data class Failed(val reason: BookImportError) : BookImportResult
}

enum class BookImportError { EMPTY, UNSUPPORTED_FORMAT, DRM, CORRUPT, IO }

class BookImportService(
    private val files: BookFileStorage,
    private val repository: BookRepository,
    private val parsers: List<BookParser>,
) {
    private val mutex = Mutex()

    suspend fun import(sourceName: String, openSource: () -> InputStream): BookImportResult = mutex.withLock {
        withContext(Dispatchers.IO) { importLocal(sourceName, openSource) }
    }

    // File staging and parsing happen before one database transaction exposes a READY book.
    @Suppress("TooGenericExceptionCaught") // Third-party parsers may throw unchecked decoding errors.
    private suspend fun importLocal(sourceName: String, openSource: () -> InputStream): BookImportResult {
        val id = UUID.randomUUID().toString()
        var committed = false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val (format, file) = openSource().buffered().use { input ->
                val format = detectBookFormat(input)
                format to files.copyBook(id, format, DigestInputStream(input, digest))
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            repository.findByHash(hash)?.let { return BookImportResult.Duplicate(it) }
            if (format == BookFormat.EPUB) validateEpubProtection(file)
            val parser = parsers.first { it.format == format }
            parser.open(id, file).use { document ->
                val book = makeBook(hash, format, file, sourceName, document)
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    when (val result = repository.addBook(book, document.chapters)) {
                        is AddBookResult.Added -> {
                            committed = true
                            BookImportResult.Added(result.book)
                        }
                        is AddBookResult.Duplicate -> BookImportResult.Duplicate(result.existing)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: BookParseException) {
            BookImportResult.Failed(BookImportError.valueOf(error.reason.name))
        } catch (_: SecurityException) {
            BookImportResult.Failed(BookImportError.IO)
        } catch (_: IOException) {
            BookImportResult.Failed(BookImportError.IO)
        } catch (_: Exception) {
            BookImportResult.Failed(BookImportError.CORRUPT)
        } finally {
            if (!committed) withContext(NonCancellable) { files.deleteBook(id) }
        }
    }

    private suspend fun makeBook(
        hash: String,
        format: BookFormat,
        file: File,
        sourceName: String,
        document: ParsedBook,
    ) = Book(
        id = document.chapters.first().bookId,
        contentHash = hash,
        format = format,
        localPath = file.path,
        title = document.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: sourceName.substringAfterLast('/').ifBlank { "Книга" },
        author = document.metadata.author?.trim()?.takeIf { it.isNotEmpty() } ?: "Неизвестный автор",
        coverPath = copyCover(file, document),
        state = BookState.READY,
    )

    @Suppress("ReturnCount") // Missing or unreadable cover is optional metadata.
    private suspend fun copyCover(file: File, document: ParsedBook): String? {
        val reference = document.metadata.coverRef ?: return null
        val target = File(file.parentFile, "cover")
        return try {
            val source = document.openMedia(reference) ?: return null
            source.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            target.path.takeIf { target.length() > 0 }
        } catch (_: IOException) {
            target.delete()
            null
        }
    }
}

private fun detectBookFormat(input: java.io.BufferedInputStream): BookFormat {
    val probe = ByteArray(PROBE_BYTES)
    input.mark(PROBE_BYTES)
    val size = input.read(probe)
    input.reset()
    if (size < 0) throw BookParseException(BookParseError.EMPTY)
    if (size >= 2 && probe[0] == 'P'.code.toByte() && probe[1] == 'K'.code.toByte()) return BookFormat.EPUB
    val xml = listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).any { charset ->
        String(probe, 0, size, charset).trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith('<')
    }
    if (xml) return BookFormat.FB2
    throw BookParseException(BookParseError.UNSUPPORTED_FORMAT)
}

private const val PROBE_BYTES = 512
