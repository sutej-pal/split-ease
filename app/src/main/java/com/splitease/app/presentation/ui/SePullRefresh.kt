package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.splitease.app.presentation.theme.SplitEaseColors

/**
 * Material 3 pull-to-refresh wrapper styled for SplitEase screens.
 *
 * @param isRefreshing Whether a refresh is in progress.
 * @param onRefresh Invoked when the user pulls to refresh.
 * @param modifier Modifier for the refresh container.
 * @param content Scrollable content (typically a [androidx.compose.foundation.lazy.LazyColumn]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SePullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = SplitEaseColors.Navy,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
