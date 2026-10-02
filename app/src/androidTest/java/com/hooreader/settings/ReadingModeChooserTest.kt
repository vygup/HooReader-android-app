package com.hooreader.settings

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReadingMode
import com.hooreader.ui.settings.ReaderSettingsSheet
import com.hooreader.ui.settings.ReaderSettingsViewModel
import com.hooreader.ui.theme.HooReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReadingModeChooserTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedModeAutomaticallyPersistsInSharedPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReaderPreferencesRepository(context)
        val previous = repository.preferences.first()
        val store = ViewModelStore()
        try {
            repository.setReadingMode(ReadingMode.VERTICAL)
            val model = ReaderSettingsViewModel(repository)
            compose.runOnUiThread { store.put("settings", model) }
            compose.setContent {
                val preferences by model.preferences.collectAsState()
                HooReaderTheme {
                    preferences?.let { current ->
                        ReaderSettingsSheet(
                            current,
                            model::setTheme,
                            model::setFontScale,
                            {},
                            onReadingModeChange = model::setReadingMode,
                        )
                    }
                }
            }
            compose.waitUntil { model.preferences.value != null }
            compose.onNodeWithTag("reading_mode_vertical").assertIsSelected()
            compose.onNodeWithTag("reading_mode_paginated").performClick()
            compose.waitUntil { model.preferences.value?.readingMode == ReadingMode.PAGINATED }
            compose.onNodeWithTag("reading_mode_paginated").assertIsSelected()
            val reopened = ReaderPreferencesRepository(context).preferences.first()
            assertEquals(previous.copy(readingMode = ReadingMode.PAGINATED), reopened)
            compose.onNodeWithTag("reading_mode_vertical").performClick()
            compose.waitUntil { model.preferences.value?.readingMode == ReadingMode.VERTICAL }
            compose.onNodeWithTag("reading_mode_vertical").assertIsSelected()
        } finally {
            compose.runOnUiThread { store.clear() }
            repository.setReadingMode(previous.readingMode)
        }
        Unit
    }
}
