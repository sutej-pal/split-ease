package com.splitease.app.domain.category

import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.SyncStatus

/**
 * Built-in expense categories with **stable ids** shared across devices on the wire.
 *
 * Custom categories remain device-local until a future cloud `categories` table exists.
 * Only these ids are written to Supabase `expenses.category_id`.
 */
object DefaultCategories {
    /** Stable default category definition. */
    data class Definition(
        val id: String,
        val name: String,
        val iconKey: String,
    )

    val ALL: List<Definition> =
        listOf(
            Definition("cat_general", "General", "category_general"),
            Definition("cat_food", "Food", "category_food"),
            Definition("cat_travel", "Travel", "category_travel"),
            Definition("cat_rent", "Rent", "category_rent"),
            Definition("cat_utilities", "Utilities", "category_utilities"),
            Definition("cat_entertainment", "Entertainment", "category_entertainment"),
        )

    private val byId = ALL.associateBy { it.id }
    private val byNameLower = ALL.associateBy { it.name.lowercase() }

    /** @return true when [id] is a built-in stable category id. */
    fun isStableId(id: String): Boolean = id in byId

    /** @return Definition for a stable id, or null. */
    fun byId(id: String): Definition? = byId[id]

    /** @return Stable id for a default [name] (case-insensitive), or null. */
    fun stableIdForName(name: String): String? = byNameLower[name.trim().lowercase()]?.id

    /**
     * Maps a local category id to the value stored in Supabase.
     * Custom / legacy ids are omitted (`null`) so peers are not sent unusable UUIDs.
     */
    fun categoryIdForCloud(localCategoryId: String?): String? {
        if (localCategoryId.isNullOrBlank()) return null
        return localCategoryId.takeIf { isStableId(it) }
    }

    /** Domain model for pull-time auto-seed. */
    fun toCategory(definition: Definition): Category =
        Category(
            id = definition.id,
            name = definition.name,
            iconKey = definition.iconKey,
            isDefault = true,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
}
