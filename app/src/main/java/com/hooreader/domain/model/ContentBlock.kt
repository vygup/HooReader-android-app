package com.hooreader.domain.model

enum class BlockKind { PARAGRAPH, HEADING, LIST, IMAGE, FALLBACK }

data class TextStyleRange(
    val start: Int,
    val endExclusive: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
) {
    init {
        require(start >= 0 && endExclusive >= start)
    }
}

data class ContentBlock(
    val chapterIndex: Int,
    val blockIndex: Int,
    val kind: BlockKind,
    val text: String = "",
    val styles: List<TextStyleRange> = emptyList(),
    val mediaRef: String? = null,
) {
    init {
        require(chapterIndex >= 0 && blockIndex >= 0)
        require(styles.all { it.endExclusive <= text.length })
    }
}
