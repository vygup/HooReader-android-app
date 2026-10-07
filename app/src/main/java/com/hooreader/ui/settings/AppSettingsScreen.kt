package com.hooreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hooreader.R

@Composable
fun AppSettingsScreen(
    state: SettingsWriteState,
    onConfirmExitChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize().testTag("app_settings")) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
            Text(stringResource(R.string.app_settings_title), style = MaterialTheme.typography.headlineMedium)
            Row(
                Modifier.fillMaxWidth().testTag("confirm_reader_exit").toggleable(
                    value = state.requestedApp.confirmReaderExit,
                    role = Role.Switch,
                    onValueChange = onConfirmExitChange,
                ).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.confirm_reader_exit), Modifier.weight(1f))
                Switch(checked = state.requestedApp.confirmReaderExit, onCheckedChange = null)
            }
            if (state.error != null) {
                Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("app_settings_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
