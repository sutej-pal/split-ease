package com.splitease.app.data.local.mapper

import com.splitease.app.data.local.entity.CategoryEntity
import com.splitease.app.data.local.entity.ExpenseEntity
import com.splitease.app.data.local.entity.ExpenseSplitEntity
import com.splitease.app.data.local.entity.FriendEntity
import com.splitease.app.data.local.entity.GroupEntity
import com.splitease.app.data.local.entity.GroupMemberEntity
import com.splitease.app.data.local.entity.PaymentEntity
import com.splitease.app.data.local.entity.UserEntity
import com.splitease.app.domain.model.Category
import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import com.splitease.app.domain.model.Friend
import com.splitease.app.domain.model.Group
import com.splitease.app.domain.model.GroupMember
import com.splitease.app.domain.model.Payment
import com.splitease.app.domain.model.User

/** Maps [UserEntity] to domain [User]. */
fun UserEntity.toDomain(): User =
    User(
        id = id,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        phoneCountryCode = phoneCountryCode,
        phoneNumber = phoneNumber,
        preferredCurrency = preferredCurrency,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [User] to [UserEntity]. */
fun User.toEntity(): UserEntity =
    UserEntity(
        id = id,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        phoneCountryCode = phoneCountryCode,
        phoneNumber = phoneNumber,
        preferredCurrency = preferredCurrency,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps [FriendEntity] to domain [Friend]. */
fun FriendEntity.toDomain(): Friend =
    Friend(
        id = id,
        ownerUserId = ownerUserId,
        friendUserId = friendUserId,
        emailSnapshot = emailSnapshot,
        displayNameSnapshot = displayNameSnapshot,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [Friend] to [FriendEntity]. */
fun Friend.toEntity(): FriendEntity =
    FriendEntity(
        id = id,
        ownerUserId = ownerUserId,
        friendUserId = friendUserId,
        emailSnapshot = emailSnapshot,
        displayNameSnapshot = displayNameSnapshot,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps [GroupEntity] to domain [Group]. */
fun GroupEntity.toDomain(): Group =
    Group(
        id = id,
        name = name,
        defaultCurrencyCode = defaultCurrencyCode,
        groupType = groupType,
        createdByUserId = createdByUserId,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [Group] to [GroupEntity]. */
fun Group.toEntity(): GroupEntity =
    GroupEntity(
        id = id,
        name = name,
        defaultCurrencyCode = defaultCurrencyCode,
        groupType = groupType,
        createdByUserId = createdByUserId,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps [GroupMemberEntity] to domain [GroupMember]. */
fun GroupMemberEntity.toDomain(): GroupMember =
    GroupMember(
        id = id,
        groupId = groupId,
        userId = userId,
        role = role,
        joinedAtEpochMs = joinedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [GroupMember] to [GroupMemberEntity]. */
fun GroupMember.toEntity(): GroupMemberEntity =
    GroupMemberEntity(
        id = id,
        groupId = groupId,
        userId = userId,
        role = role,
        joinedAtEpochMs = joinedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps [CategoryEntity] to domain [Category]. */
fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        iconKey = iconKey,
        isDefault = isDefault,
        syncStatus = syncStatus,
    )

/** Maps domain [Category] to [CategoryEntity]. */
fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        isDefault = isDefault,
        syncStatus = syncStatus,
    )

/** Maps [ExpenseEntity] to domain [Expense]. */
fun ExpenseEntity.toDomain(): Expense =
    Expense(
        id = id,
        description = description,
        amount = amount,
        currencyCode = currencyCode,
        categoryId = categoryId,
        paidByUserId = paidByUserId,
        groupId = groupId,
        expenseDateEpochMs = expenseDateEpochMs,
        splitType = splitType,
        isRecurring = isRecurring,
        recurrenceFrequency = recurrenceFrequency,
        nextOccurrenceEpochMs = nextOccurrenceEpochMs,
        recurringTemplateId = recurringTemplateId,
        notes = notes,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [Expense] to [ExpenseEntity]. */
fun Expense.toEntity(): ExpenseEntity =
    ExpenseEntity(
        id = id,
        description = description,
        amount = amount,
        currencyCode = currencyCode,
        categoryId = categoryId,
        paidByUserId = paidByUserId,
        groupId = groupId,
        expenseDateEpochMs = expenseDateEpochMs,
        splitType = splitType,
        isRecurring = isRecurring,
        recurrenceFrequency = recurrenceFrequency,
        nextOccurrenceEpochMs = nextOccurrenceEpochMs,
        recurringTemplateId = recurringTemplateId,
        notes = notes,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps [ExpenseSplitEntity] to domain [ExpenseSplit]. */
fun ExpenseSplitEntity.toDomain(): ExpenseSplit =
    ExpenseSplit(
        id = id,
        expenseId = expenseId,
        userId = userId,
        owedAmount = owedAmount,
        percentage = percentage,
        shares = shares,
        syncStatus = syncStatus,
    )

/** Maps domain [ExpenseSplit] to [ExpenseSplitEntity]. */
fun ExpenseSplit.toEntity(): ExpenseSplitEntity =
    ExpenseSplitEntity(
        id = id,
        expenseId = expenseId,
        userId = userId,
        owedAmount = owedAmount,
        percentage = percentage,
        shares = shares,
        syncStatus = syncStatus,
    )

/** Maps [PaymentEntity] to domain [Payment]. */
fun PaymentEntity.toDomain(): Payment =
    Payment(
        id = id,
        fromUserId = fromUserId,
        toUserId = toUserId,
        amount = amount,
        currencyCode = currencyCode,
        groupId = groupId,
        note = note,
        paidAtEpochMs = paidAtEpochMs,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )

/** Maps domain [Payment] to [PaymentEntity]. */
fun Payment.toEntity(): PaymentEntity =
    PaymentEntity(
        id = id,
        fromUserId = fromUserId,
        toUserId = toUserId,
        amount = amount,
        currencyCode = currencyCode,
        groupId = groupId,
        note = note,
        paidAtEpochMs = paidAtEpochMs,
        remoteId = remoteId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        syncStatus = syncStatus,
    )
