package com.splitease.app.data.push

import com.splitease.app.data.remote.NotificationPrefsRemoteDataSource
import com.splitease.app.data.remote.dto.NotificationPrefsDto
import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps local mute prefs in sync with Supabase `notification_prefs` so the
 * notify Edge Function can skip muted recipients. [refreshFromRemote] is also
 * called on each FCM message so another device's unmute is not ignored.
 */
@Singleton
class NotificationPrefsCoordinator
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val local: AppSettingsRepository,
        private val remote: NotificationPrefsRemoteDataSource,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Observes auth and hydrates mute prefs from the cloud while signed in. */
        fun start() {
            scope.launch {
                authRepository
                    .observeSession()
                    .map { (it as? AuthSession.SignedIn)?.user?.userId }
                    .distinctUntilChanged()
                    .collect { userId ->
                        if (userId != null) {
                            runCatching { pullAndReconcile(userId) }
                        }
                    }
            }
        }

        /**
         * Reloads mute prefs from the cloud for the signed-in user.
         *
         * Waits until auth has finished restoring so a background FCM start
         * does not read [AuthSession.Loading] and skip the pull.
         */
        suspend fun refreshFromRemote() {
            val session =
                authRepository.observeSession().first { it !is AuthSession.Loading }
            val userId = (session as? AuthSession.SignedIn)?.user?.userId ?: return
            pullAndReconcile(userId)
        }

        /**
         * Sets the mute-all preference and uploads it.
         *
         * @param muted When true, no group pushes are delivered to this user.
         */
        suspend fun setMuteAll(muted: Boolean) {
            local.setNotificationsMutedAll(muted)
            pushRemote()
        }

        /**
         * Mutes or unmutes pushes for one group and uploads the set.
         *
         * @param groupId Group id.
         * @param muted When true, this group is muted.
         */
        suspend fun setGroupMuted(
            groupId: String,
            muted: Boolean,
        ) {
            local.setGroupNotificationsMuted(groupId, muted)
            pushRemote()
        }

        private suspend fun pullAndReconcile(userId: String) {
            val remoteRow = remote.fetch(userId)
            if (remoteRow == null) {
                pushRemote(userId)
                return
            }
            val localUpdated = local.getNotificationPrefsUpdatedAtEpochMs()
            if (remoteRow.updatedAtEpochMs >= localUpdated) {
                local.applyRemoteNotificationPrefs(
                    muteAll = remoteRow.muteAll,
                    mutedGroupIds = remoteRow.mutedGroupIds.toSet(),
                    updatedAtEpochMs = remoteRow.updatedAtEpochMs,
                )
            } else {
                pushRemote(userId)
            }
        }

        private suspend fun pushRemote(userIdOverride: String? = null) {
            val userId = userIdOverride ?: currentUserId() ?: return
            val dto =
                NotificationPrefsDto(
                    userId = userId,
                    muteAll = local.getNotificationsMutedAll(),
                    mutedGroupIds = local.getMutedGroupIds().toList(),
                    updatedAtEpochMs = local.getNotificationPrefsUpdatedAtEpochMs(),
                )
            runCatching { remote.upsert(dto) }
        }

        private suspend fun currentUserId(): String? =
            (authRepository.observeSession().first() as? AuthSession.SignedIn)?.user?.userId
    }
