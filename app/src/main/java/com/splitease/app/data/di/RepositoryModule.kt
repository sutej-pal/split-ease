package com.splitease.app.data.di

import com.splitease.app.data.repository.RoomActivityEventRepository
import com.splitease.app.data.repository.RoomCategoryRepository
import com.splitease.app.data.repository.RoomExpenseRepository
import com.splitease.app.data.repository.RoomFriendRepository
import com.splitease.app.data.repository.RoomGroupRepository
import com.splitease.app.data.repository.RoomInviteRepository
import com.splitease.app.data.repository.RoomPaymentRepository
import com.splitease.app.data.repository.RoomUserRepository
import com.splitease.app.data.repository.RenderMailRepository
import com.splitease.app.data.repository.SupabaseAuthRepository
import com.splitease.app.data.settings.SharedPreferencesAppSettingsRepository
import com.splitease.app.domain.repository.ActivityEventRepository
import com.splitease.app.domain.repository.AuthRepository
import com.splitease.app.domain.repository.CategoryRepository
import com.splitease.app.domain.repository.ExpenseRepository
import com.splitease.app.domain.repository.FriendRepository
import com.splitease.app.domain.repository.GroupRepository
import com.splitease.app.domain.repository.InviteRepository
import com.splitease.app.domain.repository.MailRepository
import com.splitease.app.domain.repository.PaymentRepository
import com.splitease.app.domain.repository.UserRepository
import com.splitease.app.domain.settings.AppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds domain repository interfaces to Room / Supabase implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /** Binds [AuthRepository] to [SupabaseAuthRepository]. */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    /** Binds [UserRepository] to [RoomUserRepository]. */
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: RoomUserRepository): UserRepository

    /** Binds [FriendRepository] to [RoomFriendRepository]. */
    @Binds
    @Singleton
    abstract fun bindFriendRepository(impl: RoomFriendRepository): FriendRepository

    /** Binds [InviteRepository] to [RoomInviteRepository]. */
    @Binds
    @Singleton
    abstract fun bindInviteRepository(impl: RoomInviteRepository): InviteRepository

    /** Binds [GroupRepository] to [RoomGroupRepository]. */
    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: RoomGroupRepository): GroupRepository

    /** Binds [CategoryRepository] to [RoomCategoryRepository]. */
    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    /** Binds [ExpenseRepository] to [RoomExpenseRepository]. */
    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: RoomExpenseRepository): ExpenseRepository

    /** Binds [ActivityEventRepository] to [RoomActivityEventRepository]. */
    @Binds
    @Singleton
    abstract fun bindActivityEventRepository(impl: RoomActivityEventRepository): ActivityEventRepository

    /** Binds [PaymentRepository] to [RoomPaymentRepository]. */
    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: RoomPaymentRepository): PaymentRepository

    /** Binds [AppSettingsRepository] to [SharedPreferencesAppSettingsRepository]. */
    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: SharedPreferencesAppSettingsRepository): AppSettingsRepository

    /** Binds [MailRepository] to [RenderMailRepository]. */
    @Binds
    @Singleton
    abstract fun bindMailRepository(impl: RenderMailRepository): MailRepository
}
