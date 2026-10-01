package com.hooreader.domain.model

data class ReadingPosition(
    val bookId: String,
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val characterOffset: Int = 0,
    val progressPercent: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(bookId.isNotBlank())
        require(chapterIndex >= 0 && blockIndex >= 0 && characterOffset >= 0)
        require(progressPercent.isFinite() && progressPercent in 0.0..MAX_PROGRESS_PERCENT)
        require(updatedAt >= 0)
    }
    companion object {
        const val MAX_PROGRESS_PERCENT = 100.0
    }
}
