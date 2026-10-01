package com.hooreader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
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

    val preferences: Flow<ReaderPreferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        val theme = ReaderTheme.entries.firstOrNull { it.name == values[THEME] } ?: ReaderTheme.LIGHT
        val scale = values[FONT_SCALE]?.takeIf { it.isFinite() } ?: 1f
        ReaderPreferences(theme, scale.coerceIn(ReaderPreferences.MIN_FONT_SCALE, ReaderPreferences.MAX_FONT_SCALE))
    }

    suspend fun setTheme(theme: ReaderTheme) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setFontScale(scale: Float) {
        require(scale.isFinite() && scale in ReaderPreferences.MIN_FONT_SCALE..ReaderPreferences.MAX_FONT_SCALE)
        store.edit { it[FONT_SCALE] = scale }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
    }
}
