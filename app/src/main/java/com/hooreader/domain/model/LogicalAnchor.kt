package com.hooreader.domain.model

/** Coordinates in source Kotlin strings, independent of page numbers and pixel geometry. */
data class LogicalAnchor(
    val chapterIndex: Int,
    val blockIndex: Int,
    val characterOffset: Int
) : Comparable<LogicalAnchor> {
    init {
        require(chapterIndex >= 0 && blockIndex >= 0 && characterOffset >= 0)
    }

    override fun compareTo(other: LogicalAnchor): Int = compareValuesBy(
        this,
        other,
        LogicalAnchor::chapterIndex,
        LogicalAnchor::blockIndex,
        LogicalAnchor::characterOffset,
    )
}

fun ReadingPosition.logicalAnchor() = LogicalAnchor(chapterIndex, blockIndex, characterOffset)
