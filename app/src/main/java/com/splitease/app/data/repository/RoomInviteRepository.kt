package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.InviteDao
import com.splitease.app.data.local.entity.InviteEntity
import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.repository.InviteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [InviteRepository].
 */
@Singleton
class RoomInviteRepository
    @Inject
    constructor(
        private val inviteDao: InviteDao,
    ) : InviteRepository {
        override fun observeSentInvites(inviterUserId: String): Flow<List<Invite>> =
            inviteDao.observeByInviter(inviterUserId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getPendingByEmail(email: String): List<Invite> =
            inviteDao.getByEmailAndStatus(email, InviteStatus.PENDING).map { it.toDomain() }

        override suspend fun getByToken(token: String): Invite? = inviteDao.getByToken(token)?.toDomain()

        override suspend fun getByFriendRowId(friendRowId: String): Invite? =
            inviteDao.getByFriendRowId(friendRowId)?.toDomain()

        override suspend fun upsert(invite: Invite) {
            inviteDao.upsert(invite.toEntity())
        }
    }

private fun InviteEntity.toDomain() =
    Invite(
        id = id,
        token = token,
        inviterUserId = inviterUserId,
        email = email,
        kind = kind,
        groupId = groupId,
        friendRowId = friendRowId,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        syncStatus = syncStatus,
    )

private fun Invite.toEntity() =
    InviteEntity(
        id = id,
        token = token,
        inviterUserId = inviterUserId,
        email = email,
        kind = kind,
        groupId = groupId,
        friendRowId = friendRowId,
        status = status,
        createdAtEpochMs = createdAtEpochMs,
        syncStatus = syncStatus,
    )
