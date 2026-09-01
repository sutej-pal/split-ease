package com.splitease.app.data.session

import android.content.Context
import com.splitease.app.data.local.db.SplitEaseDatabase
import com.splitease.app.data.media.LocalMediaCleanup
import com.splitease.app.data.sync.SyncInteractor
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes local user data on sign-out so the next account starts clean.
 *
 * Clears Room rows, on-disk media/cache, and user-scoped preferences.
 * Device-level prefs (theme, locale, install referrer), pending invites, and a
 * pending welcome-email user id are preserved via [AppSettingsRepository.clearSessionData].
 */
@Singleton
class LocalUserDataCleanup
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: SplitEaseDatabase,
        private val appSettingsRepository: AppSettingsRepository,
        private val syncInteractor: SyncInteractor,
    ) {
        /** Best-effort full local wipe for the signed-out account. */
        suspend fun clearAll() {
            withContext(Dispatchers.IO) {
                runCatching { database.clearAllTables() }
                runCatching { LocalMediaCleanup.deleteAllUserMedia(context) }
            }
            runCatching { appSettingsRepository.clearSessionData() }
            runCatching { syncInteractor.resetSession() }
        }
    }
