package com.splitease.app.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.splitease.app.data.remote.DeviceTokenRemoteDataSource
import com.splitease.app.data.remote.dto.DeviceTokenDto
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers / refreshes FCM tokens in Supabase `device_tokens` while signed in.
 * No-ops when Firebase is not configured (`google-services.json` missing).
 */
@Singleton
class PushTokenRegistrar
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val remote: DeviceTokenRemoteDataSource,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Observes auth and keeps the current device token synced. */
        fun start() {
            scope.launch {
                authRepository
                    .observeSession()
                    .map { (it as? AuthSession.SignedIn)?.user?.userId }
                    .distinctUntilChanged()
                    .collect { userId ->
                        if (userId == null) {
                            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
                        } else {
                            runCatching { registerForUser(userId) }
                        }
                    }
            }
        }

        /**
         * Uploads a refreshed FCM token for the signed-in user.
         *
         * @param token New FCM registration token.
         */
        fun onNewToken(token: String) {
            scope.launch {
                runCatching {
                    val userId =
                        (authRepository.observeSession().first() as? AuthSession.SignedIn)
                            ?.user
                            ?.userId
                            ?: return@launch
                    upsertToken(userId, token)
                }
            }
        }

        private suspend fun registerForUser(userId: String) {
            val token = FirebaseMessaging.getInstance().token.await()
            upsertToken(userId, token)
        }

        private suspend fun upsertToken(
            userId: String,
            token: String,
        ) {
            val stableId =
                java.util.UUID
                    .nameUUIDFromBytes("$userId:$token".toByteArray(Charsets.UTF_8))
                    .toString()
            remote.upsert(
                DeviceTokenDto(
                    id = stableId,
                    userId = userId,
                    token = token,
                    platform = "android",
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }
