package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.GroupEntity
import com.splitease.app.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for groups and group memberships.
 */
@Dao
interface GroupDao {
    /** @return Flow of all groups ordered by name. */
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    /** @param id Local UUID. @return Group or null. */
    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GroupEntity?

    /** Inserts or replaces [group]. */
    @Upsert
    suspend fun upsert(group: GroupEntity)

    /** Deletes the group with [id] (members cascade). */
    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: String)

    /** @param groupId Parent group. @return Flow of members. */
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY joinedAtEpochMs ASC")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    /** Inserts or replaces [member]. */
    @Upsert
    suspend fun upsertMember(member: GroupMemberEntity)

    /** Deletes membership [memberId]. */
    @Query("DELETE FROM group_members WHERE id = :memberId")
    suspend fun deleteMemberById(memberId: String)
}
