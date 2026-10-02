package com.hooreader.ui.reader.pagination

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.sp
import com.hooreader.ui.reader.ReaderTypography

@Composable
fun readerLayoutKey(
    contentHash: String,
    typography: ReaderTypography,
    width: Int,
    height: Int,
    topStrip: Int,
    bottomStrip: Int,
): LayoutKey {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val adjustment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) configuration.fontWeightAdjustment else 0
    val insets = WindowInsets.safeDrawing
    return LayoutKey(
        LayoutContentIdentity(contentHash),
        LayoutViewport(
            width,
            height,
            density.density,
            density.fontScale,
            with(density) { listOf(11.sp.toPx(), 16.sp.toPx(), 24.sp.toPx(), 32.sp.toPx()) },
            topStripPx = topStrip,
            bottomStripPx = bottomStrip,
            insetTopPx = insets.getTop(density),
            insetBottomPx = insets.getBottom(density),
            insetLeftPx = insets.getLeft(density, direction),
            insetRightPx = insets.getRight(density, direction),
        ),
        LayoutTypography(
            typography.readingScale,
            "AndroidDefault:$adjustment",
            Build.FINGERPRINT,
            typography.body.toString(),
            typography.heading.toString(),
            typography.body.lineBreak.toString(),
            configuration.locales.toLanguageTags(),
            direction.name,
            with(density) { typography.listIndent.toPx() },
            with(density) { typography.blockSpacing.toPx() },
        ),
    )
}
