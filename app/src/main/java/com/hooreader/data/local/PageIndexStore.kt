package com.hooreader.data.local

import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.ui.reader.pagination.ChapterPageInfo
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.PageIndex
import com.hooreader.ui.reader.pagination.PageSlice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Serial owner of completed, exact chapter prefixes. It never reads or changes Room positions. */
class PageIndexStore(private val files: BookFileStorage) {
    private val lock = Mutex()

    suspend fun prepareThrough(
        bookId: String,
        key: LayoutKey,
        sourceChapterCount: Int,
        throughChapter: Int,
        build: suspend (Int, Int, suspend (PageSlice) -> Unit) -> Unit,
    ): PagePrefix = lock.withLock {
        require(sourceChapterCount > 0 && throughChapter in 0 until sourceChapterCount)
        val existing = loadPrefix(bookId, key, sourceChapterCount)
        val wanted = maxOf(throughChapter + 1, existing?.completedPrefix ?: 0)
        val index = PageIndex(files, bookId, key)
        val chapters = mutableListOf<ChapterPageInfo>()
        var pageCount = 0
        var changed = existing == null
        repeat(wanted) { chapter ->
            currentCoroutineContext().ensureActive()
            val info = index.ensureChapter(chapter, pageCount) { emit -> build(chapter, pageCount + 1, emit) }
            chapters += info
            pageCount = Math.addExact(pageCount, info.pageCount)
            changed = changed || existing?.chapters?.getOrNull(chapter) != info
            if (changed) publishPrefix(PagePrefix(bookId, key, sourceChapterCount, chapters.toList()))
        }
        PagePrefix(bookId, key, sourceChapterCount, chapters.toList())
    }

    suspend fun pageContaining(prefix: PagePrefix, anchor: LogicalAnchor): PageSlice = lock.withLock {
        val info = prefix.chapters.getOrNull(anchor.chapterIndex)
            ?: error("Requested chapter is beyond the completed frontier")
        PageIndex(files, prefix.bookId, prefix.key).pageContaining(info, anchor)
    }

    suspend fun readGlobal(prefix: PagePrefix, globalPageNumber: Int): PageSlice = lock.withLock {
        val info = prefix.chapterForPage(globalPageNumber)
        PageIndex(files, prefix.bookId, prefix.key).readPage(info, globalPageNumber - info.prefixCount - 1)
    }

    suspend fun invalidate(prefix: PagePrefix) = lock.withLock {
        PageIndex(files, prefix.bookId, prefix.key).clear()
    }

    @Suppress("TooGenericExceptionCaught") // Disposable cache miss; cancellation never becomes a miss.
    private suspend fun loadPrefix(bookId: String, key: LayoutKey, sourceCount: Int): PagePrefix? =
        withContext(Dispatchers.IO) {
            try {
                val metadata = owned(bookId, key, "prefix.json")
                require(metadata.length() in 1..MAX_PREFIX_BYTES)
                val json = JSONObject(metadata.readText())
                require(json.getString("layoutHash") == key.hash && json.getInt("sourceChapterCount") == sourceCount)
                val array = json.getJSONArray("chapters")
                require(array.length() <= sourceCount)
                val chapters = List(array.length()) { i ->
                    val entry = array.getJSONObject(i)
                    ChapterPageInfo(entry.getInt("chapter"), entry.getInt("prefix"), entry.getInt("count"))
                }
                PagePrefix(bookId, key, sourceCount, chapters).also {
                    require(json.getInt("completedPrefix") == it.completedPrefix)
                    require(json.getInt("totalPages") == it.totalPages && json.getBoolean("eofKnown") == it.eofKnown)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun publishPrefix(prefix: PagePrefix) = withContext(Dispatchers.IO) {
        val target = owned(prefix.bookId, prefix.key, "prefix.json")
        val staging = owned(prefix.bookId, prefix.key, "prefix.${UUID.randomUUID()}.part")
        try {
            val chapters = JSONArray()
            prefix.chapters.forEach {
                chapters.put(
                    JSONObject().put("chapter", it.chapterIndex).put("prefix", it.prefixCount)
                        .put("count", it.pageCount)
                )
            }
            val json = JSONObject().put(
                "layoutHash",
                prefix.key.hash
            ).put("sourceChapterCount", prefix.sourceChapterCount)
                .put("completedPrefix", prefix.completedPrefix).put("totalPages", prefix.totalPages)
                .put("eofKnown", prefix.eofKnown).put("chapters", chapters)
            FileOutputStream(staging).use {
                it.write(json.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            staging.delete()
        }
    }

    private fun owned(bookId: String, key: LayoutKey, name: String): File = files.requireOwnedPath(
        bookId,
        File(files.derivedDirectory(bookId, "pages", key.hash), name).path,
    )

    private companion object {
        const val MAX_PREFIX_BYTES = 8 * 1024 * 1024L
    }
}

data class PagePrefix(
    val bookId: String,
    val key: LayoutKey,
    val sourceChapterCount: Int,
    val chapters: List<ChapterPageInfo>,
) {
    val completedPrefix = chapters.size
    val totalPages: Int
    val eofKnown = completedPrefix == sourceChapterCount

    init {
        UUID.fromString(bookId)
        require(sourceChapterCount > 0 && completedPrefix <= sourceChapterCount)
        var count = 0L
        chapters.forEachIndexed { index, chapter ->
            require(chapter.chapterIndex == index && chapter.prefixCount.toLong() == count)
            count += chapter.pageCount
            require(count <= Int.MAX_VALUE)
        }
        totalPages = count.toInt()
    }

    fun chapterForPage(number: Int): ChapterPageInfo {
        require(number in 1..totalPages)
        val index = chapters.binarySearch {
            when {
                number <= it.prefixCount -> 1
                number > it.prefixCount + it.pageCount -> -1
                else -> 0
            }
        }
        return chapters[index]
    }
}
