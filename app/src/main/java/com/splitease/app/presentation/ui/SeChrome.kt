package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.presentation.theme.SplitEaseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = if (centered) Modifier.fillMaxSize() else Modifier,
            )
        },
        navigationIcon = {
            when {
                onClose != null ->
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                onBack != null ->
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
            }
        },
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = SplitEaseColors.Navy,
                actionIconContentColor = SplitEaseColors.Navy,
                navigationIconContentColor = SplitEaseColors.Navy,
            ),
    )
}

@Composable
fun SeScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    centeredTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (padding: PaddingValuesAware) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SeTopBar(
                title = title,
                onBack = onBack,
                onClose = onClose,
                centered = centeredTitle,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        content(PaddingValuesAware(padding))
    }
}

/** Thin wrapper so call sites can `padding.values` without importing Scaffold padding types awkwardly. */
data class PaddingValuesAware(
    val values: androidx.compose.foundation.layout.PaddingValues,
)

@Preview(name = "Top bar", showBackground = true)
@Composable
private fun SeTopBarPreview() {
    SePreview {
        Column {
            SeTopBar(title = "Friends", onBack = {})
            SeTopBar(title = "Create a group", onClose = {}, centered = true, actions = {
                SeTextButton(text = "Done", onClick = {})
            })
        }
    }
}

@Preview(name = "Screen scaffold", showBackground = true, heightDp = 320)
@Composable
private fun SeScreenPreview() {
    SePreview {
        SeScreen(
            title = "Account",
            onBack = {},
            content = { padding ->
                Text(
                    text = "Account body",
                    modifier = Modifier.padding(padding.values).padding(8.dp),
                )
            },
        )
    }
}
