package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE ownerId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeRoutines(userId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex")
    fun observeDays(routineId: String): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days ORDER BY orderIndex")
    fun observeAllDays(): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days WHERE id = :id")
    fun observeDay(id: String): Flow<RoutineDayEntity?>

    @Query("SELECT * FROM routine_slots WHERE routineDayId = :dayId ORDER BY orderIndex")
    fun observeSlots(dayId: String): Flow<List<RoutineSlotEntity>>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM routine_days WHERE routineId = :routineId")
    suspend fun nextDayIndex(routineId: String): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM routine_slots WHERE routineDayId = :dayId")
    suspend fun nextSlotIndex(dayId: String): Int

    @Query("SELECT * FROM routine_slots WHERE routineDayId = :dayId ORDER BY orderIndex")
    suspend fun getSlots(dayId: String): List<RoutineSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(row: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(row: RoutineDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(row: RoutineSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotTargets(rows: List<SlotTargetMuscleCrossRef>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM routine_days WHERE id = :id")
    suspend fun deleteDay(id: String)

    @Query("DELETE FROM routine_slots WHERE id = :id")
    suspend fun deleteSlot(id: String)

    @Query("SELECT * FROM slot_target_muscles WHERE slotId = :slotId")
    suspend fun getSlotTargets(slotId: String): List<SlotTargetMuscleCrossRef>

    @Query("SELECT * FROM slot_target_muscles")
    fun observeAllSlotTargets(): kotlinx.coroutines.flow.Flow<List<SlotTargetMuscleCrossRef>>
}