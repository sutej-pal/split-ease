package com.splitease.app.data.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActivityNotifyPlanTest {
    @Test
    fun firstPull_primesCursor_andDoesNotNotifyHistory() {
        val plan =
            planActivityNotifications(
                incoming = listOf(notice(id = "old", actor = "other", group = "g1", sort = 10L)),
                existingIds = emptySet(),
                currentUserId = "me",
                watermarkEpochMs = 0L,
                nowEpochMs = 50L,
            )
        assertEquals(50L, plan.watermarkEpochMs)
        assertTrue(plan.notifications.isEmpty())
    }

    @Test
    fun laterPull_notifiesNewestOtherMemberEventPerGroup() {
        val plan =
            planActivityNotifications(
                incoming =
                    listOf(
                        notice(id = "a", actor = "other", group = "g1", sort = 20L),
                        notice(id = "b", actor = "other", group = "g1", sort = 30L),
                        notice(id = "c", actor = "other", group = "g2", sort = 25L),
                        notice(id = "mine", actor = "me", group = "g1", sort = 40L),
                        notice(id = "seen", actor = "other", group = "g1", sort = 35L),
                        notice(id = "nongroup", actor = "other", group = null, sort = 36L),
                    ),
                existingIds = setOf("seen"),
                currentUserId = "me",
                watermarkEpochMs = 15L,
                nowEpochMs = 100L,
            )
        assertEquals(15L, plan.watermarkEpochMs)
        assertEquals(listOf("b", "c"), plan.notifications.map { it.id }.sorted())
    }

    private fun notice(
        id: String,
        actor: String,
        group: String?,
        sort: Long,
    ) = ActivityNotice(
        id = id,
        actorUserId = actor,
        sortEpochMs = sort,
        groupId = group,
        title = group ?: "",
        body = id,
    )
}
