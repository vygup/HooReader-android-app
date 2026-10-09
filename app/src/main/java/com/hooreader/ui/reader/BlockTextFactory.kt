package com.hooreader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hooreader.domain.model.ContentBlock

/** One source string and style mapping for measurement and rendering; markers are decoration. */
object BlockTextFactory {
    fun create(block: ContentBlock, fallback: String): AnnotatedString = buildAnnotatedString {
        append(block.text.ifBlank { fallback })
        block.styles.forEach { range ->
            addStyle(
                SpanStyle(
                    fontWeight = if (range.bold) FontWeight.Bold else null,
                    fontStyle = if (range.italic) FontStyle.Italic else null,
                ),
                range.start,
                range.endExclusive,
            )
        }
    }
}
