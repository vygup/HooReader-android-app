package com.hooreader.navigation

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hooreader.domain.model.ReaderPreferences
import com.hooreader.ui.library.ImportBookLauncher
import com.hooreader.ui.library.LibraryViewModel
import com.hooreader.ui.library.importFromPicker
import com.hooreader.ui.reader.GeometryChangeOrigin
import com.hooreader.ui.reader.ReaderChromeEvent
import com.hooreader.ui.reader.ReaderOverlay
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderViewModel
import com.hooreader.ui.settings.ReaderSettingsSheet
import com.hooreader.ui.settings.ReaderSettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun HooReaderNavHost(
    navController: NavHostController = rememberNavController(),
    libraryContent: (@Composable ((String) -> Unit) -> Unit)? = null,
    readerContent: (@Composable (String, () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val dependencies = remember(context) { ReaderDependencies(context) }
    NavHost(navController = navController, startDestination = HooReaderRoutes.LIBRARY) {
        composable(HooReaderRoutes.LIBRARY) {
            val openBook: (String) -> Unit = { bookId ->
                navController.navigate(HooReaderRoutes.reader(bookId)) { launchSingleTop = true }
            }
            if (libraryContent != null) {
                libraryContent(openBook)
            } else {
                val model: LibraryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { LibraryViewModel(dependencies.library) { importFromPicker(dependencies, it) } }
                    },
                )
                ImportBookLauncher(model, openBook)
            }
        }
        composable(
            route = HooReaderRoutes.READER,
            arguments = listOf(navArgument(HooReaderRoutes.BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = requireNotNull(entry.arguments?.getString(HooReaderRoutes.BOOK_ID))
            val onBack: () -> Unit = { navController.popBackStack() }
            if (readerContent != null) {
                readerContent(bookId, onBack)
            } else {
                ReaderDestination(bookId, dependencies, onBack)
            }
        }
    }
}

@Composable
private fun ReaderDestination(bookId: String, dependencies: ReaderDependencies, onBack: () -> Unit) {
    val settings: ReaderSettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { ReaderSettingsViewModel(dependencies.preferences) } },
    )
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    val current = preferences
    if (current == null) {
        CircularProgressIndicator()
    } else {
        OpenReaderDestination(bookId, dependencies, current, settings, onBack)
    }
}

@Composable
private fun OpenReaderDestination(
    bookId: String,
    dependencies: ReaderDependencies,
    preferences: ReaderPreferences,
    settings: ReaderSettingsViewModel,
    onBack: () -> Unit,
) {
    val model: ReaderViewModel = viewModel(
        key = bookId,
        factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId,
                    dependencies.repository,
                    dependencies.parsers,
                    dependencies.readerPages,
                    preferences,
                    dependencies.readerContent,
                )
            }
        },
    )
    val saveFailed by settings.saveFailed.collectAsStateWithLifecycle()
    val chrome by model.chromeState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(preferences) {
        model.pagination.applyPreferences(preferences, GeometryChangeOrigin.SYSTEM_CONFIGURATION)
    }
    ReaderScreen(
        model,
        fontScale = preferences.fontScale,
        onSettings = { model.onChromeEvent(ReaderChromeEvent.OPEN_READER_SETTINGS) },
        onBack = onBack,
    )
    if (chrome.overlay == ReaderOverlay.READER_SETTINGS) {
        ReaderSettingsSheet(
            preferences = preferences,
            onThemeChange = settings::setTheme,
            onFontScaleChange = { scale ->
                scope.launch {
                    model.pagination.applyPreferences(
                        preferences.copy(fontScale = scale),
                        GeometryChangeOrigin.USER_PREFERENCE,
                    ) { settings.setFontScale(scale) }
                }
            },
            onReadingModeChange = { mode ->
                scope.launch {
                    model.pagination.applyPreferences(
                        preferences.copy(readingMode = mode),
                        GeometryChangeOrigin.USER_PREFERENCE,
                    ) { settings.setReadingMode(mode) }
                }
            },
            onDismiss = { model.onChromeEvent(ReaderChromeEvent.DISMISS_OVERLAY) },
            saveFailed = saveFailed,
            onRetry = settings::retry,
        )
    }
}
