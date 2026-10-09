package com.hooreader.reader

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.ReadingPosition
import com.hooreader.ui.reader.BlockTextFactory
import com.hooreader.ui.reader.ReaderPositionResolver
import com.hooreader.ui.reader.readerTypography
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderPositionLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun visibleLineAndRestorationUseOriginalUtf16Coordinates() {
        val block = ContentBlock(2, 7, BlockKind.PARAGRAPH, "Кириллица 😀 e\u0301 漢字 текст. ".repeat(30))
        lateinit var layout: TextLayoutResult
        compose.setContent {
            HooReaderTheme {
                val typography = readerTypography(1f)
                Text(
                    BlockTextFactory.create(block, "Fallback"),
                    modifier = Modifier.width(200.dp),
                    style = typography.style(block.kind),
                    onTextLayout = { layout = it },
                )
            }
        }
        compose.runOnIdle {
            assertTrue(layout.lineCount > 10)
            for (line in 1 until layout.lineCount) {
                val offset = ReaderPositionResolver.safeOffset(block.text, layout.getLineStart(line))
                val pixels = layout.getLineTop(line).toInt() + 1
                val anchor = ReaderPositionResolver.topVisibleAnchor(block, layout, pixels)
                assertEquals(offset, anchor.characterOffset)
                val saved = ReadingPosition("book", 2, 7, offset)
                val restored = ReaderPositionResolver.restoreScrollOffset(saved, block, layout)
                assertEquals(layout.getLineTop(line).toInt(), restored)
            }
        }
    }
}
