package com.hooreader.data.repository

import android.graphics.BitmapFactory
import com.hooreader.data.import.BookParser
import com.hooreader.data.import.ParsedBook
import com.hooreader.data.local.BookContentIndex
import com.hooreader.data.local.BookContentIndexStore
import com.hooreader.data.local.BookFileStorage
import com.hooreader.domain.model.Book
import com.hooreader.domain.model.BookState
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class ReaderContentRepository(
    private val files: BookFileStorage,
    private val store: BookContentIndexStore,
    private val parsers: List<BookParser>,
) {
    suspend fun open(book: Book): ReaderContentSession = withContext(Dispatchers.IO) {
        require(book.state == BookState.READY)
        val source = files.requireOwnedPath(book.id, book.localPath)
        val document = parsers.first { it.format == book.format }.open(book.id, source)
        var opened = false
        try {
            ReaderContentSession(book, document, store.ensure(book.id, book.contentHash, document), store, files)
                .also { opened = true }
        } finally {
            if (!opened) document.close()
        }
    }
}

/** Retains directory metadata and at most one caller's 128-block window, never the full book text. */
class ReaderContentSession internal constructor(
    val book: Book,
    val document: ParsedBook,
    private var index: BookContentIndex,
    private val store: BookContentIndexStore,
    private val files: BookFileStorage,
) : Closeable {
    val chapters get() = document.chapters
    val recordCount get() = index.recordCount
    val sourcePassCount get() = document.sourcePassCount
    private val lock = Mutex()
    private val images = object : LinkedHashMap<String, LocalImageMetrics>(IMAGE_CACHE_SIZE, LRU_LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LocalImageMetrics>?): Boolean =
            size > IMAGE_CACHE_SIZE
    }

    fun recordIndex(chapterIndex: Int, blockIndex: Int): Int {
        val chapter = index.chapterDirectory[chapterIndex]
        require(blockIndex in 0..chapter.blockCount)
        return chapter.firstRecord + blockIndex
    }

    suspend fun window(chapterIndex: Int, blockIndex: Int): List<ContentBlock> {
        val record = recordIndex(chapterIndex, blockIndex)
        return readRecords((record - BookContentIndexStore.MAX_WINDOW / 2).coerceAtLeast(0))
    }

    @Suppress("TooGenericExceptionCaught") // Missing/corrupt derived records are recreated; source remains immutable.
    suspend fun readRecords(start: Int, limit: Int = BookContentIndexStore.MAX_WINDOW): List<ContentBlock> =
        lock.withLock {
            try {
                store.readWindow(index, start, limit)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                store.invalidate(index)
                index = store.ensure(book.id, book.contentHash, document)
                store.readWindow(index, start, limit)
            }
        }

    suspend fun imageMetrics(reference: String): LocalImageMetrics = lock.withLock {
        images[reference] ?: withContext(Dispatchers.IO) {
            readImage(reference).also { images[reference] = it }
        }
    }

    suspend fun openMedia(reference: String): InputStream? {
        val metrics = imageMetrics(reference)
        return withContext(Dispatchers.IO) { metrics.localPath?.let { File(it).inputStream() } }
    }

    @Suppress("TooGenericExceptionCaught") // Bad embedded media gets fixed fallback geometry.
    private suspend fun readImage(reference: String): LocalImageMetrics = try {
        val directory = files.derivedDirectory(book.id, "content", "${book.contentHash}-1")
        check(directory.mkdirs() || directory.isDirectory)
        val key = MessageDigest.getInstance("SHA-256").digest(reference.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val target = files.requireOwnedPath(book.id, File(directory, "$key.media").path)
        if (!target.exists()) cacheMedia(reference, target)
        require(target.isFile && target.length() > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.path, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            LocalImageMetrics(bounds.outWidth, bounds.outHeight, target.path)
        } else {
            LocalImageMetrics.FALLBACK
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        LocalImageMetrics.FALLBACK
    }

    private suspend fun cacheMedia(reference: String, target: File) {
        val staging = File.createTempFile("media-", ".part", target.parentFile)
        try {
            val input = document.openMedia(reference) ?: return
            input.use {
                staging.outputStream().use { output ->
                    copyMedia(input, output)
                }
            }
            currentCoroutineContext().ensureActive()
            check(staging.renameTo(target))
        } finally {
            staging.delete()
        }
    }

    override fun close() = document.close()

    private companion object {
        const val IMAGE_CACHE_SIZE = 32
        const val LRU_LOAD_FACTOR = 0.75f
    }
}

private suspend fun copyMedia(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
}

data class LocalImageMetrics(val width: Int, val height: Int, val localPath: String?) {
    init {
        require(width > 0 && height > 0)
    }

    val isFallback get() = localPath == null

    companion object {
        val FALLBACK = LocalImageMetrics(1, 1, null)
    }
}
