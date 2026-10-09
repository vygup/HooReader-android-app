package com.hooreader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hooreader.domain.model.AppPreferences
import com.hooreader.domain.model.ReaderPreferenceField
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.readerPreferencesStore by preferencesDataStore(
    name = "reader_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class ReaderPreferencesRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.readerPreferencesStore)

    private val stored = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    val appPreferences: Flow<AppPreferences> = stored.map { AppPreferences(it[CONFIRM_READER_EXIT] ?: true) }
    val preferences: Flow<ReaderPreferences> = stored.map { values ->
        val theme = ReaderTheme.entries.firstOrNull { it.name == values[THEME] } ?: ReaderTheme.LIGHT
        val scale = values[FONT_SCALE]?.takeIf { it.isFinite() } ?: 1f
        val mode = ReadingMode.entries.firstOrNull { it.name == values[READING_MODE] } ?: ReadingMode.VERTICAL
        ReaderPreferences(
            theme,
            scale.coerceIn(ReaderPreferences.MIN_FONT_SCALE, ReaderPreferences.MAX_FONT_SCALE),
            mode
        )
    }

    suspend fun setTheme(theme: ReaderTheme) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setFontScale(scale: Float) {
        require(scale.isFinite() && scale in ReaderPreferences.MIN_FONT_SCALE..ReaderPreferences.MAX_FONT_SCALE)
        store.edit { it[FONT_SCALE] = scale }
    }

    suspend fun setReadingMode(mode: ReadingMode) {
        store.edit { it[READING_MODE] = mode.name }
    }

    suspend fun setConfirmReaderExit(confirm: Boolean) {
        store.edit { it[CONFIRM_READER_EXIT] = confirm }
    }

    suspend fun writePending(
        snapshot: ReaderPreferences,
        fields: Set<ReaderPreferenceField>,
        appSnapshot: AppPreferences = AppPreferences(),
    ) {
        store.edit { values ->
            if (ReaderPreferenceField.THEME in fields) values[THEME] = snapshot.theme.name
            if (ReaderPreferenceField.FONT_SCALE in fields) values[FONT_SCALE] = snapshot.fontScale
            if (ReaderPreferenceField.READING_MODE in fields) values[READING_MODE] = snapshot.readingMode.name
            if (ReaderPreferenceField.CONFIRM_READER_EXIT in fields) {
                values[CONFIRM_READER_EXIT] = appSnapshot.confirmReaderExit
            }
        }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val READING_MODE = stringPreferencesKey("reading_mode")
        private val CONFIRM_READER_EXIT = booleanPreferencesKey("confirm_reader_exit")
    }
}
