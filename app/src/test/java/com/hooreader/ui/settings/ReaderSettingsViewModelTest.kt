package com.hooreader.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferenceField
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
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

    @Test
    fun `retry writes latest rapid intentions across all fields`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val job = SupervisorJob()
        val models = ViewModelStore()
        try {
            val file = folder.newFolder().resolve("rapid.preferences_pb")
            val delegate = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            var failThemeWrites = true
            val failing = object : DataStore<Preferences> by delegate {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    val preview = transform(delegate.data.first())
                    if (failThemeWrites && preview.asMap().any { (key, value) ->
                            key.name == "theme" && value == ReaderTheme.DARK.name
                        }
                    ) {
                        throw IOException("Unavailable theme storage")
                    }
                    return delegate.updateData(transform)
                }
            }
            val model = ReaderSettingsViewModel(ReaderPreferencesRepository(failing))
                .also { models.put("rapid", it) }
            await(model, ReaderPreferences())
            model.setTheme(ReaderTheme.DARK)
            model.setFontScale(1.5f)
            model.setReadingMode(ReadingMode.PAGINATED)
            withTimeout(5_000) { model.writeState.first { it.error != null } }
            await(model, ReaderPreferences(fontScale = 1.5f, readingMode = ReadingMode.PAGINATED))
            withTimeout(5_000) {
                model.writeState.first { it.pendingFields == setOf(ReaderPreferenceField.THEME) }
            }
            assertEquals(
                ReaderPreferences(ReaderTheme.DARK, 1.5f, ReadingMode.PAGINATED),
                model.writeState.value.requestedSnapshot,
            )
            model.setTheme(ReaderTheme.LIGHT)
            withTimeout(5_000) {
                model.writeState.first {
                    it.pendingFields == setOf(ReaderPreferenceField.THEME) &&
                        it.requestedSnapshot.theme == ReaderTheme.LIGHT
                }
            }
            failThemeWrites = false
            model.retry()
            await(model, ReaderPreferences(ReaderTheme.LIGHT, 1.5f, ReadingMode.PAGINATED))
            withTimeout(5_000) { model.writeState.first { it.pendingFields.isEmpty() && it.error == null } }
            Unit
        } finally {
            models.clear()
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `app and reader writes share pending queue and retry preserves latest selection`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val job = SupervisorJob()
        val models = ViewModelStore()
        try {
            val file = folder.newFolder().resolve("app-pending.preferences_pb")
            val delegate = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            var failApp = true
            val failing = object : DataStore<Preferences> by delegate {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    val before = delegate.data.first()
                    val after = transform(before)
                    val key = androidx.datastore.preferences.core.booleanPreferencesKey("confirm_reader_exit")
                    if (failApp && after[key] != before[key]) throw IOException("Unavailable app preference")
                    return delegate.updateData(transform)
                }
            }
            val repository = ReaderPreferencesRepository(failing)
            val shared = ReaderSettingsViewModel(repository).also { models.put("shared", it) }
            val app = AppSettingsViewModel(shared).also { models.put("app", it) }
            await(shared, ReaderPreferences())
            withTimeout(5_000) { shared.appPreferences.first { it != null } }
            app.setConfirmReaderExit(false)
            withTimeout(5_000) { app.state.first { it.error != null } }
            shared.setTheme(ReaderTheme.DARK)
            await(shared, ReaderPreferences(ReaderTheme.DARK))
            withTimeout(5_000) {
                shared.writeState.first { it.pendingFields == setOf(ReaderPreferenceField.CONFIRM_READER_EXIT) }
            }
            assertEquals(false, app.state.value.requestedApp.confirmReaderExit)
            assertEquals(true, repository.appPreferences.first().confirmReaderExit)
            assertEquals("save_failed", app.state.value.error)
            app.setConfirmReaderExit(true)
            app.setConfirmReaderExit(false)
            failApp = false
            app.retry()
            withTimeout(5_000) { app.state.first { it.pendingFields.isEmpty() && it.error == null } }
            assertEquals(false, repository.appPreferences.first().confirmReaderExit)
            assertEquals(ReaderTheme.DARK, repository.preferences.first().theme)
            models.clear()
            val restored = ReaderSettingsViewModel(repository).also { models.put("restored", it) }
            withTimeout(5_000) { restored.appPreferences.first { it?.confirmReaderExit == false } }
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
