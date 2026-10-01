package com.hooreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.hooreader.data.local.ReaderPreferencesRepository
import com.hooreader.navigation.HooReaderNavHost
import com.hooreader.ui.theme.HooReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { ReaderPreferencesRepository(applicationContext) }
            HooReaderTheme(preferences) {
                HooReaderNavHost()
            }
        }
    }
}
