package com.hooreader.domain.model

data class Chapter(
    val bookId: String,
    val index: Int,
    val title: String?,
    val sourceRef: String,
    val blockCount: Int,
) {
    init {
        require(bookId.isNotBlank())
        require(index >= 0 && blockCount >= 0)
        require(sourceRef.isNotBlank())
    }
}
