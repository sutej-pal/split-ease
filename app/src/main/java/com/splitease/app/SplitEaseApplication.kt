package com.splitease.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.splitease.app.data.recurring.RecurringExpenseWorker
import com.splitease.app.data.settings.SharedPreferencesAppSettingsRepository
import com.splitease.app.data.social.InstallReferrerInviteBootstrap
import com.splitease.app.data.sync.SyncWorker
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Enables Hilt dependency injection for the app graph
 * and configures WorkManager with [HiltWorkerFactory].
 */
@HiltAndroidApp
class SplitEaseApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var installReferrerInviteBootstrap: InstallReferrerInviteBootstrap

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        (appSettingsRepository as? SharedPreferencesAppSettingsRepository)?.applyStoredLocale()
        installReferrerInviteBootstrap.start()
        RecurringExpenseWorker.enqueue(this)
        SyncWorker.enqueuePeriodic(this)
        SyncWorker.enqueueOnce(this)
    }
}
