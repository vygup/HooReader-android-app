package com.hooreader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.domain.model.ReaderTheme

private val lightColors = lightColorScheme()
private val darkColors = darkColorScheme()

@Composable
fun HooReaderTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val activity = view.context as? Activity
    SideEffect {
        if (!view.isInEditMode && activity != null) {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        content = content,
    )
}

@Composable
fun HooReaderTheme(repository: ReaderPreferencesRepository, content: @Composable () -> Unit) {
    val preferences by repository.preferences.collectAsStateWithLifecycle(initialValue = null)
    preferences?.let { HooReaderTheme(darkTheme = it.theme == ReaderTheme.DARK, content = content) }
}
