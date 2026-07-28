package com.splitease.app.data.repository

import com.splitease.app.data.local.dao.CategoryDao
import com.splitease.app.data.local.entity.CategoryEntity
import com.splitease.app.data.local.mapper.toDomain
import com.splitease.app.data.local.mapper.toEntity
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
 */
@Singleton
class RoomCategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
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
            if (categoryDao.count() > 0) return
            // Stable ids so co-members share the same category_id over the wire.
            // (Older installs used random UUIDs; remote pull drops unknown category ids.)
            val defaults =
                listOf(
                    Triple("cat_general", "General", "category_general"),
                    Triple("cat_food", "Food", "category_food"),
                    Triple("cat_travel", "Travel", "category_travel"),
                    Triple("cat_rent", "Rent", "category_rent"),
                    Triple("cat_utilities", "Utilities", "category_utilities"),
                    Triple("cat_entertainment", "Entertainment", "category_entertainment"),
                ).map { (id, name, icon) ->
                    CategoryEntity(
                        id = id,
                        name = name,
                        iconKey = icon,
                        isDefault = true,
                        syncStatus = SyncStatus.LOCAL_ONLY,
                    )
                }
            categoryDao.upsertAll(defaults)
        }
    }
