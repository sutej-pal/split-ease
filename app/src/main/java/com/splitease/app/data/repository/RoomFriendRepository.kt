package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.FriendDao
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [FriendRepository].
 *
 * @property friendDao Local friends DAO.
 */
@Singleton
class RoomFriendRepository
    @Inject
    constructor(
        private val friendDao: FriendDao,
    ) : FriendRepository {
        override fun observeFriends(ownerUserId: String): Flow<List<Friend>> =
            friendDao.observeByOwner(ownerUserId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun getById(id: String): Friend? = friendDao.getById(id)?.toDomain()

        override suspend fun getByOwnerAndEmail(ownerUserId: String, email: String): Friend? =
            friendDao.getByOwnerAndEmail(ownerUserId, email)?.toDomain()

        override suspend fun upsert(friend: Friend) {
            friendDao.upsert(friend.toEntity())
        }

        override suspend fun deleteById(id: String) {
            friendDao.deleteById(id)
        }
    }
