package com.splitease.app.data.media

import android.content.Context
import com.splitease.app.data.pinboard.isRemotePinImagePath
import com.splitease.app.data.pinboard.pinBoardImagePaths
import com.splitease.app.data.remote.ExpenseReceiptStorage
import com.splitease.app.data.remote.GroupCoverStorage
import com.splitease.app.data.remote.PinBoardImageStorage
import com.splitease.app.data.remote.StorageObjectPaths
import com.splitease.app.data.remote.mapper.isRemoteMediaUrl
import com.splitease.app.domain.model.ExpensePhoto
import com.splitease.app.domain.model.Group
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort cleanup of Supabase Storage objects and local media files when entities
 * are deleted or media is removed.
 */
@Singleton
class MediaStorageCleanup
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val receiptStorage: ExpenseReceiptStorage,
        private val groupCoverStorage: GroupCoverStorage,
        private val pinBoardImageStorage: PinBoardImageStorage,
    ) {
        /** Deletes cloud receipts, local JPEGs, and remote-image cache entries for [photos]. */
        suspend fun purgeExpenseAttachments(
            expenseId: String,
            photos: List<ExpensePhoto>,
        ) {
            // Always attempt Storage cleanup — Room may be empty while objects still exist.
            receiptStorage.deleteAllForExpense(expenseId, photos.map { it.id })
            photos.forEach { photo ->
                photo.remoteUrl?.let { AvatarImageIO.evictRemoteCache(context, it) }
                LocalMediaCleanup.deleteLocalFile(context, photo.localPath)
            }
            LocalMediaCleanup.deleteExpensePhotoDir(context, expenseId)
        }

        /** Deletes group photo, pin-board images, leftover cover files, and related local folders. */
        suspend fun purgeGroupMedia(
            group: Group,
            pinBoardContent: String?,
        ) {
            val groupId = group.id
            runCatching { groupCoverStorage.deleteAllForGroup(groupId) }
            pinBoardContent?.let { content ->
                val imageIds =
                    pinBoardImagePaths(content)
                        .filter { isRemotePinImagePath(it) }
                        .mapNotNull { url ->
                            StorageObjectPaths.pinBoardImageIdFromPublicUrl(url, groupId)
                        }
                        .distinct()
                pinBoardImageStorage.deleteImages(groupId, imageIds)
            }
            group.photoUrl?.takeIf { it.isRemoteMediaUrl() }?.let {
                AvatarImageIO.evictRemoteCache(context, it)
            }
            LocalMediaCleanup.deleteGroupCoverFiles(context, groupId)
            LocalMediaCleanup.deleteGroupPhotoFiles(context, groupId)
            LocalMediaCleanup.deletePinBoardLocalDir(context, groupId)
        }

        /** Clears a single remote URL from the decode cache (e.g. after replacing a photo). */
        fun evictRemoteMediaCache(remoteUrl: String?) {
            val url = remoteUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (!url.isRemoteMediaUrl()) return
            AvatarImageIO.evictRemoteCache(context, url)
        }

        /** Removes a profile photo from local disk and any remote-image cache slot. */
        fun purgeProfilePhoto(photoUrl: String?) {
            val url = photoUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (url.isRemoteMediaUrl()) {
                AvatarImageIO.evictRemoteCache(context, url)
            } else {
                LocalMediaCleanup.deleteLocalFile(context, url)
            }
        }

        /** Deletes all avatar JPEGs stored for [userId] on this device. */
        fun purgeAllUserAvatars(userId: String) {
            LocalMediaCleanup.deleteUserAvatars(context, userId, keepNewest = 0)
        }
    }
