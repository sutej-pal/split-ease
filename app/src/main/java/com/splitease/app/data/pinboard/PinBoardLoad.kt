package com.splitease.app.data.pinboard

import com.splitease.app.data.local.entity.PinBoardEntity
import com.splitease.app.domain.model.SyncStatus
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Result of merging a local Room row with a cloud snapshot.
 *
 * @property writeRemoteToCache True when [dto] came from the server and should replace a non-pending local row.
 */
internal data class PinBoardLoadDecision(
    val dto: PinBoardDto,
    val writeRemoteToCache: Boolean,
)

/**
 * Prefers the cloud board unless this device has unsynced edits.
 *
 * Pending local content is kept so an in-progress draft is not wiped by a refresh.
 */
internal fun resolvePinBoardLoad(
    groupId: String,
    local: PinBoardEntity?,
    remote: PinBoardDto?,
): PinBoardLoadDecision {
    if (remote == null) {
        return PinBoardLoadDecision(
            dto = local?.toPinBoardDto() ?: PinBoardDto(groupId = groupId, content = ""),
            writeRemoteToCache = false,
        )
    }
    if (local?.syncStatus == SyncStatus.PENDING) {
        return PinBoardLoadDecision(local.toPinBoardDto(), writeRemoteToCache = false)
    }
    return PinBoardLoadDecision(remote, writeRemoteToCache = true)
}

internal fun PinBoardEntity.toPinBoardDto(): PinBoardDto =
    PinBoardDto(
        groupId = groupId,
        content = content,
        updatedBy = updatedByUserId,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs).toString(),
    )

/** Parses a PostgREST timestamptz, or null when missing/unreadable. */
internal fun parsePinBoardUpdatedAtEpochMs(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}
