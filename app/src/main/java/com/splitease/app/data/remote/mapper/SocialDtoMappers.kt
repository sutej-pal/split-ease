package com.splitease.app.data.remote.mapper

import com.splitease.app.data.remote.dto.FriendDto
import com.splitease.app.data.remote.dto.GroupDto
import com.splitease.app.data.remote.dto.GroupMemberDto
import com.splitease.app.data.remote.dto.InviteDto
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.model.SyncStatus

/** Maps domain [Group] to PostgREST [GroupDto]. */
fun Group.toDto(updatedAtEpochMs: Long = this.updatedAtEpochMs): GroupDto =
    GroupDto(
        id = id,
        name = name,
        defaultCurrencyCode = defaultCurrencyCode,
        createdByUserId = createdByUserId,
        updatedAtEpochMs = updatedAtEpochMs,
    )

/** Maps domain [GroupMember] to PostgREST [GroupMemberDto]. */
fun GroupMember.toDto(): GroupMemberDto =
    GroupMemberDto(
        id = id,
        groupId = groupId,
        userId = userId,
        role = role.name,
        joinedAtEpochMs = joinedAtEpochMs,
    )

/** Maps domain [Invite] to PostgREST [InviteDto]. */
fun Invite.toDto(): InviteDto =
    InviteDto(
        id = id,
        token = token,
        inviterUserId = inviterUserId,
        email = email,
        kind = kind.name,
        groupId = groupId,
        friendRowId = friendRowId,
        status = status.name,
        createdAtEpochMs = createdAtEpochMs,
    )

/** Maps PostgREST [InviteDto] to domain [Invite] (cloud rows are treated as synced). */
fun InviteDto.toDomain(): Invite =
    Invite(
        id = id,
        token = token,
        inviterUserId = inviterUserId,
        email = email,
        kind = runCatching { InviteKind.valueOf(kind) }.getOrDefault(InviteKind.FRIEND),
        groupId = groupId,
        friendRowId = friendRowId,
        status = runCatching { InviteStatus.valueOf(status) }.getOrDefault(InviteStatus.PENDING),
        createdAtEpochMs = createdAtEpochMs,
        syncStatus = SyncStatus.SYNCED,
    )

/** Maps domain [Friend] to PostgREST [FriendDto]. */
fun Friend.toDto(updatedAtEpochMs: Long = this.updatedAtEpochMs): FriendDto =
    FriendDto(
        id = id,
        ownerUserId = ownerUserId,
        friendUserId = friendUserId,
        emailSnapshot = emailSnapshot,
        displayNameSnapshot = displayNameSnapshot,
        updatedAtEpochMs = updatedAtEpochMs,
    )
