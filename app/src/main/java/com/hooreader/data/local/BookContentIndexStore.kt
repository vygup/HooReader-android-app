package com.hooreader.data.local

import com.hooreader.data.import.ParsedBook
import com.hooreader.domain.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

/** App-private, disposable spool. Only a validated, atomically renamed directory is readable. */
class BookContentIndexStore(
    private val files: BookFileStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val buildLock = Mutex()

    suspend fun ensure(bookId: String, contentHash: String, document: ParsedBook): BookContentIndex =
        buildLock.withLock {
            withContext(ioDispatcher) {
                val target = directory(bookId, contentHash)
                load(target, bookId, contentHash) ?: build(target, bookId, contentHash, document)
            }
        }

    suspend fun readWindow(index: BookContentIndex, startRecord: Int, limit: Int = MAX_WINDOW): List<ContentBlock> =
        withContext(ioDispatcher) {
            require(index.complete && startRecord in 0..index.recordCount && limit in 1..MAX_WINDOW)
            if (startRecord == index.recordCount) return@withContext emptyList()
            val checkpoint = index.checkpoints.last { it.recordIndex <= startRecord }
            val spool = ownedFile(index, "blocks.bin")
            RandomAccessFile(spool, "r").use { input ->
                input.seek(checkpoint.byteOffset)
                repeat(startRecord - checkpoint.recordIndex) {
                    ensureActive()
                    skipRecord(input)
                }
                List(minOf(limit, index.recordCount - startRecord)) { offset ->
                    ensureActive()
                    readRecord(input).also { validateCoordinate(index, startRecord + offset, it) }
                }
            }
        }

    suspend fun invalidate(index: BookContentIndex) = buildLock.withLock {
        withContext(ioDispatcher) {
            val target = directory(index.bookId, index.contentHash)
            check(!target.exists() || target.deleteRecursively())
        }
    }

    private fun directory(bookId: String, hash: String): File {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        return files.derivedDirectory(bookId, "content", "$hash-${BookContentIndex.PARSER_VERSION}")
    }

    private fun ownedFile(index: BookContentIndex, name: String): File =
        files.requireOwnedPath(index.bookId, File(directory(index.bookId, index.contentHash), name).path)

    private suspend fun build(target: File, bookId: String, hash: String, document: ParsedBook): BookContentIndex {
        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        val staging = files.requireOwnedPath(bookId, "${target.path}.${UUID.randomUUID()}.part")
        check(staging.mkdir())
        try {
            val index = writeSpool(staging, bookId, hash, document)
            File(staging, "index.json").writeText(ContentIndexCodec.encode(index))
            check(load(staging, bookId, hash) == index) { "Spool validation failed" }
            currentCoroutineContext().ensureActive()
            check(!target.exists() || target.deleteRecursively())
            check(staging.renameTo(target)) { "Cannot publish content spool" }
            return index
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun writeSpool(
        staging: File,
        bookId: String,
        hash: String,
        document: ParsedBook,
    ): BookContentIndex {
        var ordinal = 0
        val chapters = document.chapters.map {
            ContentChapter(it.index, ordinal, it.blockCount).also { ordinal += it.blockCount }
        }
        val expectedCount = ordinal
        val checkpoints = mutableListOf<ContentCheckpoint>()
        var bytes = 0L
        ordinal = 0
        val spool = File(staging, "blocks.bin")
        FileOutputStream(spool).use { stream ->
            val output = DataOutputStream(stream.buffered())
            document.orderedBlocks().collect { block ->
                currentCoroutineContext().ensureActive()
                val chapter = chapters.first { ordinal in it.firstRecord until it.firstRecord + it.blockCount }
                require(block.chapterIndex == chapter.chapterIndex && block.blockIndex == ordinal - chapter.firstRecord)
                if (ordinal % BookContentIndex.CHECKPOINT_INTERVAL == 0) {
                    checkpoints += ContentCheckpoint(ordinal, bytes)
                }
                val encoded = ContentBlockCodec.encode(block)
                output.writeInt(encoded.size)
                output.write(encoded)
                bytes += Int.SIZE_BYTES + encoded.size
                ordinal++
            }
            output.flush()
            stream.fd.sync()
        }
        require(ordinal == expectedCount)
        return BookContentIndex(
            bookId, hash, BookContentIndex.PARSER_VERSION, BookContentIndex.SPOOL_VERSION,
            chapters, checkpoints, ordinal, bytes, digest(spool), complete = true,
        )
    }

    @Suppress("TooGenericExceptionCaught") // Invalid derived data is rebuilt; cancellation must still propagate.
    private suspend fun load(directory: File, bookId: String, hash: String): BookContentIndex? = try {
        val metadata = files.requireOwnedPath(bookId, File(directory, "index.json").path)
        require(metadata.length() in 1..MAX_METADATA_BYTES)
        val index = ContentIndexCodec.decode(metadata.readText())
        require(index.complete && index.bookId == bookId && index.contentHash == hash)
        require(index.parserVersion == BookContentIndex.PARSER_VERSION)
        require(index.spoolVersion == BookContentIndex.SPOOL_VERSION)
        val spool = files.requireOwnedPath(bookId, File(directory, "blocks.bin").path)
        require(spool.length() == index.spoolBytes && digest(spool) == index.spoolHash)
        verifyRecords(index, spool)
        index
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun verifyRecords(index: BookContentIndex, spool: File) {
        RandomAccessFile(spool, "r").use { input ->
            repeat(index.recordCount) { ordinal ->
                currentCoroutineContext().ensureActive()
                if (ordinal % BookContentIndex.CHECKPOINT_INTERVAL == 0) {
                    require(
                        index.checkpoints[ordinal / BookContentIndex.CHECKPOINT_INTERVAL] ==
                            ContentCheckpoint(ordinal, input.filePointer)
                    )
                }
                validateCoordinate(index, ordinal, readRecord(input))
            }
            require(input.filePointer == input.length())
        }
    }

    private fun validateCoordinate(index: BookContentIndex, ordinal: Int, block: ContentBlock) {
        val chapter = index.chapterDirectory.first { ordinal in it.firstRecord until it.firstRecord + it.blockCount }
        require(block.chapterIndex == chapter.chapterIndex && block.blockIndex == ordinal - chapter.firstRecord)
    }

    companion object {
        const val MAX_WINDOW = 128
        private const val MAX_METADATA_BYTES = 16L * 1024 * 1024
    }
}

private fun readRecord(input: RandomAccessFile): ContentBlock {
    val size = recordSize(input)
    val bytes = ByteArray(size)
    input.readFully(bytes)
    return ContentBlockCodec.decode(bytes)
}

private fun skipRecord(input: RandomAccessFile) {
    val size = recordSize(input)
    input.seek(input.filePointer + size)
}

private fun recordSize(input: RandomAccessFile): Int = input.readInt().also {
    require(it in 1..ContentBlockCodec.MAX_RECORD_BYTES && it <= input.length() - input.filePointer)
}

private suspend fun digest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
