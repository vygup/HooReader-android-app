package com.hooreader.ui.reader.pagination

import java.security.MessageDigest

/** Colors and chrome visibility deliberately do not participate in geometry identity. */
data class LayoutKey(
    val content: LayoutContentIdentity,
    val viewport: LayoutViewport,
    val typography: LayoutTypography,
) {
    val hash: String get() = MessageDigest.getInstance("SHA-256").digest(toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class LayoutContentIdentity(
    val contentHash: String,
    val parserVersion: Int = 1,
    val paginatorVersion: Int = 1,
    val schemaVersion: Int = 1,
) {
    init {
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(parserVersion > 0 && paginatorVersion > 0 && schemaVersion > 0)
    }
}

data class LayoutViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val systemFontScale: Float,
    val spConversionSamples: List<Float>,
    // Actual composed strip heights; the viewport excludes both. Chrome is an independent overlay.
    val topStripPx: Int = 0,
    val bottomStripPx: Int = 0,
    val insetTopPx: Int = 0,
    val insetBottomPx: Int = 0,
    val insetLeftPx: Int = 0,
    val insetRightPx: Int = 0,
) {
    init {
        require(widthPx > 0 && heightPx > 0 && topStripPx >= 0 && bottomStripPx >= 0)
        require(density.isFinite() && density > 0 && systemFontScale.isFinite() && systemFontScale > 0)
        require(spConversionSamples.isNotEmpty() && spConversionSamples.all { it.isFinite() && it > 0 })
        require(listOf(insetTopPx, insetBottomPx, insetLeftPx, insetRightPx).all { it >= 0 })
    }
}

@Suppress("LongParameterList") // Every metric has explicit cache invalidation semantics.
data class LayoutTypography(
    val readingScale: Float,
    val fontIdentity: String,
    val systemFontVersion: String,
    val bodyStyle: String,
    val headingStyle: String,
    val lineBreak: String,
    val locale: String,
    val layoutDirection: String,
    val listIndentPx: Float,
    val blockSpacingPx: Float,
) {
    init {
        require(readingScale.isFinite() && readingScale > 0)
        require(listIndentPx.isFinite() && listIndentPx >= 0 && blockSpacingPx.isFinite() && blockSpacingPx >= 0)
        require(fontIdentity.isNotBlank() && systemFontVersion.isNotBlank())
    }
}
