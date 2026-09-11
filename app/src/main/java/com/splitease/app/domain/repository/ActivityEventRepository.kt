package com.splitease.app.domain.repository

import com.splitease.app.domain.model.ActivityEvent
import kotlinx.coroutines.flow.Flow

/**
 * Activity-feed events (local Room cache, flushed to Supabase).
 */
interface ActivityEventRepository {
    /**
     * Persists an activity event.
     *
     * @param event Event to store.
     */
    suspend fun upsert(event: ActivityEvent)

    /**
     * Observes events involving [userId].
     *
     * @param userId Signed-in user id.
     */
    fun observeForUser(userId: String): Flow<List<ActivityEvent>>

    /**
     * Newest [limit] events involving [userId] (Activity tab).
     */
    fun observeRecentForUser(
        userId: String,
        limit: Int = FeedQueryLimits.UI_FEED,
    ): Flow<List<ActivityEvent>>

    /** Marks all events for [userId] as seen. */
    suspend fun markAllAsSeen(userId: String)

    /** Observes the count of unseen events for [userId]. */
    fun observeUnseenCount(userId: String): Flow<Int>

    /** Returns events that have not been synced to the cloud yet. */
    suspend fun getPendingSync(): List<ActivityEvent>

    /** Returns an event by id, or null if not found. */
    suspend fun getById(id: String): ActivityEvent?
}
