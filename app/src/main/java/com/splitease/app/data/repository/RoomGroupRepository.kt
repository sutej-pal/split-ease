package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.GroupDao
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [GroupRepository].
 *
 * @property groupDao Local groups DAO.
 */
@Singleton
class RoomGroupRepository
    @Inject
    constructor(
        private val groupDao: GroupDao,
    ) : GroupRepository {
        override fun observeGroupsForUser(userId: String): Flow<List<Group>> =
            groupDao.observeGroupsForUser(userId).map { rows -> rows.map { it.toDomain() } }

        override fun observeGroups(): Flow<List<Group>> =
            groupDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override suspend fun getGroupById(id: String): Group? = groupDao.getById(id)?.toDomain()

        override fun observeGroupById(id: String): Flow<Group?> =
            groupDao.observeById(id).map { rows -> rows.firstOrNull()?.toDomain() }

        override suspend fun upsertGroup(group: Group) {
            groupDao.upsert(group.toEntity())
        }

        override suspend fun deleteGroupById(id: String) {
            groupDao.deleteById(id)
        }

        override fun observeMembers(groupId: String): Flow<List<GroupMember>> =
            groupDao.observeMembers(groupId).map { rows -> rows.map { it.toDomain() } }

        override suspend fun upsertMember(member: GroupMember) {
            groupDao.upsertMember(member.toEntity())
        }

        override suspend fun deleteMemberById(memberId: String) {
            groupDao.deleteMemberById(memberId)
        }

        override suspend fun remapMemberUserId(fromUserId: String, toUserId: String) {
            if (fromUserId == toUserId) return
            groupDao.remapMemberUserId(fromUserId, toUserId)
        }

        override suspend fun getMember(groupId: String, userId: String): GroupMember? =
            groupDao.getMember(groupId, userId)?.toDomain()

        override suspend fun getPendingGroups(): List<Group> =
            groupDao.getPendingGroups().map { it.toDomain() }

        override suspend fun getPendingMembers(): List<GroupMember> =
            groupDao.getPendingMembers().map { it.toDomain() }
    }
