package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to groups and memberships.
 */
interface GroupRepository {
    /**
     * Observes groups the given user belongs to.
     *
     * @param userId Member user id.
     * @return Cold [Flow] of groups ordered by name.
     */
    fun observeGroupsForUser(userId: String): Flow<List<Group>>

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
     * Observes a single group by id.
     *
     * @param id Local UUID.
     * @return Cold [Flow] emitting the group or null when missing.
     */
    fun observeGroupById(id: String): Flow<Group?>

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

    /**
     * Remaps membership user ids after invite accept.
     *
     * @param fromUserId Placeholder id.
     * @param toUserId Real user id.
     */
    suspend fun remapMemberUserId(fromUserId: String, toUserId: String)

    /**
     * Loads a membership if present.
     *
     * @param groupId Group id.
     * @param userId Member user id.
     * @return Membership or null.
     */
    suspend fun getMember(groupId: String, userId: String): GroupMember?

    /**
     * Groups that still need a cloud upsert.
     *
     * @return Pending / local-only groups, oldest first.
     */
    suspend fun getPendingGroups(): List<Group>

    /**
     * Memberships that still need a cloud upsert.
     *
     * @return Pending / local-only members, oldest first.
     */
    suspend fun getPendingMembers(): List<GroupMember>
}
