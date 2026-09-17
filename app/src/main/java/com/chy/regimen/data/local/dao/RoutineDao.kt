package com.chy.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chy.regimen.data.local.entity.RoutineDayEntity
import com.chy.regimen.data.local.entity.RoutineEntity
import com.chy.regimen.data.local.entity.RoutineSlotEntity
import com.chy.regimen.data.local.entity.SlotTargetMuscleCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE ownerId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeRoutines(userId: String): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(row: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(row: RoutineDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(row: RoutineSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotTargets(rows: List<SlotTargetMuscleCrossRef>)
}