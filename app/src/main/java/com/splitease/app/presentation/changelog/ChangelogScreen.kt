package com.splitease.app.presentation.changelog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.splitease.app.R
import com.splitease.app.domain.changelog.ChangelogRelease
import com.splitease.app.domain.changelog.ChangelogSection
import com.splitease.app.presentation.theme.SplitEaseColors
import com.splitease.app.presentation.ui.SeConfirmDialog
import com.splitease.app.presentation.ui.SeConfirmTone
import com.splitease.app.presentation.ui.SePreview
import com.splitease.app.presentation.ui.SeScreen
import com.splitease.app.presentation.ui.SeSectionHeader

@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    viewModel: ChangelogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangelogScreenContent(
        versionName = state.versionName,
        versionCode = state.versionCode,
        releases = state.releases,
        onBack = onBack,
    )
}

@Composable
fun WhatsNewPrompt(
    onSeeAll: () -> Unit,
    viewModel: ChangelogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt = state.promptRelease ?: return
    val items = prompt.summaryItems()
    val body =
        if (items.isEmpty()) {
            stringResource(R.string.whats_new_empty)
        } else {
            items.joinToString("\n") { "• $it" }
        }
    SeConfirmDialog(
        title = stringResource(R.string.whats_new_prompt_title, prompt.versionName),
        body = body,
        onDismissRequest = viewModel::dismissPrompt,
        confirmLabel = stringResource(R.string.whats_new_see_all),
        onConfirm = {
            viewModel.dismissPrompt()
            onSeeAll()
        },
        dismissLabel = stringResource(R.string.action_done),
        icon = Icons.Filled.NewReleases,
        tone = SeConfirmTone.Primary,
    )
}

@Composable
private fun ChangelogScreenContent(
    versionName: String,
    versionCode: Int,
    releases: List<ChangelogRelease>,
    onBack: () -> Unit,
) {
    SeScreen(
        title = stringResource(R.string.whats_new_title),
        onBack = onBack,
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding.values)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.whats_new_current_build, versionName, versionCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (releases.isEmpty()) {
                    Text(
                        text = stringResource(R.string.whats_new_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    releases.forEach { release ->
                        val heading =
                            if (release.date.isNullOrBlank()) {
                                stringResource(R.string.whats_new_version, release.versionName)
                            } else {
                                stringResource(
                                    R.string.whats_new_version_dated,
                                    release.versionName,
                                    release.date,
                                )
                            }
                        SeSectionHeader(text = heading)
                        if (release.sections.isEmpty()) {
                            Text(
                                text = stringResource(R.string.whats_new_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            release.sections.forEach { section ->
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SplitEaseColors.NavyMuted,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                                section.items.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        },
    )
}

@Preview(name = "What's new", showBackground = true, heightDp = 720)
@Composable
private fun ChangelogScreenPreview() {
    SePreview {
        ChangelogScreenContent(
            versionName = "1.1.0",
            versionCode = 3,
            releases =
                listOf(
                    ChangelogRelease(
                        versionName = "1.1.0",
                        date = "2026-09-17",
                        versionCode = 3,
                        sections =
                            listOf(
                                ChangelogSection(
                                    title = "Added",
                                    items = listOf("What's new screen after each Play release."),
                                ),
                                ChangelogSection(
                                    title = "Changed",
                                    items = listOf("Room schema bumps wipe the local cache while in development."),
                                ),
                            ),
                    ),
                ),
            onBack = {},
        )
    }
}
