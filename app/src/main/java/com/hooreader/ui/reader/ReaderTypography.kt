package com.hooreader.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hooreader.domain.model.BlockKind
import com.hooreader.domain.model.ReaderPreferences

/** sp conversion belongs to the captured Compose Density (including nonlinear Android font scaling). */
data class ReaderTypography(
    val body: TextStyle,
    val heading: TextStyle,
    val readingScale: Float,
    val listIndent: Dp = 24.dp,
    val blockSpacing: Dp = 16.dp,
) {
    init {
        require(readingScale.isFinite())
        require(readingScale in ReaderPreferences.MIN_FONT_SCALE..ReaderPreferences.MAX_FONT_SCALE)
    }

    fun style(kind: BlockKind): TextStyle {
        val base = if (kind == BlockKind.HEADING) heading else body
        return base.copy(fontSize = base.fontSize * readingScale, lineHeight = base.lineHeight * readingScale)
    }
}

@Composable
fun readerTypography(fontScale: Float): ReaderTypography = ReaderTypography(
    MaterialTheme.typography.bodyLarge,
    MaterialTheme.typography.headlineSmall,
    fontScale,
)
