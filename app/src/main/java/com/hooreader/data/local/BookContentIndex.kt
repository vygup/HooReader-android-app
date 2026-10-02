package com.hooreader.data.local

import java.util.UUID

/** Derived metadata only: deleting it never deletes the immutable book or Room position. */
data class BookContentIndex(
    val bookId: String,
    val contentHash: String,
    val parserVersion: Int,
    val spoolVersion: Int,
    val chapterDirectory: List<ContentChapter>,
    val checkpoints: List<ContentCheckpoint>,
    val recordCount: Int,
    val spoolBytes: Long,
    val spoolHash: String,
    val complete: Boolean,
) {
    init {
        require(UUID.fromString(bookId).toString() == bookId)
        require(contentHash.matches(HASH) && spoolHash.matches(HASH))
        require(parserVersion > 0 && spoolVersion > 0 && recordCount >= 0 && spoolBytes >= 0)
        require(chapterDirectory.map { it.chapterIndex } == chapterDirectory.indices.toList())
        var next = 0
        chapterDirectory.forEach {
            require(it.firstRecord == next)
            next += it.blockCount
        }
        require(next == recordCount)
        require(
            checkpoints.zipWithNext().all { (a, b) -> a.recordIndex < b.recordIndex && a.byteOffset < b.byteOffset }
        )
        require(checkpoints.all { it.recordIndex < recordCount && it.byteOffset < spoolBytes })
        require(recordCount == 0 || checkpoints.firstOrNull() == ContentCheckpoint(0, 0))
    }

    companion object {
        const val PARSER_VERSION = 1
        const val SPOOL_VERSION = 1
        const val CHECKPOINT_INTERVAL = 64
        private val HASH = Regex("[0-9a-f]{64}")
    }
}

data class ContentChapter(val chapterIndex: Int, val firstRecord: Int, val blockCount: Int) {
    init {
        require(chapterIndex >= 0 && firstRecord >= 0 && blockCount >= 0)
    }
}

/** byteOffset is a UTF-8 spool address, never a UTF-16 characterOffset in a book. */
data class ContentCheckpoint(val recordIndex: Int, val byteOffset: Long) {
    init {
        require(recordIndex >= 0 && byteOffset >= 0)
    }
}
