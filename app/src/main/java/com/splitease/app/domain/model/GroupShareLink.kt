package com.splitease.app.domain.model

/**
 * Ready-to-share group invite link payload for UI and share sheets.
 *
 * @property groupName Display name of the group.
 * @property url Absolute https invite URL.
 * @property shareText Plain-text share body including the URL.
 */
data class GroupShareLink(
    val groupName: String,
    val url: String,
    val shareText: String,
)
