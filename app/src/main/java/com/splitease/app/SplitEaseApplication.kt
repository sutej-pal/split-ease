package com.splitease.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
import com.splitease.app.data.media.SupabaseImageAuth
import com.splitease.app.data.push.NotificationPrefsCoordinator
import com.splitease.app.data.push.PushTokenRegistrar
import com.splitease.app.data.push.SplitEaseNotificationChannels
import com.splitease.app.data.recurring.RecurringExpenseWorker
import com.splitease.app.data.settings.SharedPreferencesAppSettingsRepository
import com.splitease.app.data.social.InstallReferrerInviteBootstrap
import com.splitease.app.data.sync.SyncWorker
import com.splitease.app.domain.settings.AppSettingsRepository
import com.splitease.app.presentation.ads.AdConfig
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    @Inject
    lateinit var notificationPrefsCoordinator: NotificationPrefsCoordinator

    @Inject
    lateinit var supabaseClient: SupabaseClient

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        SupabaseImageAuth.update(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        (appSettingsRepository as? SharedPreferencesAppSettingsRepository)?.applyStoredLocale()
        installReferrerInviteBootstrap.start()
        SplitEaseNotificationChannels.ensure(this)
        pushTokenRegistrar.start()
        notificationPrefsCoordinator.start()
        RecurringExpenseWorker.enqueue(this)
        SyncWorker.enqueuePeriodic(this)
        SyncWorker.enqueueOnce(this)
        if (AdConfig.isEnabled) {
            applicationScope.launch(Dispatchers.IO) {
                MobileAds.initialize(this@SplitEaseApplication)
            }
        }
    }
}
