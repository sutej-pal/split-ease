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

    /**
     * Newest [limit] events for [userId] (actor or involved participant).
     */
    @Query(
        """
        SELECT * FROM activity_events
        WHERE actorUserId = :userId
           OR involvedUserIds LIKE '%' || :userIdToken || '%'
        ORDER BY sortEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentForUser(
        userId: String,
        userIdToken: String,
        limit: Int,
    ): Flow<List<ActivityEventEntity>>

    /** Fetches a single event by id. */
    @Query("SELECT * FROM activity_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ActivityEventEntity?

    /** Gets events that are waiting to be flushed to the cloud. */
    @Query("SELECT * FROM activity_events WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<ActivityEventEntity>

    /** Marks all events for a user as seen. */
    @Query(
        """
        UPDATE activity_events SET isSeen = 1
        WHERE (actorUserId = :userId OR involvedUserIds LIKE '%' || :userIdToken || '%')
          AND isSeen = 0
        """
    )
    suspend fun markAllAsSeen(userId: String, userIdToken: String)

    /** Number of unseen events for a user. */
    @Query(
        """
        SELECT COUNT(*) FROM activity_events
        WHERE (actorUserId = :userId OR involvedUserIds LIKE '%' || :userIdToken || '%')
          AND isSeen = 0
        """
    )
    fun observeUnseenCount(userId: String, userIdToken: String): Flow<Int>
}
