package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.domain.model.ReadingMode
import com.hooreader.ui.reader.pagination.readerLayoutKey

/** Strips are measured before the viewport in the same pass, independently of overlay visibility. */
@Composable
internal fun ReadingIndicators(
    reading: ReaderUiState.Reading?,
    modifier: Modifier = Modifier,
    content: @Composable (topStripPx: Int, bottomStripPx: Int) -> Unit,
) {
    val base = MaterialTheme.typography.labelSmall
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value * (reading?.fontScale ?: 1f)
    val ratio = minOf(1f, bodySize * INDICATOR_MAX_BODY_RATIO / base.fontSize.value)
    val style = base.copy(
        fontSize = base.fontSize * ratio,
        lineHeight = base.lineHeight * ratio,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INDICATOR_ALPHA),
    )
    val indicator = reading?.indicator
    val paginated = reading?.effectiveMode == ReadingMode.PAGINATED
    SubcomposeLayout(modifier) { constraints ->
        val stripConstraints = constraints.copy(minHeight = 0)
        val top = subcompose(IndicatorSlot.TOP) {
            if (indicator != null) TopReadingIndicator(indicator, style)
        }.map { it.measure(stripConstraints) }
        val topHeight = top.maxOfOrNull { it.height } ?: 0
        val bottomHeight = if (paginated) {
            subcompose(IndicatorSlot.BOTTOM_MEASUREMENT) { PageReadingIndicator(null, style) }
                .single().measure(stripConstraints).height
        } else {
            0
        }
        val contentHeight = (constraints.maxHeight - topHeight - bottomHeight).coerceAtLeast(1)
        val bottom = subcompose(IndicatorSlot.BOTTOM) {
            if (paginated && reading != null) {
                val key = readerLayoutKey(
                    reading.book.contentHash,
                    readerTypography(reading.fontScale),
                    constraints.maxWidth,
                    contentHeight,
                    topHeight,
                    bottomHeight,
                )
                PageReadingIndicator(indicator?.pageNumber?.takeIf { reading.pages?.key == key }, style)
            }
        }.map { it.measure(stripConstraints) }
        val book = subcompose(IndicatorSlot.CONTENT) { content(topHeight, bottomHeight) }
            .single().measure(Constraints.fixed(constraints.maxWidth, contentHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            top.forEach { it.placeRelative(0, 0) }
            book.placeRelative(0, topHeight)
            bottom.forEach { it.placeRelative(0, topHeight + contentHeight) }
        }
    }
}

@Composable
private fun TopReadingIndicator(indicator: ReadingIndicator, style: TextStyle) {
    if (indicator.chapter == null && indicator.percent == null) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicator.chapter?.let { chapter ->
            Text(
                chapter.title ?: stringResource(R.string.chapter_number, chapter.number),
                Modifier.weight(1f).testTag("reader_chapter_indicator"),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        indicator.percent?.let { percent ->
            Text(
                stringResource(R.string.reader_book_percent, percent),
                Modifier.testTag("reader_progress_indicator"),
                style = style,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun PageReadingIndicator(number: Int?, style: TextStyle) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            if (number == null) " " else stringResource(R.string.reader_page_number, number),
            if (number == null) Modifier else Modifier.testTag("reader_page_indicator"),
            style = style,
            softWrap = false,
        )
    }
}

private enum class IndicatorSlot { TOP, BOTTOM_MEASUREMENT, BOTTOM, CONTENT }
private const val INDICATOR_ALPHA = 0.6f
private const val INDICATOR_MAX_BODY_RATIO = 0.9f
