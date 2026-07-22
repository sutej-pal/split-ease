package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Friend
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to [Friend] relationships for a given owner.
 */
interface FriendRepository {
    /**
     * Observes friends belonging to [ownerUserId].
     *
     * @param ownerUserId Local user who owns the friend list.
     * @return Cold [Flow] of friends ordered by display name.
     */
    fun observeFriends(ownerUserId: String): Flow<List<Friend>>

    /**
     * Loads a friendship by id.
     *
     * @param id Local UUID.
     * @return The friend row, or null.
     */
    suspend fun getById(id: String): Friend?

    /**
     * Inserts or replaces a friendship.
     *
     * @param friend Domain friend to persist.
     */
    suspend fun upsert(friend: Friend)

    /**
     * Deletes a friendship by id.
     *
     * @param id Local UUID.
     */
    suspend fun deleteById(id: String)
}
