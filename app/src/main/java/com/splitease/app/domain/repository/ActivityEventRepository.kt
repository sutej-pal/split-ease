package com.splitease.app.domain.repository

import com.splitease.app.domain.model.ActivityEvent
import kotlinx.coroutines.flow.Flow

/**
 * Local activity-feed events.
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
}
