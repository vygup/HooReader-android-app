package com.hooreader.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hooreader.R

@Composable
fun HooReaderNavHost(
    navController: NavHostController = rememberNavController(),
    libraryContent: @Composable ((String) -> Unit) -> Unit = { LibraryDestination() },
    readerContent: @Composable (String, () -> Unit) -> Unit = { _, onBack -> ReaderDestination(onBack) },
) {
    NavHost(navController = navController, startDestination = HooReaderRoutes.LIBRARY) {
        composable(HooReaderRoutes.LIBRARY) {
            libraryContent { bookId ->
                navController.navigate(HooReaderRoutes.reader(bookId)) { launchSingleTop = true }
            }
        }
        composable(
            route = HooReaderRoutes.READER,
            arguments = listOf(navArgument(HooReaderRoutes.BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = requireNotNull(entry.arguments?.getString(HooReaderRoutes.BOOK_ID))
            readerContent(bookId) { navController.popBackStack() }
        }
    }
}

@Composable
private fun LibraryDestination() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.library_description))
        }
    }
}

@Composable
private fun ReaderDestination(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.reader_unavailable), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onBack) { Text(stringResource(R.string.back_to_library)) }
        }
    }
}
