package com.splitease.app.domain.model

/**
 * Expense category for filtering and totals.
 *
 * @property id Stable local UUID.
 * @property name Display label (e.g. `"Food"`).
 * @property iconKey Optional Material / drawable key for UI.
 * @property isDefault Whether the category ships as a built-in preset.
 * @property syncStatus Offline-first sync bookmark.
 */
data class Category(
    val id: String,
    val name: String,
    val iconKey: String? = null,
    val isDefault: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
