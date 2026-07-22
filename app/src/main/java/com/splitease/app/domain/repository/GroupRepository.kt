package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to groups and memberships.
 */
interface GroupRepository {
    /**
     * Observes all locally cached groups.
     *
     * @return Cold [Flow] of groups ordered by name.
     */
    fun observeGroups(): Flow<List<Group>>

    /**
     * Loads a group by local id.
     *
     * @param id Local UUID.
     * @return The group, or null.
     */
    suspend fun getGroupById(id: String): Group?

    /**
     * Inserts or replaces a group.
     *
     * @param group Domain group to persist.
     */
    suspend fun upsertGroup(group: Group)

    /**
     * Deletes a group and its members (cascade at DB layer).
     *
     * @param id Local UUID.
     */
    suspend fun deleteGroupById(id: String)

    /**
     * Observes members of a group.
     *
     * @param groupId Parent group id.
     * @return Cold [Flow] of members.
     */
    fun observeMembers(groupId: String): Flow<List<GroupMember>>

    /**
     * Inserts or replaces a membership row.
     *
     * @param member Domain membership.
     */
    suspend fun upsertMember(member: GroupMember)

    /**
     * Removes a membership by id.
     *
     * @param memberId Local UUID of the membership row.
     */
    suspend fun deleteMemberById(memberId: String)
}
