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
import com.hooreader.ui.settings.AppSettingsScreen
import com.hooreader.ui.settings.AppSettingsViewModel
import com.hooreader.ui.settings.ReaderSettingsSheet
import com.hooreader.ui.settings.ReaderSettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun HooReaderNavHost(
    navController: NavHostController = rememberNavController(),
    libraryContent: (@Composable ((String) -> Unit) -> Unit)? = null,
    readerContent: (@Composable (String, () -> Unit) -> Unit)? = null,
    readerDependencies: ReaderDependencies? = null,
) {
    val context = LocalContext.current.applicationContext
    val dependencies = readerDependencies ?: remember(context) { ReaderDependencies(context) }
    val settings: ReaderSettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { ReaderSettingsViewModel(dependencies.preferences) } },
    )
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
                ImportBookLauncher(model, openBook) {
                    navController.navigate(HooReaderRoutes.APP_SETTINGS) { launchSingleTop = true }
                }
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
                ReaderDestination(bookId, dependencies, settings, onBack)
            }
        }
        composable(HooReaderRoutes.APP_SETTINGS) {
            val model: AppSettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { AppSettingsViewModel(settings) } },
            )
            val state by model.state.collectAsStateWithLifecycle()
            val loaded by settings.appPreferences.collectAsStateWithLifecycle()
            if (loaded == null) {
                CircularProgressIndicator()
            } else {
                AppSettingsScreen(state, model::setConfirmReaderExit, model::retry) { navController.popBackStack() }
            }
        }
    }
}

@Composable
private fun ReaderDestination(
    bookId: String,
    dependencies: ReaderDependencies,
    settings: ReaderSettingsViewModel,
    onBack: () -> Unit,
) {
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    val appPreferences by settings.appPreferences.collectAsStateWithLifecycle()
    val current = preferences
    if (current == null || appPreferences == null) {
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
                    dependencies.positionSaverFactory(dependencies.repository),
                )
            }
        },
    )
    val writeState by settings.writeState.collectAsStateWithLifecycle()
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
        confirmReaderExit = writeState.requestedApp.confirmReaderExit,
    )
    if (chrome.overlay == ReaderOverlay.READER_SETTINGS) {
        ReaderSettingsSheet(
            preferences = writeState.requestedSnapshot,
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
            saveFailed = writeState.error != null,
            onRetry = settings::retry,
        )
    }
}
