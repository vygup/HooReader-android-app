package com.hooreader.ui.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.TextStyleRange
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockTextFactoryTest {
    @Test
    fun `list decoration does not shift source Unicode styles`() {
        val block = ContentBlock(0, 0, BlockKind.LIST, "😀 Жирный e\u0301", listOf(TextStyleRange(3, 9, bold = true)))
        val text = BlockTextFactory.create(block, "Fallback")
        assertEquals(block.text, text.text)
        assertEquals(3, text.spanStyles.single().start)
        assertEquals(9, text.spanStyles.single().end)
        assertEquals(FontWeight.Bold, text.spanStyles.single().item.fontWeight)
    }

    @Test
    fun `body heading and fallback use the same reading scale and sp line height`() {
        val typography = ReaderTypography(
            TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
            2f
        )
        assertEquals(32.sp, typography.style(BlockKind.PARAGRAPH).fontSize)
        assertEquals(48.sp, typography.style(BlockKind.PARAGRAPH).lineHeight)
        assertEquals(typography.style(BlockKind.PARAGRAPH), typography.style(BlockKind.FALLBACK))
        assertEquals(48.sp, typography.style(BlockKind.HEADING).fontSize)
        assertEquals("Fallback", BlockTextFactory.create(ContentBlock(0, 0, BlockKind.FALLBACK), "Fallback").text)
    }
}
