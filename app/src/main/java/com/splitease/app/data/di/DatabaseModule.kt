package com.splitease.app.data.di

import android.content.Context
import androidx.room.Room
import com.splitease.app.data.local.dao.CategoryDao
import com.splitease.app.data.local.dao.ExpenseDao
import com.splitease.app.data.local.dao.FriendDao
import com.splitease.app.data.local.dao.GroupDao
import com.splitease.app.data.local.dao.PaymentDao
import com.splitease.app.data.local.dao.UserDao
import com.splitease.app.data.local.db.SplitEaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the offline-first [SplitEaseDatabase] and its DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Builds the singleton Room database.
     *
     * @param context Application context.
     * @return Configured [SplitEaseDatabase].
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SplitEaseDatabase =
        Room.databaseBuilder(
            context,
            SplitEaseDatabase::class.java,
            SplitEaseDatabase.NAME,
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    /** @param db Database. @return [UserDao]. */
    @Provides
    fun provideUserDao(db: SplitEaseDatabase): UserDao = db.userDao()

    /** @param db Database. @return [FriendDao]. */
    @Provides
    fun provideFriendDao(db: SplitEaseDatabase): FriendDao = db.friendDao()

    /** @param db Database. @return [GroupDao]. */
    @Provides
    fun provideGroupDao(db: SplitEaseDatabase): GroupDao = db.groupDao()

    /** @param db Database. @return [CategoryDao]. */
    @Provides
    fun provideCategoryDao(db: SplitEaseDatabase): CategoryDao = db.categoryDao()

    /** @param db Database. @return [ExpenseDao]. */
    @Provides
    fun provideExpenseDao(db: SplitEaseDatabase): ExpenseDao = db.expenseDao()

    /** @param db Database. @return [PaymentDao]. */
    @Provides
    fun providePaymentDao(db: SplitEaseDatabase): PaymentDao = db.paymentDao()
}
