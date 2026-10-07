package com.hooreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hooreader.R
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    onThemeChange: (ReaderTheme) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit = {},
    saveFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("reader_settings_sheet")) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("reader_settings_content")
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge)
            ThemeChooser(preferences.theme, onThemeChange)
            FontScaleChooser(preferences.fontScale, onFontScaleChange)
            ReadingModeChooser(preferences.readingMode, onReadingModeChange)
            if (saveFailed) {
                Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun ReadingModeChooser(mode: ReadingMode, onChange: (ReadingMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ReadingMode.entries.forEach { option ->
            val vertical = option == ReadingMode.VERTICAL
            Row(
                Modifier.fillMaxWidth()
                    .testTag(if (vertical) "reading_mode_vertical" else "reading_mode_paginated")
                    .selectable(selected = mode == option, role = Role.RadioButton, onClick = { onChange(option) })
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = mode == option, onClick = null)
                Text(stringResource(if (vertical) R.string.reading_mode_vertical else R.string.reading_mode_paginated))
            }
        }
    }
}

@Composable
private fun ThemeChooser(theme: ReaderTheme, onThemeChange: (ReaderTheme) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        ReaderTheme.entries.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = theme == option,
                    role = Role.RadioButton,
                    onClick = { onThemeChange(option) },
                ).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = theme == option, onClick = null)
                Text(stringResource(if (option == ReaderTheme.LIGHT) R.string.theme_light else R.string.theme_dark))
            }
        }
    }
}

@Composable
private fun FontScaleChooser(fontScale: Float, onFontScaleChange: (Float) -> Unit) {
    var draft by remember(fontScale) { mutableFloatStateOf(fontScale) }
    Text(stringResource(R.string.font_scale_percent, (draft * PERCENT_MULTIPLIER).roundToInt()))
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onFontScaleChange(draft) },
        valueRange = ReaderPreferences.MIN_FONT_SCALE..ReaderPreferences.MAX_FONT_SCALE,
        steps = 4,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            onClick = { onFontScaleChange(fontScale - FONT_SCALE_STEP) },
            enabled = fontScale > ReaderPreferences.MIN_FONT_SCALE,
        ) { Text(stringResource(R.string.font_scale_decrease)) }
        TextButton(
            onClick = { onFontScaleChange(fontScale + FONT_SCALE_STEP) },
            enabled = fontScale < ReaderPreferences.MAX_FONT_SCALE,
        ) { Text(stringResource(R.string.font_scale_increase)) }
    }
}

private const val PERCENT_MULTIPLIER = 100
private const val FONT_SCALE_STEP = 0.25f
