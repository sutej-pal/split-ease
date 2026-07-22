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
import java.util.UUID
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
            val defaults = listOf(
                "General" to "category_general",
                "Food" to "category_food",
                "Travel" to "category_travel",
                "Rent" to "category_rent",
                "Utilities" to "category_utilities",
                "Entertainment" to "category_entertainment",
            ).map { (name, icon) ->
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    iconKey = icon,
                    isDefault = true,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )
            }
            categoryDao.upsertAll(defaults)
        }
    }
