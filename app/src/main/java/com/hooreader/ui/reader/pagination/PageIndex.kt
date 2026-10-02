package com.hooreader.ui.reader.pagination

import com.hooreader.data.local.BookFileStorage
import com.hooreader.domain.model.LogicalAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

/** Exact, completed chapter counts plus random-access page records. No full page list in RAM. */
class PageIndex(private val files: BookFileStorage, private val bookId: String, val key: LayoutKey) {
    private val lock = Mutex()
    private val layoutHash = key.hash
    private val root = files.derivedDirectory(bookId, "pages", layoutHash)

    suspend fun ensureChapter(
        chapter: Int,
        prefixCount: Int,
        build: suspend (suspend (PageSlice) -> Unit) -> Unit,
    ): ChapterPageInfo {
        val measurementContext = currentCoroutineContext().minusKey(kotlinx.coroutines.Job)
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val existing = loadChapter(chapter, prefixCount)
                if (existing != null) {
                    root.setLastModified(System.currentTimeMillis())
                    return@withContext existing
                }
                check(root.mkdirs() || root.isDirectory)
                pruneLayouts()
                val staging = owned(root, "$chapter.${UUID.randomUUID()}.part")
                check(staging.mkdir())
                var writer: ChapterPageWriter? = null
                try {
                    val activeWriter = ChapterPageWriter(staging, key, chapter, prefixCount)
                    writer = activeWriter
                    coroutineScope {
                        val pending = Channel<PageSlice>(PAGE_WRITE_BUFFER)
                        val writing = launch {
                            for (page in pending) {
                                currentCoroutineContext().ensureActive()
                                activeWriter.append(page)
                            }
                        }
                        withContext(measurementContext) {
                            build { page -> pending.send(page) }
                        }
                        pending.close()
                        writing.join()
                    }
                    val info = activeWriter.finish()
                    currentCoroutineContext().ensureActive()
                    val target = chapterDirectory(chapter)
                    check(!target.exists() || target.deleteRecursively())
                    check(staging.renameTo(target))
                    info
                } finally {
                    writer?.close()
                    staging.deleteRecursively()
                }
            }
        }
    }

    suspend fun readPage(info: ChapterPageInfo, localPage: Int): PageSlice = withContext(Dispatchers.IO) {
        require(localPage in 0 until info.pageCount)
        val directory = chapterDirectory(info.chapterIndex)
        RandomAccessFile(owned(directory, "offsets.bin"), "r").use { offsets ->
            offsets.seek(localPage.toLong() * Long.SIZE_BYTES)
            val address = offsets.readLong()
            RandomAccessFile(owned(directory, "pages.bin"), "r").use { pages ->
                require(address >= 0 && address < pages.length())
                pages.seek(address)
                val size = pages.readInt()
                require(size in 1..PageSliceCodec.MAX_BYTES && size <= pages.length() - pages.filePointer)
                val bytes = ByteArray(size)
                pages.readFully(bytes)
                PageSliceCodec.decode(bytes).also {
                    require(it.chapterIndex == info.chapterIndex && it.localPageIndex == localPage)
                    require(it.globalPageNumber == info.prefixCount + localPage + 1)
                    it.validateGeometry(key)
                }
            }
        }
    }

    suspend fun pageContaining(info: ChapterPageInfo, anchor: LogicalAnchor): PageSlice {
        require(anchor.chapterIndex == info.chapterIndex)
        var low = 0
        var high = info.pageCount - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (readPage(info, middle).startAnchor <= anchor) low = middle else high = middle - 1
        }
        return readPage(info, low)
    }

    suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            check(!root.exists() || root.deleteRecursively())
        }
    }

    private fun chapterDirectory(chapter: Int): File {
        require(chapter >= 0)
        return owned(root, chapter.toString())
    }

    private fun owned(directory: File, name: String) = files.requireOwnedPath(bookId, File(directory, name).path)

    @Suppress("TooGenericExceptionCaught") // Derived cache is disposable; cancellation is never converted into a miss.
    private suspend fun loadChapter(chapter: Int, prefixCount: Int): ChapterPageInfo? = try {
        val directory = chapterDirectory(chapter)
        val metadata = owned(directory, "chapter.json")
        require(metadata.length() in 1..MAX_METADATA_BYTES)
        val json = JSONObject(metadata.readText())
        require(json.getBoolean("complete") && json.getString("layoutHash") == layoutHash)
        val info = ChapterPageInfo(json.getInt("chapter"), json.getInt("prefix"), json.getInt("count"))
        require(info.chapterIndex == chapter && info.prefixCount == prefixCount)
        require(owned(directory, "offsets.bin").length() == info.pageCount.toLong() * Long.SIZE_BYTES)
        require(pageDigest(owned(directory, "pages.bin")) == json.getString("pagesHash"))
        require(pageDigest(owned(directory, "offsets.bin")) == json.getString("offsetsHash"))
        info
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun pruneLayouts() {
        val layouts = root.parentFile!!.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("[0-9a-f]{64}")) && it != root }
            .sortedByDescending { it.lastModified() }
        layouts.drop(MAX_LAYOUTS - 1).forEach { directory ->
            files.requireOwnedPath(bookId, directory.path).deleteRecursively()
        }
    }

    private companion object {
        const val MAX_LAYOUTS = 2
        const val MAX_METADATA_BYTES = 8192L
        const val PAGE_WRITE_BUFFER = 1 // producer, consumer and queued page: at most three page slices.
    }
}

data class ChapterPageInfo(val chapterIndex: Int, val prefixCount: Int, val pageCount: Int) {
    init {
        require(chapterIndex >= 0 && prefixCount >= 0 && pageCount > 0)
    }
}

private class ChapterPageWriter(
    private val directory: File,
    private val key: LayoutKey,
    private val chapter: Int,
    private val prefix: Int,
) : java.io.Closeable {
    private val pages = RandomAccessFile(File(directory, "pages.bin"), "rw")
    private val offsets = RandomAccessFile(File(directory, "offsets.bin"), "rw")
    private var count = 0
    private var previousEnd: LogicalAnchor? = null

    fun append(page: PageSlice) {
        require(page.chapterIndex == chapter && page.localPageIndex == count)
        require(page.globalPageNumber == prefix + count + 1)
        require(previousEnd == null || previousEnd == page.startAnchor)
        page.validateGeometry(key)
        val bytes = PageSliceCodec.encode(page)
        offsets.writeLong(pages.filePointer)
        pages.writeInt(bytes.size)
        pages.write(bytes)
        previousEnd = page.endAnchorExclusive
        count++
    }

    suspend fun finish(): ChapterPageInfo {
        require(count > 0)
        pages.fd.sync()
        offsets.fd.sync()
        val metadata = JSONObject().apply {
            put("layoutHash", key.hash)
            put("chapter", chapter)
            put("prefix", prefix)
            put("count", count)
            put("pagesHash", pageDigest(File(directory, "pages.bin")))
            put("offsetsHash", pageDigest(File(directory, "offsets.bin")))
            put("complete", true)
        }
        File(directory, "chapter.json").writeText(metadata.toString())
        return ChapterPageInfo(chapter, prefix, count)
    }

    override fun close() {
        try {
            pages.close()
        } finally {
            offsets.close()
        }
    }
}

private suspend fun pageDigest(file: File): String {
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
