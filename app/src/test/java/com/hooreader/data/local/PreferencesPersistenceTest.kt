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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesPersistenceTest {
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
