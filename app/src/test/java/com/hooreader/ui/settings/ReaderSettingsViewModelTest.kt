package com.hooreader.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `bounds scale ignores nonfinite input and restores preferences in new model`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val job = SupervisorJob()
        val models = ViewModelStore()
        try {
            val file = folder.newFolder().resolve("settings.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            val repository = ReaderPreferencesRepository(store)
            val model = ReaderSettingsViewModel(repository).also { models.put("settings", it) }
            await(model, ReaderPreferences())
            model.setTheme(ReaderTheme.DARK)
            await(model, ReaderPreferences(ReaderTheme.DARK))
            model.setFontScale(100f)
            await(model, ReaderPreferences(ReaderTheme.DARK, ReaderPreferences.MAX_FONT_SCALE))
            model.setFontScale(-1f)
            await(model, ReaderPreferences(ReaderTheme.DARK, ReaderPreferences.MIN_FONT_SCALE))
            model.setFontScale(Float.NaN)
            model.setFontScale(Float.POSITIVE_INFINITY)
            assertEquals(ReaderPreferences(ReaderTheme.DARK, ReaderPreferences.MIN_FONT_SCALE), model.preferences.value)
            models.clear()
            val restored = ReaderSettingsViewModel(repository).also { models.put("settings", it) }
            await(restored, ReaderPreferences(ReaderTheme.DARK, ReaderPreferences.MIN_FONT_SCALE))
            Unit
        } finally {
            models.clear()
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed save preserves persisted settings and retries the requested change`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val job = SupervisorJob()
        val models = ViewModelStore()
        try {
            val file = folder.newFolder().resolve("failure.preferences_pb")
            val delegate = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            var failWrites = true
            val failing = object : DataStore<Preferences> by delegate {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    if (failWrites) throw IOException("Unavailable storage")
                    return delegate.updateData(transform)
                }
            }
            val model = ReaderSettingsViewModel(ReaderPreferencesRepository(failing))
                .also { models.put("settings", it) }
            await(model, ReaderPreferences())
            model.setTheme(ReaderTheme.DARK)
            withTimeout(5_000) { model.saveFailed.first { it } }
            assertEquals(ReaderPreferences(), model.preferences.value)
            failWrites = false
            model.retry()
            await(model, ReaderPreferences(ReaderTheme.DARK))
            withTimeout(5_000) { model.saveFailed.first { !it } }
            Unit
        } finally {
            models.clear()
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    private suspend fun await(model: ReaderSettingsViewModel, expected: ReaderPreferences) = withTimeout(5_000) {
        model.preferences.first { it == expected }
    }
}
