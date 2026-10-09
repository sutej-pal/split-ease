package com.splitease.app.data.pinboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinBoardImagePathsTest {
    @Test
    fun normalize_file_uri_to_absolute_path() {
        assertEquals(
            "/data/user/0/com.splitease.app/files/pinboard/g/uuid.jpg",
            normalizePinImagePath("file:///data/user/0/com.splitease.app/files/pinboard/g/uuid.jpg"),
        )
    }

    @Test
    fun is_remote_detects_https() {
        assertTrue(isRemotePinImagePath("https://example.com/pin-board-images/g/uuid.jpg"))
    }
}
