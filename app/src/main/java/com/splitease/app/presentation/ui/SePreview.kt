package com.splitease.app.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseTheme

/**
 * Wraps [content] in [SplitEaseTheme] for Android Studio / Compose previews.
 */
@Composable
fun SePreview(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    SplitEaseTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Preview(name = "Preview shell · light", showBackground = true)
@Composable
private fun SePreviewLightPreview() {
    SePreview {
        Box(modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(24.dp))
    }
}

@Preview(name = "Preview shell · dark", showBackground = true)
@Composable
private fun SePreviewDarkPreview() {
    SePreview(darkTheme = true) {
        Box(modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(24.dp))
    }
}
