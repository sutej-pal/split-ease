package com.splitease.app.data.social

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * One-shot Play Install Referrer bootstrap for deferred invite deep links.
 *
 * When a user opens an https invite without the app, the mail-service sends them to
 * Play Store with `referrer=invite_token%3D…`. After install, this reads that referrer
 * once and stores it via [AppSettingsRepository.setPendingInviteToken] — the same path
 * as a live deep link.
 */
@Singleton
class InstallReferrerInviteBootstrap
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Starts the one-shot referrer read. Safe to call from [android.app.Application.onCreate].
         */
        fun start() {
            scope.launch {
                runCatching { captureOnce() }
            }
        }

        /**
         * Reads Install Referrer at most once per install and seeds a pending invite token.
         *
         * Visible for unit tests that inject a fake referrer string via [applyReferrerString].
         */
        suspend fun captureOnce() {
            if (appSettingsRepository.getInstallReferrerChecked()) return
            // Deep link already won — do not overwrite, but still mark checked.
            if (!appSettingsRepository.getPendingInviteToken().isNullOrBlank()) {
                appSettingsRepository.setInstallReferrerChecked(true)
                return
            }
            val referrer =
                withContext(Dispatchers.IO) {
                    runCatching { fetchInstallReferrer() }.getOrNull()
                }
            applyReferrerString(referrer)
            appSettingsRepository.setInstallReferrerChecked(true)
        }

        /**
         * Parses [referrer] and stores a pending invite token when present.
         *
         * @param referrer Raw Play Install Referrer campaign string, or null.
         */
        suspend fun applyReferrerString(referrer: String?) {
            if (!appSettingsRepository.getPendingInviteToken().isNullOrBlank()) return
            val token = InviteLinks.tokenFromInstallReferrer(referrer) ?: return
            appSettingsRepository.setPendingInviteToken(token)
        }

        private suspend fun fetchInstallReferrer(): String? =
            suspendCancellableCoroutine { cont ->
                val client = InstallReferrerClient.newBuilder(context).build()
                val finished = AtomicBoolean(false)
                fun complete(value: String?) {
                    if (!finished.compareAndSet(false, true)) return
                    runCatching { client.endConnection() }
                    if (cont.isActive) cont.resume(value)
                }
                cont.invokeOnCancellation {
                    runCatching { client.endConnection() }
                }
                client.startConnection(
                    object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            if (responseCode != InstallReferrerResponse.OK) {
                                complete(null)
                                return
                            }
                            val referrer =
                                runCatching { client.installReferrer.installReferrer }.getOrNull()
                            complete(referrer)
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            complete(null)
                        }
                    },
                )
            }
    }
