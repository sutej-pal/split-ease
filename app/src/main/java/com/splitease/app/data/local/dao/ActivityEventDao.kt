package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitease.app.data.local.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ActivityEventEntity].
 */
@Dao
interface ActivityEventDao {
    /** Inserts or replaces an event. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ActivityEventEntity)

    /**
     * Observes events for [userId] (actor or involved participant).
     *
     * @param userId Signed-in user id.
     * @param userIdToken Comma-wrapped token e.g. `,uuid,`.
     */
    @Query(
        """
        SELECT * FROM activity_events
        WHERE actorUserId = :userId
           OR involvedUserIds LIKE '%' || :userIdToken || '%'
        ORDER BY sortEpochMs DESC
        """,
    )
    fun observeForUser(
        userId: String,
        userIdToken: String,
    ): Flow<List<ActivityEventEntity>>
}
