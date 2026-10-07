package com.hooreader.ui.settings

import com.hooreader.domain.model.AppPreferences
import com.hooreader.domain.model.ReaderPreferenceField
import com.hooreader.domain.model.ReaderPreferences

data class SettingsWriteState(
    val persistedSnapshot: ReaderPreferences = ReaderPreferences(),
    val requestedSnapshot: ReaderPreferences = persistedSnapshot,
    val pendingFields: Set<ReaderPreferenceField> = emptySet(),
    val error: String? = null,
    val persistedApp: AppPreferences = AppPreferences(),
    val requestedApp: AppPreferences = persistedApp,
)
