package com.hooreader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun SaveReadingPositionOnLifecycle(viewModel: ReaderViewModel) {
    val screen = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(screen, viewModel) {
        val process = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.saveNow()
        }
        screen.addObserver(observer)
        process.addObserver(observer)
        onDispose {
            screen.removeObserver(observer)
            process.removeObserver(observer)
            viewModel.saveNow()
        }
    }
}
