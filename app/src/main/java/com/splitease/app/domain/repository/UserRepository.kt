package com.splitease.app.domain.repository

import com.splitease.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to [User] records.
 */
interface UserRepository {
    /**
     * Observes all locally cached users.
     *
     * @return Cold [Flow] that emits the full user list on each change.
     */
    fun observeUsers(): Flow<List<User>>

    /** @param id Local UUID. @return Flow of matching user or null. */
    fun observeUserById(id: String): Flow<User?>

    /**
     * Loads a single user by local id.
     *
     * @param id Local UUID.
     * @return The user, or null if missing.
     */
    suspend fun getUserById(id: String): User?

    /**
     * Loads a user by email (case-sensitive match against stored value).
     *
     * @param email Email address.
     * @return The user, or null if missing.
     */
    suspend fun getUserByEmail(email: String): User?

    /**
     * Inserts or replaces a user row.
     *
     * @param user Domain user to persist.
     */
    suspend fun upsert(user: User)

    /**
     * Deletes a user by local id.
     *
     * @param id Local UUID.
     */
    suspend fun deleteById(id: String)
}
