package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitease.app.data.local.entity.PinBoardEntity
import com.splitease.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PinBoardDao {
    @Query("SELECT * FROM pin_boards WHERE groupId = :groupId")
    fun observePinBoard(groupId: String): Flow<PinBoardEntity?>

    @Query("SELECT * FROM pin_boards WHERE groupId = :groupId")
    suspend fun getPinBoard(groupId: String): PinBoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pinBoard: PinBoardEntity)

    @Query("SELECT * FROM pin_boards WHERE syncStatus = :status")
    suspend fun getBySyncStatus(status: SyncStatus): List<PinBoardEntity>

    @Query("DELETE FROM pin_boards WHERE groupId = :groupId")
    suspend fun delete(groupId: String)
}
