package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to expense categories.
 */
interface CategoryRepository {
    /**
     * Observes all categories.
     *
     * @return Cold [Flow] ordered by name.
     */
    fun observeCategories(): Flow<List<Category>>

    /**
     * Loads a category by id.
     *
     * @param id Local UUID.
     * @return The category, or null.
     */
    suspend fun getById(id: String): Category?

    /**
     * Inserts or replaces a category.
     *
     * @param category Domain category.
     */
    suspend fun upsert(category: Category)

    /**
     * Deletes a category by id.
     *
     * @param id Local UUID.
     */
    suspend fun deleteById(id: String)

    /**
     * Seeds default categories and remaps legacy random default ids to stable ids.
     */
    suspend fun ensureDefaults()

    /**
     * Maps a local category id for Supabase push (stable defaults only).
     *
     * @param localCategoryId Room category id.
     * @return Cloud-safe id, or null when omitted from sync.
     */
    fun categoryIdForCloud(localCategoryId: String?): String?

    /**
     * Resolves a remote `category_id` for Room after pull.
     *
     * Auto-seeds missing stable defaults; drops unknown custom ids.
     *
     * @param remoteCategoryId Value from PostgREST.
     * @return Local category id to store on the expense, or null.
     */
    suspend fun resolveCategoryForRemotePull(remoteCategoryId: String?): String?
}
