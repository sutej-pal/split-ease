package com.splitease.app.data.pinboard

import android.content.Context
import com.splitease.app.data.local.dao.PinBoardDao
import com.splitease.app.data.local.entity.PinBoardEntity
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.remote.PinBoardImageStorage
import com.splitease.app.domain.model.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates pin board load / save.
 *
 * Offline-first — see [PinBoardPolicy]. Writes go to Room then PostgREST;
 * [load] always tries the server so another member’s save is picked up.
 */
@Singleton
class PinBoardInteractor
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val remote: PinBoardRemoteDataSource,
        private val imageStorage: PinBoardImageStorage,
        private val pinBoardDao: PinBoardDao,
    ) {
        /** Cached board for [groupId], or null when this device has never opened it. */
        suspend fun peekLocal(groupId: String): PinBoardDto? = pinBoardDao.getPinBoard(groupId)?.toPinBoardDto()

        /**
         * Loads the board for [groupId].
         *
         * Fetches Supabase when possible and caches it. Pending local edits win over
         * remote so an in-progress draft is not discarded. Falls back to Room offline.
         */
        suspend fun load(groupId: String): PinBoardDto {
            val local = pinBoardDao.getPinBoard(groupId)
            val remoteDto = runCatching { remote.fetch(groupId) }.getOrNull()
            val decision = resolvePinBoardLoad(groupId, local, remoteDto)
            if (decision.writeRemoteToCache) {
                val dto = decision.dto
                pinBoardDao.upsert(
                    PinBoardEntity(
                        groupId = dto.groupId,
                        content = dto.content,
                        updatedByUserId = dto.updatedBy,
                        updatedAtEpochMs = parsePinBoardUpdatedAtEpochMs(dto.updatedAt) ?: System.currentTimeMillis(),
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
            return decision.dto
        }

        /** Saves locally and enqueues a sync. */
        suspend fun saveLocal(
            groupId: String,
            content: String,
            userId: String,
        ) {
            pinBoardDao.upsert(
                PinBoardEntity(
                    groupId = groupId,
                    content = content,
                    updatedByUserId = userId,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING,
                ),
            )
        }

        /** Syncs a specific group's board to the cloud. */
        suspend fun sync(groupId: String): String? {
            val local = pinBoardDao.getPinBoard(groupId) ?: return null
            if (local.syncStatus == SyncStatus.SYNCED) return local.content

            val synced =
                syncPinBoardImagePaths(local.content) { localPath ->
                    uploadLocalImage(groupId, localPath)
                }
            remote.upsert(
                PinBoardDto(
                    groupId = groupId,
                    content = synced,
                    updatedBy = local.updatedByUserId,
                ),
            )
            pinBoardDao.upsert(local.copy(content = synced, syncStatus = SyncStatus.SYNCED))
            return synced
        }

        /** Syncs all pending pin boards. */
        suspend fun flushPending(): Int {
            val pending = pinBoardDao.getBySyncStatus(SyncStatus.PENDING)
            pending.forEach { sync(it.groupId) }
            return pending.size
        }

        /**
         * Uploads a single local JPEG and returns its public URL, or null on failure.
         *
         * Seeds the remote-URL decode cache so the UI can show the image immediately.
         */
        suspend fun uploadLocalImage(
            groupId: String,
            localPath: String,
        ): String? {
            val normalized = normalizePinImagePath(localPath)
            val file = File(normalized)
            if (!file.isFile) return null
            return runCatching {
                val remoteUrl =
                    imageStorage.upload(
                        groupId = groupId,
                        imageId = file.nameWithoutExtension.ifBlank { file.name },
                        localJpegPath = normalized,
                    )
                AvatarImageIO.seedRemoteImageCache(appContext, remoteUrl, file)
                remoteUrl
            }.getOrNull()
        }

        /**
         * Persists [content] for [groupId], uploading any local image paths first.
         *
         * @return Saved markdown (remote image URLs when upload succeeded).
         */
        suspend fun save(
            groupId: String,
            content: String,
            userId: String,
        ): String {
            saveLocal(groupId, content, userId)
            return sync(groupId) ?: content
        }
    }
