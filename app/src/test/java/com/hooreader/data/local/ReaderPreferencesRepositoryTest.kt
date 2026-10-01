package com.hooreader.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
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
}
