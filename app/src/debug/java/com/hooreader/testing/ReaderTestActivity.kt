package com.hooreader.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Debug-only host keeps the test content installed across genuine Activity recreation. */
class ReaderTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content?.invoke() }
    }

    companion object {
        var content: (@Composable () -> Unit)? by mutableStateOf(null)
    }
}
