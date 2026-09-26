package com.chy.muscletome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE ownerId = :userId ORDER BY createdAtEpochMs DESC")
    fun observeRoutines(userId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE ownerId = :userId ORDER BY createdAtEpochMs")
    suspend fun getRoutines(userId: String): List<RoutineEntity>

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

    /** Sets/clears superset membership for one slot (null binds NULL). */
    @Query("UPDATE routine_slots SET supersetGroupId = :groupId WHERE id = :slotId")
    suspend fun updateSlotSupersetGroup(slotId: String, groupId: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(row: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(row: RoutineDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(rows: List<RoutineDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(row: RoutineSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(rows: List<RoutineSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlotTargets(rows: List<SlotTargetMuscleCrossRef>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM routine_days WHERE id = :id")
    suspend fun deleteDay(id: String)

    @Query("DELETE FROM routine_slots WHERE id = :id")
    suspend fun deleteSlot(id: String)

    @Query(
        """
        UPDATE routine_slots
        SET sets = :sets, repRangeMin = :repMin, repRangeMax = :repMax, restSeconds = :restSeconds,
            targetRpe = :targetRpe
        WHERE id = :id
        """,
    )
    suspend fun updateSlotMetrics(
        id: String,
        sets: Int,
        repMin: Int,
        repMax: Int,
        restSeconds: Int,
        targetRpe: Float?,
    )

    @Query("SELECT * FROM slot_target_muscles WHERE slotId = :slotId")
    suspend fun getSlotTargets(slotId: String): List<SlotTargetMuscleCrossRef>

    @Query("SELECT * FROM routine_days WHERE id = :id")
    suspend fun getDay(id: String): RoutineDayEntity?

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex")
    suspend fun getDays(routineId: String): List<RoutineDayEntity>

    @Update
    suspend fun updateDays(rows: List<RoutineDayEntity>)

    @Update
    suspend fun updateSlots(rows: List<RoutineSlotEntity>)

    @Query("SELECT exerciseId FROM routine_slots WHERE exerciseId IS NOT NULL")
    suspend fun getFixedSlotExerciseIds(): List<String>

    @Query("SELECT * FROM routine_days")
    suspend fun getAllDays(): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_slots")
    suspend fun getAllSlots(): List<RoutineSlotEntity>

    @Query("SELECT * FROM slot_target_muscles")
    suspend fun getAllSlotTargets(): List<SlotTargetMuscleCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(rows: List<RoutineEntity>)

    @Query("SELECT * FROM slot_target_muscles")
    fun observeAllSlotTargets(): kotlinx.coroutines.flow.Flow<List<SlotTargetMuscleCrossRef>>
}