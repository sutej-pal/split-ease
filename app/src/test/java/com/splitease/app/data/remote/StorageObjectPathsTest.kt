package com.splitease.app.data.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StorageObjectPathsTest {
    @Test
    fun objectPathFromPublicUrl_extractsKey() {
        val url =
            "https://abc.supabase.co/storage/v1/object/public/expense-receipts/exp-1/photo-2.jpg"
        assertEquals("exp-1/photo-2.jpg", StorageObjectPaths.objectPathFromPublicUrl(url, "expense-receipts"))
    }

    @Test
    fun objectPathFromPublicUrl_ignoresOtherBuckets() {
        val url =
            "https://abc.supabase.co/storage/v1/object/public/group-covers/g1/cover.jpg"
        assertNull(StorageObjectPaths.objectPathFromPublicUrl(url, "expense-receipts"))
    }

    @Test
    fun pinBoardImageIdFromPublicUrl_parsesImageId() {
        val url =
            "https://abc.supabase.co/storage/v1/object/public/pin-board-images/g1/img42.jpg?v=1"
        assertEquals("img42", StorageObjectPaths.pinBoardImageIdFromPublicUrl(url, "g1"))
    }
}
