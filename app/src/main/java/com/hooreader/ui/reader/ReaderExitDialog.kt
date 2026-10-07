package com.hooreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hooreader.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderExitDialog(onContinue: () -> Unit, onExit: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onContinue) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.testTag("reader_exit_dialog")) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.reader_exit_title), style = MaterialTheme.typography.headlineSmall)
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag("reader_continue_reading")
                ) {
                    Text(stringResource(R.string.reader_continue_reading))
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().testTag("reader_confirm_exit")) {
                    Text(stringResource(R.string.reader_confirm_exit))
                }
            }
        }
    }
}
