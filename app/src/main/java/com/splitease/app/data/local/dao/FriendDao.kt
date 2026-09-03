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

    /**
     * Finds any friendship pointing at [friendUserId] (for hydrating a local user stub).
     *
     * @param friendUserId The other party's user id.
     * @return Matching friend or null.
     */
    @Query("SELECT * FROM friends WHERE friendUserId = :friendUserId LIMIT 1")
    suspend fun getByFriendUserId(friendUserId: String): FriendEntity?

    /**
     * Finds a friendship by owner + friend user id.
     *
     * @param ownerUserId Owner user id.
     * @param friendUserId The other party's user id.
     * @return Matching friend or null.
     */
    @Query(
        """
        SELECT * FROM friends
        WHERE ownerUserId = :ownerUserId AND friendUserId = :friendUserId
        LIMIT 1
        """,
    )
    suspend fun getByOwnerAndFriendUserId(
        ownerUserId: String,
        friendUserId: String,
    ): FriendEntity?

    /**
     * Finds a friendship by owner + email snapshot (case-insensitive).
     *
     * @param ownerUserId Owner user id.
     * @param email Friend email.
     * @return Matching friend or null.
     */
    @Query(
        """
        SELECT * FROM friends
        WHERE ownerUserId = :ownerUserId AND lower(emailSnapshot) = lower(:email)
        LIMIT 1
        """,
    )
    suspend fun getByOwnerAndEmail(ownerUserId: String, email: String): FriendEntity?

    /** Inserts or replaces [friend]. */
    @Upsert
    suspend fun upsert(friend: FriendEntity)

    /** Deletes the friend with [id]. */
    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: String)
}
