package com.splitease.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically flushes PENDING local writes to Supabase.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncInteractor: SyncInteractor,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            runCatching {
                syncInteractor.syncForUser()
                Result.success()
            }.getOrElse { Result.retry() }

        companion object {
            const val PERIODIC_NAME = "splitease_sync_periodic"
            const val ONCE_NAME = "splitease_sync_once"

            fun enqueuePeriodic(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }

            fun enqueueOnce(context: Context) {
                val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }
