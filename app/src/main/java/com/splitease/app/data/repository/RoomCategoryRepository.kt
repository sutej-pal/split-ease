package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.CategoryDao
import com.splitease.app.data.local.dao.ExpenseDao
import com.splitease.app.data.local.entity.CategoryEntity
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
import com.splitease.app.domain.category.DefaultCategories
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.SyncStatus
import com.splitease.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [CategoryRepository].
 *
 * @property categoryDao Local categories DAO.
 * @property expenseDao Used to remap legacy default ids on expenses.
 */
@Singleton
class RoomCategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
        private val expenseDao: ExpenseDao,
    ) : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> =
            categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

        override suspend fun getById(id: String): Category? = categoryDao.getById(id)?.toDomain()

        override suspend fun upsert(category: Category) {
            categoryDao.upsert(category.toEntity())
        }

        override suspend fun deleteById(id: String) {
            categoryDao.deleteById(id)
        }

        override suspend fun ensureDefaults() {
            migrateLegacyDefaultIdsToStable()
            seedMissingStableDefaults()
        }

        override fun categoryIdForCloud(localCategoryId: String?): String? =
            DefaultCategories.categoryIdForCloud(localCategoryId)

        override suspend fun resolveCategoryForRemotePull(remoteCategoryId: String?): String? {
            if (remoteCategoryId.isNullOrBlank()) return null
            if (categoryDao.getById(remoteCategoryId) != null) return remoteCategoryId
            val definition = DefaultCategories.byId(remoteCategoryId) ?: return null
            categoryDao.upsert(definition.toEntity())
            return remoteCategoryId
        }

        /**
         * Rewrites pre-stable default rows (random UUID per install) to shared `cat_*` ids
         * and updates referencing expenses.
         */
        private suspend fun migrateLegacyDefaultIdsToStable() {
            DefaultCategories.ALL.forEach { definition ->
                val stableId = definition.id
                val stableRow = categoryDao.getById(stableId)
                val legacyRow =
                    categoryDao.getByNameIgnoreCase(definition.name)
                        ?.takeIf { it.id != stableId && it.isDefault }

                if (stableRow != null) {
                    if (legacyRow != null) {
                        expenseDao.remapCategoryId(legacyRow.id, stableId)
                        categoryDao.deleteById(legacyRow.id)
                    }
                    return@forEach
                }

                if (legacyRow != null) {
                    // Free the unique `name` index before inserting the stable row.
                    categoryDao.upsert(
                        legacyRow.copy(name = "__legacy__${legacyRow.id}"),
                    )
                    categoryDao.upsert(definition.toEntity())
                    expenseDao.remapCategoryId(legacyRow.id, stableId)
                    categoryDao.deleteById(legacyRow.id)
                }
            }
        }

        /** Inserts any missing built-in defaults (fresh install or partial seed). */
        private suspend fun seedMissingStableDefaults() {
            DefaultCategories.ALL.forEach { definition ->
                if (categoryDao.getById(definition.id) != null) return@forEach
                if (categoryDao.getByNameIgnoreCase(definition.name) != null) return@forEach
                categoryDao.upsert(definition.toEntity())
            }
        }
    }

private fun DefaultCategories.Definition.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        isDefault = true,
        syncStatus = SyncStatus.LOCAL_ONLY,
    )
