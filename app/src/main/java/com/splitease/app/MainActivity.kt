package com.splitease.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.splitease.app.presentation.navigation.SplitEaseNavHost
import com.splitease.app.presentation.theme.SplitEaseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for all Compose destinations.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitEaseTheme(darkTheme = false, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SplitEaseNavHost()
                }
            }
        }
    }
}
