package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.UserDao
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.model.User
import com.splitease.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [UserRepository].
 *
 * @property userDao Local users DAO.
 */
@Singleton
class RoomUserRepository
    @Inject
    constructor(
        private val userDao: UserDao,
    ) : UserRepository {
        override fun observeUsers(): Flow<List<User>> = userDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override fun observeUserById(id: String): Flow<User?> =
            userDao.observeById(id).map { it.firstOrNull()?.toDomain() }

        override suspend fun getUserById(id: String): User? = userDao.getById(id)?.toDomain()

        override suspend fun getUserByEmail(email: String): User? = userDao.getByEmail(email)?.toDomain()

        override suspend fun upsert(user: User) {
            userDao.upsert(user.toEntity())
        }

        override suspend fun deleteById(id: String) {
            userDao.deleteById(id)
        }
    }
