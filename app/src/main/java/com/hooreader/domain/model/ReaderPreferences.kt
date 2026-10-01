package com.hooreader.domain.model

enum class ReaderTheme { LIGHT, DARK }

data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScale: Float = 1f,
) {
    init {
        require(fontScale.isFinite() && fontScale in MIN_FONT_SCALE..MAX_FONT_SCALE)
    }

    companion object {
        const val MIN_FONT_SCALE = 0.75f
        const val MAX_FONT_SCALE = 2f
    }
}
