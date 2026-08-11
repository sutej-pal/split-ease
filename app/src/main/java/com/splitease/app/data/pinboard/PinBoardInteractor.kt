package com.splitease.app.data.pinboard

import android.content.Context
import com.splitease.app.data.media.AvatarImageIO
import com.splitease.app.data.remote.PinBoardImageStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates pin board load / save. Stateless — persistence is triggered by explicit Save.
 */
@Singleton
class PinBoardInteractor
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val remote: PinBoardRemoteDataSource,
        private val imageStorage: PinBoardImageStorage,
    ) {
        /**
         * Loads the board content and metadata for [groupId].
         *
         * @return The DTO, or a blank board stub when none exists yet.
         */
        suspend fun load(groupId: String): PinBoardDto =
            remote.fetch(groupId) ?: PinBoardDto(groupId = groupId, content = "")

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
            val synced =
                syncPinBoardImagePaths(content) { localPath ->
                    uploadLocalImage(groupId, localPath)
                }
            remote.upsert(
                PinBoardDto(
                    groupId = groupId,
                    content = synced,
                    updatedBy = userId,
                ),
            )
            return synced
        }
    }
