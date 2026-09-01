package com.splitease.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.splitease.app.data.local.converter.SplitEaseTypeConverters
import com.splitease.app.data.local.dao.ActivityEventDao
import com.splitease.app.data.local.dao.CategoryDao
import com.splitease.app.data.local.dao.ExpenseCommentDao
import com.splitease.app.data.local.dao.ExpenseDao
import com.splitease.app.data.local.dao.ExpensePhotoDao
import com.splitease.app.data.local.dao.FriendDao
import com.splitease.app.data.local.dao.GroupDao
import com.splitease.app.data.local.dao.InviteDao
import com.splitease.app.data.local.dao.PaymentDao
import com.splitease.app.data.local.dao.UserDao
import com.splitease.app.data.local.dao.PinBoardDao
import com.splitease.app.data.local.entity.ActivityEventEntity
import com.splitease.app.data.local.entity.CategoryEntity
import com.splitease.app.data.local.entity.ExpenseCommentEntity
import com.splitease.app.data.local.entity.ExpenseEntity
import com.splitease.app.data.local.entity.ExpensePhotoEntity
import com.splitease.app.data.local.entity.ExpenseSplitEntity
import com.splitease.app.data.local.entity.FriendEntity
import com.splitease.app.data.local.entity.GroupEntity
import com.splitease.app.data.local.entity.GroupMemberEntity
import com.splitease.app.data.local.entity.InviteEntity
import com.splitease.app.data.local.entity.PaymentEntity
import com.splitease.app.data.local.entity.PinBoardEntity
import com.splitease.app.data.local.entity.UserEntity

/**
 * Offline-first Room database for SplitEase (version 14 — added pin_boards).
 */
@Database(
    entities = [
        UserEntity::class,
        FriendEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        ExpenseCommentEntity::class,
        ExpensePhotoEntity::class,
        PaymentEntity::class,
        InviteEntity::class,
        ActivityEventEntity::class,
        PinBoardEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@TypeConverters(SplitEaseTypeConverters::class)
abstract class SplitEaseDatabase : RoomDatabase() {
    /** @return DAO for users. */
    abstract fun userDao(): UserDao

    /** @return DAO for friends. */
    abstract fun friendDao(): FriendDao

    /** @return DAO for pin boards. */
    abstract fun pinBoardDao(): PinBoardDao

    /** @return DAO for groups and members. */
    abstract fun groupDao(): GroupDao

    /** @return DAO for categories. */
    abstract fun categoryDao(): CategoryDao

    /** @return DAO for expenses and splits. */
    abstract fun expenseDao(): ExpenseDao

    /** @return DAO for expense comments. */
    abstract fun expenseCommentDao(): ExpenseCommentDao

    /** @return DAO for expense photos. */
    abstract fun expensePhotoDao(): ExpensePhotoDao

    /** @return DAO for payments. */
    abstract fun paymentDao(): PaymentDao

    /** @return DAO for invites. */
    abstract fun inviteDao(): InviteDao

    /** @return DAO for activity events. */
    abstract fun activityEventDao(): ActivityEventDao

    companion object {
        /** On-disk database file name. */
        const val NAME = "splitease.db"
    }
}
