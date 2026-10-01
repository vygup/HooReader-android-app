package com.hooreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hooreader.navigation.HooReaderNavHost
import com.hooreader.ui.theme.HooReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HooReaderTheme {
                HooReaderNavHost()
            }
        }
    }
}
