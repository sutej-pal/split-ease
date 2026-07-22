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
     * Seeds default categories when the table is empty.
     */
    suspend fun ensureDefaults()
}
