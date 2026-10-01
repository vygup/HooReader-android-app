package com.hooreader.reader

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ContentBlock
import com.hooreader.domain.model.TextStyleRange
import com.hooreader.ui.reader.ContentBlockRenderer
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ContentBlockRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendererAppliesOverlappingBoldItalicSpansAndListMarker() {
        val block = ContentBlock(
            0,
            0,
            BlockKind.PARAGRAPH,
            "Жирный курсив",
            listOf(TextStyleRange(0, 13, bold = true), TextStyleRange(7, 13, italic = true)),
        )
        compose.setContent {
            HooReaderTheme {
                Column {
                    ContentBlockRenderer(block, { null })
                    ContentBlockRenderer(ContentBlock(0, 1, BlockKind.LIST, "Пункт"), { null })
                }
            }
        }
        val text = compose.onNodeWithTag("block_0_0").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 13 })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.start == 7 && it.end == 13 })
        compose.onNodeWithText("•").assertIsDisplayed()
        compose.onNodeWithText("Пункт").assertIsDisplayed()
    }

    @Test
    fun embeddedRasterImageDecodesAndHasAccessibleDescription() {
        val png = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGMwi24AAAHcARKsQ0g7AAAAAElFTkSuQmCC",
            Base64.DEFAULT,
        )
        compose.setContent {
            HooReaderTheme {
                ContentBlockRenderer(
                    ContentBlock(0, 0, BlockKind.IMAGE, "Рисунок", mediaRef = "#pic"),
                    { png.inputStream() }
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Рисунок").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Рисунок").assertIsDisplayed()
    }

    @Test
    fun corruptAndUnreadableImagesDoNotBlockFollowingParagraph() {
        compose.setContent {
            HooReaderTheme {
                Column {
                    ContentBlockRenderer(ContentBlock(0, 0, BlockKind.IMAGE, mediaRef = "#bad"), {
                        "broken".byteInputStream()
                    })
                    ContentBlockRenderer(ContentBlock(0, 1, BlockKind.IMAGE, mediaRef = "#missing"), {
                        throw IOException("Unreadable test image")
                    })
                    ContentBlockRenderer(ContentBlock(0, 2, BlockKind.PARAGRAPH, "Чтение продолжается"), { null })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("block_0_0").assertIsDisplayed()
        compose.onNodeWithTag("block_0_1").assertIsDisplayed()
        compose.onNodeWithText("Чтение продолжается").assertIsDisplayed()
    }
}
