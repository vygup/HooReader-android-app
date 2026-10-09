package com.hooreader.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.hooreader.data.local.PagePrefix
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.LogicalAnchor
import com.hooreader.ui.reader.PagedReaderContent
import com.hooreader.ui.reader.ReaderTypography
import com.hooreader.ui.reader.pagination.ChapterPageInfo
import com.hooreader.ui.reader.pagination.LayoutContentIdentity
import com.hooreader.ui.reader.pagination.LayoutKey
import com.hooreader.ui.reader.pagination.LayoutTypography
import com.hooreader.ui.reader.pagination.LayoutViewport
import com.hooreader.ui.reader.pagination.PageFragment
import com.hooreader.ui.reader.pagination.PageSlice
import com.hooreader.ui.reader.pagination.PreparedDrawFragment
import com.hooreader.ui.reader.pagination.PreparedPageDraw
import com.hooreader.ui.theme.HooReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PagedReaderContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fastFlingCancelFrontierAndEofUseStableAbsoluteIndices() {
        val settled = AtomicInteger(1)
        val taps = AtomicInteger(0)
        val key = LayoutKey(
            LayoutContentIdentity("a".repeat(64)),
            LayoutViewport(480, 800, 1f, 1f, listOf(16f, 24f)),
            LayoutTypography(1f, "Default", "Android", "16/24", "24/32", "Simple", "ru", "Ltr", 24f, 16f),
        )
        val id = UUID.randomUUID().toString()
        compose.setContent { Fixture(settled, taps, key, id) }
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeRight(durationMillis = 100) }
        compose.waitForIdle()
        assertEquals(1, settled.get())
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft(durationMillis = 100) }
        compose.waitUntil { settled.get() == 2 }
        compose.onNodeWithTag("reader_pager").performTouchInput {
            down(center)
            moveTo(center + Offset(-30f, 0f))
            cancel()
        }
        compose.waitForIdle()
        assertEquals(2, settled.get())
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft(durationMillis = 100) }
        compose.waitUntil { settled.get() == 3 }
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeLeft(durationMillis = 100) }
        compose.waitForIdle()
        assertEquals(3, settled.get())
        compose.onNodeWithTag("reader_pager").performTouchInput { swipeRight(durationMillis = 100) }
        compose.waitUntil { settled.get() == 2 }
        assertEquals(0, taps.get())
    }

    @Composable
    private fun Fixture(settled: AtomicInteger, taps: AtomicInteger, key: LayoutKey, id: String) {
        HooReaderTheme {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
            val typography = ReaderTypography(style, style, 1f)
            val pages = remember {
                (1..3).map { number ->
                    val chapter = if (number == 3) 1 else 0
                    val block = if (number == 3) 0 else number - 1
                    val text = "Страница $number"
                    val layout = measurer.measure(text, style)
                    val fragment = PageFragment(
                        chapter, block, BlockKind.PARAGRAPH, 0, text.length, 0, layout.lineCount,
                        0f, 0f, 480f, layout.size.height.toFloat(), 0f,
                    )
                    val slice = PageSlice(
                        chapter,
                        block,
                        number,
                        LogicalAnchor(chapter, block, 0),
                        LogicalAnchor(chapter, block + 1, 0),
                        listOf(fragment),
                    )
                    PreparedPageDraw(slice, listOf(PreparedDrawFragment(fragment, layout)))
                }
            }
            var prefix by remember { mutableStateOf(PagePrefix(id, key, 2, listOf(ChapterPageInfo(0, 0, 2)))) }
            PagedReaderContent(
                pages.first(), prefix, key, measurer, typography,
                loadPage = { pages[it - 1] },
                onSettledPage = { settled.set(it.globalPageNumber) },
                onLoadFrontier = {
                    prefix = PagePrefix(id, key, 2, listOf(ChapterPageInfo(0, 0, 2), ChapterPageInfo(1, 2, 1)))
                },
                onBookTap = { taps.incrementAndGet() },
                onNavigationDragStarted = {},
                onGestureFinished = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
