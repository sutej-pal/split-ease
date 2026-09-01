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
 * **Online-only** — see [PinBoardPolicy]. No Room cache, no [com.splitease.app.data.sync.SyncInteractor]
 * flush path. Each [load] / [save] hits PostgREST directly.
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
        /**
         * Loads the board content and metadata for [groupId].
         *
         * Hits Room first, then refreshes from remote.
         *
         * @return The DTO, or a blank board stub when none exists yet.
         */
        suspend fun load(groupId: String): PinBoardDto {
            val local = pinBoardDao.getPinBoard(groupId)
            if (local != null) {
                return PinBoardDto(
                    groupId = local.groupId,
                    content = local.content,
                    updatedBy = local.updatedByUserId,
                    updatedAt = local.updatedAtEpochMs.toString(), // Simplify for now
                )
            }
            val dto = remote.fetch(groupId) ?: PinBoardDto(groupId = groupId, content = "")
            // Seed Room with cloud content
            pinBoardDao.upsert(
                PinBoardEntity(
                    groupId = dto.groupId,
                    content = dto.content,
                    updatedByUserId = dto.updatedBy,
                    updatedAtEpochMs = System.currentTimeMillis(), // We don't have a reliable long from DTO
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
            return dto
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
