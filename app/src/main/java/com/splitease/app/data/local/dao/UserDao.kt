package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `users` table.
 */
@Dao
interface UserDao {
    /** @return Flow of all users ordered by display name. */
    @Query("SELECT * FROM users ORDER BY displayName ASC")
    fun observeAll(): Flow<List<UserEntity>>

    /** @param id Local UUID. @return Matching user or null. */
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?

    /** @param email Stored email. @return Matching user or null. */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    /** Inserts or replaces [user]. */
    @Upsert
    suspend fun upsert(user: UserEntity)

    /** Deletes the user with [id]. */
    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)
}
