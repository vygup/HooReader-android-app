package com.hooreader.data.local

import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.TextStyleRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentBlockCodecTest {
    @Test
    fun `long Unicode strings and source styles survive binary round trip`() {
        val text = "😀e\u0301漢字".repeat(20_000)
        val block = ContentBlock(3, 17, BlockKind.LIST, text, listOf(TextStyleRange(0, 2, bold = true)), "#image")
        assertEquals(block, ContentBlockCodec.decode(ContentBlockCodec.encode(block)))
        for (kind in BlockKind.entries) {
            val value = ContentBlock(0, 0, kind)
            assertEquals(value, ContentBlockCodec.decode(ContentBlockCodec.encode(value)))
        }
    }

    @Test
    fun `truncated and trailing data are rejected`() {
        val bytes = ContentBlockCodec.encode(ContentBlock(0, 0, BlockKind.PARAGRAPH, "Текст 😀"))
        assertThrows(java.io.EOFException::class.java) { ContentBlockCodec.decode(bytes.copyOf(2)) }
        assertThrows(IllegalArgumentException::class.java) { ContentBlockCodec.decode(bytes + byteArrayOf(0)) }
    }
}
