package com.chy.regimen.data.repository

import com.chy.regimen.data.local.dao.RoutineDao
import com.chy.regimen.data.local.entity.RoutineDayEntity
import com.chy.regimen.data.local.entity.RoutineEntity
import com.chy.regimen.data.local.entity.RoutineSlotEntity
import com.chy.regimen.data.local.seed.SeedCatalog
import com.chy.regimen.domain.model.SlotType
import com.chy.regimen.domain.model.TargetMovementType
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

    suspend fun deleteRoutine(id: String) = routineDao.deleteRoutine(id)
    suspend fun deleteDay(id: String) = routineDao.deleteDay(id)
    suspend fun deleteSlot(id: String) = routineDao.deleteSlot(id)
}