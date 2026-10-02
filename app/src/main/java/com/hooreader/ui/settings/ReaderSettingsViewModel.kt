package com.hooreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.domain.model.ReaderTheme
import com.hooreader.domain.model.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

class ReaderSettingsViewModel(private val repository: ReaderPreferencesRepository) : ViewModel() {
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()
    private var retryWrite: (suspend () -> Unit)? = null

    fun setTheme(theme: ReaderTheme) = write { repository.setTheme(theme) }

    fun setReadingMode(mode: ReadingMode) = write { repository.setReadingMode(mode) }

    fun setFontScale(scale: Float) {
        if (!scale.isFinite()) return
        val bounded = scale.coerceIn(ReaderPreferences.MIN_FONT_SCALE, ReaderPreferences.MAX_FONT_SCALE)
        write { repository.setFontScale(bounded) }
    }

    fun retry() {
        retryWrite?.let(::write)
    }

    private fun write(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
                retryWrite = null
                mutableSaveFailed.value = false
            } catch (_: IOException) {
                retryWrite = update
                mutableSaveFailed.value = true
            }
        }
    }
}
