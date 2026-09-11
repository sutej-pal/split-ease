package com.splitease.app.data.local.converter

import androidx.room.TypeConverter
import com.splitease.app.domain.model.ExchangeRateSource
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.model.MemberRole
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import java.math.BigDecimal

/**
 * Room [TypeConverter]s for money and domain enums.
 *
 * Amounts are persisted as plain decimal strings to preserve [BigDecimal] precision.
 */
class SplitEaseTypeConverters {
    /**
     * Converts a [BigDecimal] to a plain string for TEXT columns.
     *
     * @param value Amount, or null.
     * @return Plain string, or null.
     */
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    /**
     * Parses a TEXT column into [BigDecimal].
     *
     * @param value Plain decimal string, or null.
     * @return Parsed amount, or null.
     */
    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromSplitType(value: SplitType): String = value.name

    /** @param value Stored name. @return [SplitType]. */
    @TypeConverter
    fun toSplitType(value: String): SplitType = SplitType.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromMemberRole(value: MemberRole): String = value.name

    /** @param value Stored name. @return [MemberRole]. */
    @TypeConverter
    fun toMemberRole(value: String): MemberRole = MemberRole.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromGroupType(value: GroupType): String = value.name

    /** @param value Stored name. @return [GroupType]. */
    @TypeConverter
    fun toGroupType(value: String): GroupType =
        runCatching { GroupType.valueOf(value) }.getOrDefault(GroupType.OTHER)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    /** @param value Stored name. @return [SyncStatus]. */
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

    /** @param value Stored name. @return [RecurrenceFrequency]. */
    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromInviteKind(value: InviteKind): String = value.name

    /** @param value Stored name. @return [InviteKind]. */
    @TypeConverter
    fun toInviteKind(value: String): InviteKind = InviteKind.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromInviteStatus(value: InviteStatus): String = value.name

    /** @param value Stored name. @return [InviteStatus]. */
    @TypeConverter
    fun toInviteStatus(value: String): InviteStatus = InviteStatus.valueOf(value)

    /** @param value Enum to store. @return Name string. */
    @TypeConverter
    fun fromExchangeRateSource(value: ExchangeRateSource?): String? = value?.name

    /** @param value Stored name. @return [ExchangeRateSource]. */
    @TypeConverter
    fun toExchangeRateSource(value: String?): ExchangeRateSource? =
        value?.let { ExchangeRateSource.valueOf(it) }
}
