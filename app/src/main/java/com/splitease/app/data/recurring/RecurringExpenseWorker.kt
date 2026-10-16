package com.splitease.app.data.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.splitease.app.data.expense.ExpenseInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily worker that materializes due recurring expense templates.
 */
@HiltWorker
class RecurringExpenseWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val expenseInteractor: ExpenseInteractor,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            runCatching {
                expenseInteractor.generateDueRecurringExpenses()
                Result.success()
            }.getOrElse { Result.retry() }

        companion object {
            const val UNIQUE_NAME = "splitease_recurring_expenses"

            /** Enqueues (or keeps) the daily recurring generation job. */
            fun enqueue(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<RecurringExpenseWorker>(1, TimeUnit.DAYS)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
