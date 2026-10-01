package com.hooreader.domain.model

import java.util.UUID

enum class BookFormat { EPUB, FB2 }

enum class BookState { IMPORTING, READY, FAILED }

data class Book(
    val id: String,
    val contentHash: String,
    val format: BookFormat,
    val localPath: String,
    val title: String,
    val author: String,
    val coverPath: String? = null,
    val state: BookState = BookState.IMPORTING,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(localPath.isNotBlank())
        require(title.isNotBlank() && author.isNotBlank())
    }
}
