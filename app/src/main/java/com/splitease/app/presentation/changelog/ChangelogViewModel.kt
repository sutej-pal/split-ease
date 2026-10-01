package com.splitease.app.presentation.changelog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitease.app.BuildConfig
import com.splitease.app.data.changelog.AssetChangelogRepository
import com.splitease.app.data.changelog.WhatsNewStore
import com.splitease.app.domain.changelog.ChangelogParser
import com.splitease.app.domain.changelog.ChangelogRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChangelogUiState(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val releases: List<ChangelogRelease> = emptyList(),
    val promptRelease: ChangelogRelease? = null,
    val isLoading: Boolean = true,
)

/**
 * Loads the packaged changelog and decides whether to show a post-update prompt.
 */
@HiltViewModel
class ChangelogViewModel
    @Inject
    constructor(
        private val changelogRepository: AssetChangelogRepository,
        private val whatsNewStore: WhatsNewStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChangelogUiState())
        val state: StateFlow<ChangelogUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        /**
         * Acknowledges the current build so the What's new dialog does not show again.
         */
        fun dismissPrompt() {
            whatsNewStore.markSeen(BuildConfig.VERSION_CODE)
            _state.update { it.copy(promptRelease = null) }
        }

        private suspend fun load() {
            val markdown = withContext(Dispatchers.IO) { changelogRepository.readMarkdown() }
            val shipped = ChangelogParser.parseShipped(markdown)
            val currentCode = BuildConfig.VERSION_CODE
            val currentName = BuildConfig.VERSION_NAME
            val currentRelease =
                shipped.firstOrNull { it.versionCode == currentCode }
                    ?: shipped.firstOrNull { it.versionName == currentName }
                    ?: shipped.firstOrNull()
            val lastSeen = whatsNewStore.lastSeenVersionCode()
            val prompt =
                if (lastSeen == 0) {
                    whatsNewStore.markSeen(currentCode)
                    null
                } else if (lastSeen < currentCode) {
                    currentRelease
                } else {
                    null
                }
            _state.value =
                ChangelogUiState(
                    versionName = currentName,
                    versionCode = currentCode,
                    releases = shipped,
                    promptRelease = prompt,
                    isLoading = false,
                )
        }
    }
