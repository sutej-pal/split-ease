package com.splitease.app.data.push

import com.google.firebase.messaging.FirebaseMessaging
import com.splitease.app.data.remote.DeviceTokenRemoteDataSource
import com.splitease.app.data.remote.dto.DeviceTokenDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers / refreshes FCM tokens in Supabase `device_tokens` while signed in.
 * No-ops when Firebase is not configured (`google-services.json` missing).
 *
 * Upserts only after [SessionStatus.Authenticated] has a non-blank JWT so the
 * PostgREST call is not raced ahead of the password-grant / session attach.
 */
@Singleton
class PushTokenRegistrar
    @Inject
    constructor(
        private val supabase: SupabaseClient,
        private val remote: DeviceTokenRemoteDataSource,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Observes auth and keeps the current device token synced. */
        fun start() {
            scope.launch {
                supabase.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val accessToken = status.session.accessToken
                            val userId = status.session.user?.id
                            if (accessToken.isBlank() || userId.isNullOrBlank()) return@collect
                            runCatching { registerForUser(userId) }
                        }
                        is SessionStatus.NotAuthenticated,
                        is SessionStatus.RefreshFailure,
                        -> {
                            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
                        }
                        else -> Unit
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
                    val session = supabase.auth.currentSessionOrNull() ?: return@launch
                    val userId = session.user?.id ?: return@launch
                    if (session.accessToken.isBlank()) return@launch
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
