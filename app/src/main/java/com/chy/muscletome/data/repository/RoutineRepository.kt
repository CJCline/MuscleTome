package com.chy.muscletome.data.repository

import androidx.room.withTransaction
import com.chy.muscletome.data.local.MuscleTomeDatabase
import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SlotWithTargetMuscles
import com.chy.muscletome.data.local.entity.RoutineDayEntity
import com.chy.muscletome.data.local.entity.RoutineEntity
import com.chy.muscletome.data.local.entity.RoutineSlotEntity
import com.chy.muscletome.data.local.entity.SlotTargetMuscleCrossRef
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.routine.SupersetGrouper
import com.chy.muscletome.domain.template.RoutineTemplate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val catalogDao: CatalogDao,
    private val database: MuscleTomeDatabase,
) {
    fun observeRoutines(): Flow<List<RoutineEntity>> =
        routineDao.observeRoutines(SeedCatalog.LOCAL_USER_ID)

    fun observeRoutine(id: String): Flow<RoutineEntity?> = routineDao.observeRoutine(id)
    fun observeDays(routineId: String): Flow<List<RoutineDayEntity>> = routineDao.observeDays(routineId)
    fun observeAllDays(): Flow<List<RoutineDayEntity>> = routineDao.observeAllDays()
    fun observeDay(id: String): Flow<RoutineDayEntity?> = routineDao.observeDay(id)
    fun observeSlots(dayId: String): Flow<List<RoutineSlotEntity>> = routineDao.observeSlots(dayId)

    /** One day's slots with their targeted muscle ids (for day previews). */
    suspend fun getSlotsWithTargets(dayId: String): List<SlotWithTargetMuscles> =
        routineDao.getSlotsWithTargets(dayId)

    /** All slot→muscle-group refs (day screen labels TARGET slots). */
    fun observeAllSlotTargets(): Flow<List<SlotTargetMuscleCrossRef>> =
        routineDao.observeAllSlotTargets()

    /** Muscle-group id → display name, for target-slot labels. */
    suspend fun getMuscleGroupNames(): Map<String, String> =
        catalogDao.getMuscleGroups().associate { it.id to it.name }

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
        sets: Int = DEFAULT_SETS,
        repMin: Int = DEFAULT_REP_MIN,
        repMax: Int = DEFAULT_REP_MAX,
        restSeconds: Int = DEFAULT_REST_SECONDS,
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

    /**
     * Adds one slot per exercise, preserving the given order. When
     * [asSuperset] is set, all inserted slots share one superset group id —
     * they are trained round-robin when the day runs.
     */
    suspend fun addFixedSlots(dayId: String, exerciseIds: List<String>, asSuperset: Boolean = false) {
        if (!asSuperset) {
            exerciseIds.forEach { addFixedSlot(dayId, it) }
            return
        }
        val groupId = UUID.randomUUID().toString()
        exerciseIds.forEach { exerciseId ->
            routineDao.insertSlot(
                RoutineSlotEntity(
                    id = UUID.randomUUID().toString(),
                    routineDayId = dayId,
                    orderIndex = routineDao.nextSlotIndex(dayId),
                    type = SlotType.FIXED,
                    exerciseId = exerciseId,
                    targetMovementType = TargetMovementType.ANY,
                    sets = DEFAULT_SETS,
                    repRangeMin = DEFAULT_REP_MIN,
                    repRangeMax = DEFAULT_REP_MAX,
                    restSeconds = DEFAULT_REST_SECONDS,
                    supersetGroupId = groupId,
                ),
            )
        }
    }

    /**
     * Groups [slotId] with the slot that follows it in day order; invoking it
     * on the last slot of an existing group extends that chain (circuits of
     * 3+ are built this way). Runs the pure grouper inside one transaction.
     */
    suspend fun groupSlotWithNext(dayId: String, slotId: String) = database.withTransaction {
        val slots = routineDao.getSlots(dayId)
        SupersetGrouper.groupWithNext(
            slots.map { it.id to it.supersetGroupId },
            slotId,
        ).forEach { assignment ->
            routineDao.updateSlotSupersetGroup(assignment.slotId, assignment.groupId)
        }
    }

    /**
     * Pulls [slotId] out of its superset group. If only one member would
     * remain, the group dissolves entirely — a one-slot superset is not a
     * superset.
     */
    suspend fun ungroupSlot(dayId: String, slotId: String) = database.withTransaction {
        val slots = routineDao.getSlots(dayId)
        SupersetGrouper.ungroup(
            slots.map { it.id to it.supersetGroupId },
            slotId,
        ).forEach { assignment ->
            routineDao.updateSlotSupersetGroup(assignment.slotId, assignment.groupId)
        }
    }

    suspend fun addTargetSlot(
        dayId: String,
        muscleGroupIds: List<String>,
        movementType: TargetMovementType,
        sets: Int = DEFAULT_SETS,
        repMin: Int = DEFAULT_REP_MIN,
        repMax: Int = DEFAULT_REP_MAX,
        restSeconds: Int = DEFAULT_REST_SECONDS,
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

    /**
     * Materializes a [RoutineTemplate] into a real routine with days and
     * slots. Returns the new routine id. Used by onboarding; the template's
     * FIXED ids are validated against the seed catalog by RoutineTemplatesTest.
     */
    suspend fun applyTemplate(template: RoutineTemplate): String {
        val routineId = createRoutine(template.name)
        template.days.forEach { day ->
            val dayId = addDay(routineId, day.name)
            day.slots.forEach { slot ->
                when (slot.type) {
                    SlotType.FIXED -> addFixedSlot(
                        dayId = dayId,
                        exerciseId = requireNotNull(slot.exerciseId) {
                            "Template FIXED slot is missing an exerciseId"
                        },
                        sets = slot.sets,
                        repMin = slot.repRangeMin,
                        repMax = slot.repRangeMax,
                        restSeconds = slot.restSeconds,
                    )
                    SlotType.TARGET -> addTargetSlot(
                        dayId = dayId,
                        muscleGroupIds = slot.targetMuscleIds,
                        movementType = slot.targetMovementType,
                        sets = slot.sets,
                        repMin = slot.repRangeMin,
                        repMax = slot.repRangeMax,
                        restSeconds = slot.restSeconds,
                    )
                }
            }
        }
        return routineId
    }

    suspend fun updateSlot(
        id: String,
        sets: Int,
        repMin: Int,
        repMax: Int,
        restSeconds: Int,
        targetRpe: Float? = null,
    ) = routineDao.updateSlotMetrics(id, sets, repMin, repMax, restSeconds, targetRpe)

    companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REP_MIN = 8
        const val DEFAULT_REP_MAX = 12
        const val DEFAULT_REST_SECONDS = 90
    }
    suspend fun deleteDay(id: String) = routineDao.deleteDay(id)
    suspend fun deleteSlot(id: String) = routineDao.deleteSlot(id)

    /** Reindexes the routine's days to match [orderedIds]; skips no-op writes. */
    suspend fun reorderDays(routineId: String, orderedIds: List<String>) {
        database.withTransaction {
            val byId = routineDao.getDays(routineId).associateBy { it.id }
            val updates = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.takeIf { it.orderIndex != index }?.copy(orderIndex = index)
            }
            if (updates.isNotEmpty()) routineDao.updateDays(updates)
        }
    }

    /** Reindexes the day's slots to match [orderedIds]; skips no-op writes. */
    suspend fun reorderSlots(dayId: String, orderedIds: List<String>) {
        database.withTransaction {
            val byId = routineDao.getSlots(dayId).associateBy { it.id }
            val updates = orderedIds.mapIndexedNotNull { index, id ->
                byId[id]?.takeIf { it.orderIndex != index }?.copy(orderIndex = index)
            }
            if (updates.isNotEmpty()) routineDao.updateSlots(updates)
        }
    }

    /**
     * Copies a day with all its slots (and slot target refs), appended at
     * the end of the routine. Returns the new day id.
     */
    suspend fun duplicateDay(dayId: String): String? = database.withTransaction {
        val day = routineDao.getDay(dayId) ?: return@withTransaction null
        val newDayId = UUID.randomUUID().toString()
        routineDao.insertDay(
            day.copy(
                id = newDayId,
                name = "${day.name} (copy)",
                orderIndex = routineDao.nextDayIndex(day.routineId),
            ),
        )
        copySlots(dayId, newDayId)
        newDayId
    }

    /**
     * Copies a whole routine — days, slots, target refs — under a new id and
     * "(copy)" name. Returns the new routine id.
     */
    suspend fun duplicateRoutine(routineId: String): String? = database.withTransaction {
        val routine = routineDao.getRoutine(routineId) ?: return@withTransaction null
        val newRoutineId = UUID.randomUUID().toString()
        routineDao.insertRoutine(
            routine.copy(
                id = newRoutineId,
                name = "${routine.name} (copy)",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        routineDao.getDays(routineId).forEach { day ->
            val newDayId = UUID.randomUUID().toString()
            // Preserve the original day order; ids are fresh so no collision.
            routineDao.insertDay(day.copy(id = newDayId))
            copySlots(day.id, newDayId)
        }
        newRoutineId
    }

    /** Copies a day's slots to [toDayId] with fresh slot ids. Runs inside the caller's transaction. */
    private suspend fun copySlots(fromDayId: String, toDayId: String) {
        routineDao.getSlots(fromDayId).forEach { slot ->
            val newSlotId = UUID.randomUUID().toString()
            routineDao.insertSlot(slot.copy(id = newSlotId, routineDayId = toDayId))
            val targets = routineDao.getSlotTargets(slot.id)
            if (targets.isNotEmpty()) {
                routineDao.insertSlotTargets(targets.map { it.copy(slotId = newSlotId) })
            }
        }
    }
}