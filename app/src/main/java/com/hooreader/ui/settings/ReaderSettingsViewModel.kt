package com.hooreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferenceField
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class ReaderSettingsViewModel(private val repository: ReaderPreferencesRepository) : ViewModel() {
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val appPreferences = repository.appPreferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableWriteState = MutableStateFlow(SettingsWriteState())
    val writeState = mutableWriteState.asStateFlow()
    private val writeMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.preferences.collect { persisted ->
                val current = mutableWriteState.value
                if (current.pendingFields.isEmpty()) {
                    mutableWriteState.value = current.copy(
                        persistedSnapshot = persisted,
                        requestedSnapshot = persisted,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.appPreferences.collect { persisted ->
                val current = mutableWriteState.value
                mutableWriteState.value = current.copy(
                    persistedApp = persisted,
                    requestedApp = if (ReaderPreferenceField.CONFIRM_READER_EXIT in current.pendingFields) {
                        current.requestedApp
                    } else {
                        persisted
                    },
                )
            }
        }
    }

    val saveFailed = writeState.map { it.error != null }

    fun setTheme(theme: ReaderTheme) = request(ReaderPreferenceField.THEME) { it.copy(theme = theme) }

    fun setReadingMode(mode: ReadingMode) =
        request(ReaderPreferenceField.READING_MODE) { it.copy(readingMode = mode) }

    fun setFontScale(scale: Float) {
        if (!scale.isFinite()) return
        val bounded = scale.coerceIn(ReaderPreferences.MIN_FONT_SCALE, ReaderPreferences.MAX_FONT_SCALE)
        request(ReaderPreferenceField.FONT_SCALE) { it.copy(fontScale = bounded) }
    }

    fun retry() = launchFlush()

    fun setConfirmReaderExit(confirm: Boolean) {
        val current = mutableWriteState.value
        mutableWriteState.value = current.copy(
            requestedApp = current.requestedApp.copy(confirmReaderExit = confirm),
            pendingFields = current.pendingFields + ReaderPreferenceField.CONFIRM_READER_EXIT,
        )
        launchFlush()
    }

    private fun request(field: ReaderPreferenceField, change: (ReaderPreferences) -> ReaderPreferences) {
        val current = mutableWriteState.value
        mutableWriteState.value = current.copy(
            requestedSnapshot = change(current.requestedSnapshot),
            pendingFields = current.pendingFields + field,
        )
        launchFlush()
    }

    private fun launchFlush() {
        viewModelScope.launch {
            writeMutex.withLock {
                while (true) {
                    val current = mutableWriteState.value
                    val fields = current.pendingFields
                    if (fields.isEmpty()) return@withLock
                    val requested = current.requestedSnapshot
                    var failed = false
                    for (field in orderedFields.filter { it in fields }) {
                        try {
                            repository.writePending(requested, setOf(field), current.requestedApp)
                            val latest = mutableWriteState.value
                            val persisted = merge(latest.persistedSnapshot, requested, setOf(field))
                            val latestValue = valueFor(latest, field)
                            val writtenValue = valueFor(current, field)
                            val confirmed = if (latestValue == writtenValue) {
                                latest.pendingFields - field
                            } else {
                                latest.pendingFields
                            }
                            mutableWriteState.value = latest.copy(
                                persistedSnapshot = persisted,
                                persistedApp = if (field == ReaderPreferenceField.CONFIRM_READER_EXIT) {
                                    current.requestedApp
                                } else {
                                    latest.persistedApp
                                },
                                pendingFields = confirmed,
                                error = latest.error,
                            )
                        } catch (_: IOException) {
                            failed = true
                            mutableWriteState.value = mutableWriteState.value.copy(error = "save_failed")
                        }
                    }
                    if (failed) return@withLock
                    mutableWriteState.value = mutableWriteState.value.copy(error = null)
                }
            }
        }
    }

    private fun merge(
        base: ReaderPreferences,
        written: ReaderPreferences,
        fields: Set<ReaderPreferenceField>,
    ) = base.copy(
        theme = if (ReaderPreferenceField.THEME in fields) written.theme else base.theme,
        fontScale = if (ReaderPreferenceField.FONT_SCALE in fields) written.fontScale else base.fontScale,
        readingMode = if (ReaderPreferenceField.READING_MODE in fields) written.readingMode else base.readingMode,
    )

    private fun valueFor(state: SettingsWriteState, field: ReaderPreferenceField): Any = when (field) {
        ReaderPreferenceField.THEME -> state.requestedSnapshot.theme
        ReaderPreferenceField.FONT_SCALE -> state.requestedSnapshot.fontScale
        ReaderPreferenceField.READING_MODE -> state.requestedSnapshot.readingMode
        ReaderPreferenceField.CONFIRM_READER_EXIT -> state.requestedApp.confirmReaderExit
    }

    private companion object {
        val orderedFields = listOf(
            ReaderPreferenceField.THEME,
            ReaderPreferenceField.FONT_SCALE,
            ReaderPreferenceField.READING_MODE,
            ReaderPreferenceField.CONFIRM_READER_EXIT,
        )
    }
}
