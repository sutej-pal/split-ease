package com.splitease.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.splitease.app.data.recurring.RecurringExpenseWorker
import com.splitease.app.data.sync.SyncWorker
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

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        RecurringExpenseWorker.enqueue(this)
        SyncWorker.enqueuePeriodic(this)
        SyncWorker.enqueueOnce(this)
    }
}
