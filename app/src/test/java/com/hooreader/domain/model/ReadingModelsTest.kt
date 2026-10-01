package com.hooreader.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingModelsTest {
    @Test
    fun `position rejects negative coordinates and invalid progress`() {
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition("book", chapterIndex = -1) }
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition("book", blockIndex = -1) }
        assertThrows(IllegalArgumentException::class.java) { ReadingPosition("book", characterOffset = -1) }
        listOf(-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { progress ->
            assertThrows(IllegalArgumentException::class.java) {
                ReadingPosition("book", progressPercent = progress)
            }
        }
    }

    @Test
    fun `preferences reject invalid font scale`() {
        listOf(0f, 3f, Float.NaN, Float.POSITIVE_INFINITY).forEach { scale ->
            assertThrows(IllegalArgumentException::class.java) { ReaderPreferences(fontScale = scale) }
        }
    }

    @Test
    fun `styles must refer to existing text`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentBlock(0, 0, BlockKind.PARAGRAPH, "abc", listOf(TextStyleRange(0, 4, bold = true)))
        }
    }
}
