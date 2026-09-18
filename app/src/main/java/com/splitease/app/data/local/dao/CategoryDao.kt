package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `categories` table.
 */
@Dao
interface CategoryDao {
    /** @return Flow of categories ordered by name. */
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** @param id Local UUID. @return Category or null. */
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    /** @return Number of category rows. */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Inserts or replaces [category]. */
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    /** Inserts or replaces many categories. */
    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    /** Deletes category [id]. */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    /** @param name Category display name. @return Row with matching name (case-insensitive). */
    @Query("SELECT * FROM categories WHERE lower(name) = lower(:name) LIMIT 1")
    suspend fun getByNameIgnoreCase(name: String): CategoryEntity?

    /** @return Built-in default category rows. */
    @Query("SELECT * FROM categories WHERE isDefault = 1")
    suspend fun getDefaults(): List<CategoryEntity>
}
