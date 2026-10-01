package com.hooreader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hooreader.ui.library.ImportBookLauncher
import com.hooreader.ui.library.LibraryViewModel
import com.hooreader.ui.library.importFromPicker
import com.hooreader.ui.reader.ReaderScreen
import com.hooreader.ui.reader.ReaderViewModel

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
                val model: ReaderViewModel = viewModel(
                    key = bookId,
                    factory = viewModelFactory {
                        initializer { ReaderViewModel(bookId, dependencies.repository, dependencies.parsers) }
                    },
                )
                ReaderScreen(model, onBack = onBack)
            }
        }
    }
}
