package com.hooreader.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hooreader.domain.model.AppPreferences
import com.hooreader.domain.model.ReaderPreferenceField
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderPreferencesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `theme and scale survive store reopening`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file }
        val repository = ReaderPreferencesRepository(firstStore)
        assertEquals(ReaderPreferences(), repository.preferences.first())
        assertEquals(AppPreferences(true), repository.appPreferences.first())
        repository.setConfirmReaderExit(false)
        repository.setTheme(ReaderTheme.DARK)
        repository.setFontScale(1.5f)
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO),
            ) { file }
            assertEquals(
                ReaderPreferences(ReaderTheme.DARK, 1.5f),
                ReaderPreferencesRepository(reopened).preferences.first(),
            )
            assertEquals(AppPreferences(false), ReaderPreferencesRepository(reopened).appPreferences.first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun `theme changes preserve scale and scale changes preserve theme`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            val repository = ReaderPreferencesRepository(store)
            repository.setFontScale(ReaderPreferences.MIN_FONT_SCALE)
            repository.setTheme(ReaderTheme.DARK)
            assertEquals(
                ReaderPreferences(ReaderTheme.DARK, ReaderPreferences.MIN_FONT_SCALE),
                repository.preferences.first(),
            )
            repository.setFontScale(ReaderPreferences.MAX_FONT_SCALE)
            repository.setTheme(ReaderTheme.LIGHT)
            assertEquals(
                ReaderPreferences(ReaderTheme.LIGHT, ReaderPreferences.MAX_FONT_SCALE),
                repository.preferences.first(),
            )
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `invalid writes are rejected without changing saved preferences`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            val repository = ReaderPreferencesRepository(store)
            repository.setTheme(ReaderTheme.DARK)
            repository.setFontScale(1.5f)
            for (scale in listOf(0f, 3f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setFontScale(scale) } }
                assertEquals(ReaderPreferences(ReaderTheme.DARK, 1.5f), repository.preferences.first())
            }
            store.edit { it[floatPreferencesKey("font_scale")] = 100f }
            assertEquals(ReaderPreferences.MAX_FONT_SCALE, repository.preferences.first().fontScale)
            store.edit { it[floatPreferencesKey("font_scale")] = -10f }
            assertEquals(ReaderPreferences.MIN_FONT_SCALE, repository.preferences.first().fontScale)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `invalid stored values fall back safely`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            store.edit {
                it[stringPreferencesKey("theme")] = "unsupported"
                it[floatPreferencesKey("font_scale")] = Float.NaN
            }
            assertEquals(ReaderPreferences(), ReaderPreferencesRepository(store).preferences.first())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `old file and unknown mode fall back without losing theme or scale`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            store.edit {
                it[stringPreferencesKey("theme")] = "DARK"
                it[floatPreferencesKey("font_scale")] = 1.75f
            }
            val repository = ReaderPreferencesRepository(store)
            val original = ReaderPreferences(ReaderTheme.DARK, 1.75f, ReadingMode.VERTICAL)
            assertEquals(AppPreferences(true), repository.appPreferences.first())
            repository.setConfirmReaderExit(false)
            repository.writePending(
                original.copy(readingMode = ReadingMode.PAGINATED),
                setOf(ReaderPreferenceField.READING_MODE)
            )
            assertEquals(AppPreferences(false), repository.appPreferences.first())
            repository.setReadingMode(ReadingMode.VERTICAL)
            assertEquals(original, repository.preferences.first())
            for (unknown in listOf("future-mode", "paginated", "")) {
                store.edit { it[stringPreferencesKey("reading_mode")] = unknown }
                assertEquals(original, repository.preferences.first())
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `mode survives reopening and writes preserve all other fields`() = runBlocking {
        val file = folder.newFolder().resolve("reader.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file }
            val repository = ReaderPreferencesRepository(store)
            repository.setReadingMode(ReadingMode.PAGINATED)
            repository.setTheme(ReaderTheme.DARK)
            repository.setFontScale(2f)
            assertEquals(ReaderPreferences(ReaderTheme.DARK, 2f, ReadingMode.PAGINATED), repository.preferences.first())
            repository.setReadingMode(ReadingMode.VERTICAL)
            assertEquals(ReaderPreferences(ReaderTheme.DARK, 2f, ReadingMode.VERTICAL), repository.preferences.first())
            repository.setReadingMode(ReadingMode.PAGINATED)
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file }
            assertEquals(
                ReaderPreferences(ReaderTheme.DARK, 2f, ReadingMode.PAGINATED),
                ReaderPreferencesRepository(store).preferences.first()
            )
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
