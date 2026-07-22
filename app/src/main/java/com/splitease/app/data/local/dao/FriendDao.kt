package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `friends` table.
 */
@Dao
interface FriendDao {
    /** @param ownerUserId Owner of the friend list. @return Flow of friends. */
    @Query(
        """
        SELECT * FROM friends
        WHERE ownerUserId = :ownerUserId
        ORDER BY displayNameSnapshot ASC
        """,
    )
    fun observeByOwner(ownerUserId: String): Flow<List<FriendEntity>>

    /** @param id Local UUID. @return Friend or null. */
    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FriendEntity?

    /** Inserts or replaces [friend]. */
    @Upsert
    suspend fun upsert(friend: FriendEntity)

    /** Deletes the friend with [id]. */
    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: String)
}
