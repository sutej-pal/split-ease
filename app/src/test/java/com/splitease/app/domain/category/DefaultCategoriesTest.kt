package com.splitease.app.domain.category

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultCategoriesTest {
    @Test
    fun stableIds_areRecognized() {
        assertTrue(DefaultCategories.isStableId("cat_food"))
        assertFalse(DefaultCategories.isStableId("random-uuid"))
    }

    @Test
    fun categoryIdForCloud_passesStableOnly() {
        assertEquals("cat_travel", DefaultCategories.categoryIdForCloud("cat_travel"))
        assertNull(DefaultCategories.categoryIdForCloud("550e8400-e29b-41d4-a716-446655440000"))
        assertNull(DefaultCategories.categoryIdForCloud(null))
    }

    @Test
    fun stableIdForName_isCaseInsensitive() {
        assertEquals("cat_rent", DefaultCategories.stableIdForName("rent"))
        assertEquals("cat_food", DefaultCategories.stableIdForName("FOOD"))
        assertNull(DefaultCategories.stableIdForName("Groceries"))
    }

    @Test
    fun allDefaults_haveUniqueIdsAndNames() {
        val ids = DefaultCategories.ALL.map { it.id }
        val names = DefaultCategories.ALL.map { it.name.lowercase() }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
        assertEquals(8, DefaultCategories.ALL.size)
    }
}
