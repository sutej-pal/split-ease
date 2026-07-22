package com.splitease.app.presentation.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.splitease.app.R
import com.splitease.app.presentation.ui.SeEmptyState
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen

@Composable
fun ActivityScreen() {
    SeScreen(
        title = stringResource(R.string.nav_activity),
        content = { padding ->
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SeEmptyState(message = stringResource(R.string.activity_placeholder))
            }
        },
    )
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ActivityScreenPreview() {
    SePreview { ActivityScreen() }
}
