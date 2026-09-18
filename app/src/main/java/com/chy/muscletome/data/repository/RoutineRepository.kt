package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
) {
    fun observeRoutines(): Flow<List<RoutineEntity>> =
        routineDao.observeRoutines(SeedCatalog.LOCAL_USER_ID)

    fun observeRoutine(id: String): Flow<RoutineEntity?> = routineDao.observeRoutine(id)
    fun observeDays(routineId: String): Flow<List<RoutineDayEntity>> = routineDao.observeDays(routineId)
    fun observeAllDays(): Flow<List<RoutineDayEntity>> = routineDao.observeAllDays()
    fun observeDay(id: String): Flow<RoutineDayEntity?> = routineDao.observeDay(id)
    fun observeSlots(dayId: String): Flow<List<RoutineSlotEntity>> = routineDao.observeSlots(dayId)

    suspend fun createRoutine(name: String): String {
        val id = UUID.randomUUID().toString()
        routineDao.insertRoutine(
            RoutineEntity(
                id = id,
                name = name.trim(),
                ownerId = SeedCatalog.LOCAL_USER_ID,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun addDay(routineId: String, name: String): String {
        val id = UUID.randomUUID().toString()
        routineDao.insertDay(
            RoutineDayEntity(
                id = id,
                routineId = routineId,
                name = name.trim(),
                orderIndex = routineDao.nextDayIndex(routineId),
            ),
        )
        return id
    }

    suspend fun addFixedSlot(
        dayId: String,
        exerciseId: String,
        sets: Int,
        repMin: Int,
        repMax: Int,
        restSeconds: Int,
    ) {
        routineDao.insertSlot(
            RoutineSlotEntity(
                id = UUID.randomUUID().toString(),
                routineDayId = dayId,
                orderIndex = routineDao.nextSlotIndex(dayId),
                type = SlotType.FIXED,
                exerciseId = exerciseId,
                targetMovementType = TargetMovementType.ANY,
                sets = sets,
                repRangeMin = repMin,
                repRangeMax = repMax,
                restSeconds = restSeconds,
            ),
        )
    }

    suspend fun addTargetSlot(
        dayId: String,
        muscleGroupIds: List<String>,
        movementType: TargetMovementType,
        sets: Int,
        repMin: Int,
        repMax: Int,
        restSeconds: Int,
    ) {
        val slotId = UUID.randomUUID().toString()
        routineDao.insertSlot(
            RoutineSlotEntity(
                id = slotId,
                routineDayId = dayId,
                orderIndex = routineDao.nextSlotIndex(dayId),
                type = SlotType.TARGET,
                exerciseId = null,
                targetMovementType = movementType,
                sets = sets,
                repRangeMin = repMin,
                repRangeMax = repMax,
                restSeconds = restSeconds,
            ),
        )
        routineDao.insertSlotTargets(
            muscleGroupIds.map { SlotTargetMuscleCrossRef(slotId, it) },
        )
    }

    suspend fun deleteRoutine(id: String) = routineDao.deleteRoutine(id)
    suspend fun deleteDay(id: String) = routineDao.deleteDay(id)
    suspend fun deleteSlot(id: String) = routineDao.deleteSlot(id)
}