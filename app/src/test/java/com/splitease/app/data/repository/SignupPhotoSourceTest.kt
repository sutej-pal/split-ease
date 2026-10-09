package com.splitease.app.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignupPhotoSourceTest {
    private val ourAvatar =
        "https://abc.supabase.co/storage/v1/object/public/user-avatars/u1/photo.jpg"
    private val googlePicture = "https://lh3.googleusercontent.com/a/photo=s96-c"
    private val pendingPath = "/data/user/0/com.splitease.app/files/avatars/pending_signup.jpg"
    private val cacheUri = "file:///data/user/0/com.splitease.app/cache/avatars/crop_1.jpg"
    private val existingLocal = "/data/user/0/com.splitease.app/files/avatars/u1_1.jpg"

    private fun isOur(url: String): Boolean = url.contains("/user-avatars/")

    @Test
    fun prefersOurStorageUrlOverPendingAndGoogle() {
        assertEquals(
            ourAvatar,
            resolveSignupPhotoSource(
                metaPhotoUrl = ourAvatar,
                pendingPath = pendingPath,
                existingPhotoUrl = existingLocal,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }

    @Test
    fun usesPendingJpegWhenMetadataHasNoRemoteUrl() {
        assertEquals(
            pendingPath,
            resolveSignupPhotoSource(
                metaPhotoUrl = null,
                pendingPath = pendingPath,
                existingPhotoUrl = existingLocal,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }

    @Test
    fun pendingBeatsLegacyCacheFileMetadata() {
        assertEquals(
            pendingPath,
            resolveSignupPhotoSource(
                metaPhotoUrl = cacheUri,
                pendingPath = pendingPath,
                existingPhotoUrl = null,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }

    @Test
    fun googlePictureWinsOverLeftoverPendingFile() {
        assertEquals(
            googlePicture,
            resolveSignupPhotoSource(
                metaPhotoUrl = googlePicture,
                pendingPath = pendingPath,
                existingPhotoUrl = null,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }

    @Test
    fun fallsBackToExistingLocalWhenNothingElse() {
        assertEquals(
            existingLocal,
            resolveSignupPhotoSource(
                metaPhotoUrl = "  ",
                pendingPath = null,
                existingPhotoUrl = existingLocal,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }

    @Test
    fun returnsNullWhenNoPhotoSources() {
        assertNull(
            resolveSignupPhotoSource(
                metaPhotoUrl = null,
                pendingPath = "",
                existingPhotoUrl = null,
                isOurAvatarUrl = ::isOur,
            ),
        )
    }
}
