package com.hooreader.ui.settings

import androidx.lifecycle.ViewModel

/** Both settings surfaces delegate writes to the same navigation-host queue. */
class AppSettingsViewModel(private val settings: ReaderSettingsViewModel) : ViewModel() {
    val state = settings.writeState
    fun setConfirmReaderExit(confirm: Boolean) = settings.setConfirmReaderExit(confirm)
    fun retry() = settings.retry()
}
