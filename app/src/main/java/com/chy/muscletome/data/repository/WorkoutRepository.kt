package com.chy.muscletome.data.repository

import com.chy.muscletome.data.local.dao.CatalogDao
import com.chy.muscletome.data.local.dao.ExerciseSetPoint
import com.chy.muscletome.data.local.dao.RoutineDao
import com.chy.muscletome.data.local.dao.SelectionHistoryDao
import com.chy.muscletome.data.local.dao.UserDao
import com.chy.muscletome.data.local.dao.WorkoutDao
import com.chy.muscletome.data.local.entity.ExerciseSelectionHistoryEntity
import com.chy.muscletome.data.local.entity.SessionSlotResultEntity
import com.chy.muscletome.data.local.entity.SetLogEntity
import com.chy.muscletome.data.local.entity.WorkoutSessionEntity
import com.chy.muscletome.data.local.seed.SeedCatalog
import com.chy.muscletome.domain.model.SelectionReason
import com.chy.muscletome.domain.selection.VarietyEngine
import com.chy.muscletome.domain.model.Difficulty
import com.chy.muscletome.domain.model.MatchStrictness
import com.chy.muscletome.domain.model.SlotType
import com.chy.muscletome.domain.model.TargetMovementType
import com.chy.muscletome.domain.selection.EngineCandidate
import com.chy.muscletome.domain.selection.EngineRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

sealed class StartResult {
    data class Success(val sessionId: String) : StartResult()
    data class NoMatch(val slotLabel: String) : StartResult()
}

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val userDao: UserDao,
    private val catalogDao: CatalogDao,
    private val selectionHistoryDao: SelectionHistoryDao,
    private val varietyEngine: VarietyEngine,
) {
    fun observeSession(id: String) = workoutDao.observeSession(id)

    fun observeOpenSession() =
        workoutDao.observeOpenSession(SeedCatalog.LOCAL_USER_ID)

    fun observeLastCompletedSession() =
        workoutDao.observeLastCompletedSession(SeedCatalog.LOCAL_USER_ID)

    fun observeSlotResults(sessionId: String) = workoutDao.observeSlotResults(sessionId)
    fun observeSets(sessionId: String) = workoutDao.observeSetsForSession(sessionId)

    suspend fun lastSetForExercise(exerciseId: String): SetLogEntity? =
        workoutDao.lastSetForExercise(exerciseId)

    suspend fun bestWeightForExercise(exerciseId: String): Double? =
        workoutDao.bestWeightForExercise(exerciseId)

    suspend fun sessionCountForExercise(exerciseId: String): Int =
        workoutDao.sessionCountForExercise(exerciseId)

    fun observeExerciseSetPoints(exerciseId: String): Flow<List<ExerciseSetPoint>> =
        workoutDao.observeExerciseSetPoints(exerciseId)

    suspend fun startSession(dayId: String): StartResult {
        val existing = workoutDao.findOpenSession(SeedCatalog.LOCAL_USER_ID, dayId)
        if (existing != null) return StartResult.Success(existing.id)

        val slots = routineDao.getSlots(dayId)
        if (slots.isEmpty()) return StartResult.NoMatch("Day has no exercises")

        val user = userDao.getUser(SeedCatalog.LOCAL_USER_ID)
        val available = userDao.getAvailableEquipmentIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val excluded = userDao.getExcludedExerciseIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val muscleGroups = catalogDao.getMuscleGroups()
        val muscleParents = muscleGroups.associate { it.id to it.parentGroupId }
        val catalog = buildCatalog()

        val resolved = mutableListOf<ResolvedSlot>()
        val picked = mutableSetOf<String>()

        for (index in slots.indices) {
            val slot = slots[index]
            val resolvedId: String
            val reason: SelectionReason
            if (slot.type == SlotType.FIXED) {
                resolvedId = slot.exerciseId ?: return StartResult.NoMatch("Fixed slot missing exercise")
                reason = SelectionReason.FIXED
            } else {
                val targets = routineDao.getSlotTargets(slot.id).map { it.muscleGroupId }.toSet()
                val pick = varietyEngine.pickWithFallback(
                    EngineRequest(
                        targetMuscleIds = targets,
                        targetMovementType = slot.targetMovementType,
                        availableEquipmentIds = available,
                        excludedExerciseIds = excluded,
                        alreadyPickedIds = picked,
                        maxDifficulty = user?.maxDifficulty ?: Difficulty.ADVANCED,
                        matchStrictness = user?.primaryMatchStrictness ?: MatchStrictness.LOOSE,
                        preferCompoundEarly = user?.preferCompoundEarly ?: true,
                        slotIndex = index,
                    ),
                    catalog,
                    muscleParents,
                )

                if (pick == null) {
                    val muscleName = targets.firstOrNull()?.let { tid ->
                        muscleGroups.find { it.id == tid }?.name
                    } ?: "Target"
                    return StartResult.NoMatch("$muscleName (${slot.targetMovementType})")
                }
                resolvedId = pick.exercise.id
                reason = SelectionReason.AI_ROTATED
            }
            picked += resolvedId
            resolved += ResolvedSlot(slot.id, resolvedId, reason)
        }

        val sessionId = UUID.randomUUID().toString()
        workoutDao.insertSession(
            WorkoutSessionEntity(
                id = sessionId,
                userId = SeedCatalog.LOCAL_USER_ID,
                routineDayId = dayId,
                startedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        resolved.forEach { res ->
            workoutDao.insertSlotResult(
                SessionSlotResultEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    routineSlotId = res.slotId,
                    resolvedExerciseId = res.exerciseId,
                    selectionReason = res.reason,
                ),
            )
        }
        return StartResult.Success(sessionId)
    }

    private data class ResolvedSlot(
        val slotId: String,
        val exerciseId: String,
        val reason: SelectionReason,
    )

    suspend fun logSet(
        sessionSlotResultId: String,
        setNumber: Int,
        weight: Double,
        reps: Int,
        rpe: Float?,
        restSecondsActual: Int?,
    ) {
        workoutDao.insertSet(
            SetLogEntity(
                id = UUID.randomUUID().toString(),
                sessionSlotResultId = sessionSlotResultId,
                setNumber = setNumber,
                weight = weight,
                reps = reps,
                rpe = rpe,
                restSecondsActual = restSecondsActual,
                completedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun finishSession(sessionId: String) {
        val now = System.currentTimeMillis()
        workoutDao.endSession(sessionId, now)
        workoutDao.getSlotResults(sessionId).forEach { result ->
            recordCompletion(result.resolvedExerciseId, now)
        }
    }

    suspend fun discardSession(sessionId: String) {
        workoutDao.deleteSession(sessionId)
    }

    suspend fun rerollSlot(result: SessionSlotResultEntity, slotIndex: Int): String? {
        val slotId = result.routineSlotId ?: return null
        val user = userDao.getUser(SeedCatalog.LOCAL_USER_ID)
        val available = userDao.getAvailableEquipmentIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val excluded = userDao.getExcludedExerciseIds(SeedCatalog.LOCAL_USER_ID).toSet()
        val targets = routineDao.getSlotTargets(slotId).map { it.muscleGroupId }.toSet()
        if (targets.isEmpty()) return null

        val catalog = buildCatalog()
        val pick = varietyEngine.pick(
            EngineRequest(
                targetMuscleIds = targets,
                targetMovementType = TargetMovementType.ANY,
                availableEquipmentIds = available,
                excludedExerciseIds = excluded,
                alreadyPickedIds = setOf(result.resolvedExerciseId),
                maxDifficulty = user?.maxDifficulty ?: Difficulty.ADVANCED,
                matchStrictness = user?.primaryMatchStrictness ?: MatchStrictness.LOOSE,
                preferCompoundEarly = user?.preferCompoundEarly ?: true,
                slotIndex = slotIndex,
            ),
            catalog,
        ) ?: varietyEngine.pick(
            EngineRequest(
                targetMuscleIds = targets,
                targetMovementType = TargetMovementType.ANY,
                availableEquipmentIds = available,
                excludedExerciseIds = excluded,
                alreadyPickedIds = emptySet(),
                maxDifficulty = Difficulty.ADVANCED,
                matchStrictness = MatchStrictness.LOOSE,
                preferCompoundEarly = true,
                slotIndex = slotIndex,
            ),
            catalog,
        ) ?: return null

        recordReroll(result.resolvedExerciseId)
        workoutDao.updateSlotResult(
            result.copy(
                resolvedExerciseId = pick.exercise.id,
                selectionReason = SelectionReason.USER_REROLL,
            ),
        )
        return pick.exercise.id
    }

    suspend fun overrideSlot(result: SessionSlotResultEntity, exerciseId: String) {
        workoutDao.updateSlotResult(
            result.copy(
                resolvedExerciseId = exerciseId,
                selectionReason = SelectionReason.USER_OVERRIDE,
            ),
        )
    }

    private suspend fun buildCatalog(): List<EngineCandidate> {
        val exercises = catalogDao.getExercises()
        val equipmentLinks = catalogDao.getExerciseEquipment().groupBy { it.exerciseId }
        val secondaryLinks = catalogDao.getSecondaryMuscles().groupBy { it.exerciseId }
        val allHistory = selectionHistoryDao.getAll(SeedCatalog.LOCAL_USER_ID)
            .groupBy { it.exerciseId }

        val lastUsedMap = allHistory.mapValues { (_, rows) -> rows.maxOf { it.lastUsedAtEpochMs } }
        val affinityMap = allHistory.mapValues { (_, rows) -> rows.maxOf { it.affinity } }

        return exercises.map { exercise ->
            EngineCandidate(
                exercise = exercise,
                equipmentIds = equipmentLinks[exercise.id].orEmpty().map { it.equipmentId }.toSet(),
                secondaryMuscleIds = secondaryLinks[exercise.id].orEmpty().map { it.muscleGroupId }.toSet(),
                lastUsedAtEpochMs = lastUsedMap[exercise.id]
                    ?: workoutDao.lastSetForExercise(exercise.id)?.completedAtEpochMs,
                affinity = affinityMap[exercise.id] ?: 0f,
            )
        }
    }

    private suspend fun recordCompletion(exerciseId: String, now: Long) {
        val exercise = catalogDao.getExercises().find { it.id == exerciseId } ?: return
        val muscleId = exercise.primaryMuscleGroupId
        val current = selectionHistoryDao.get(SeedCatalog.LOCAL_USER_ID, exerciseId, muscleId)
        selectionHistoryDao.upsert(
            ExerciseSelectionHistoryEntity(
                userId = SeedCatalog.LOCAL_USER_ID,
                exerciseId = exerciseId,
                muscleGroupId = muscleId,
                lastUsedAtEpochMs = now,
                useCount30d = (current?.useCount30d ?: 0) + 1,
                useCount90d = (current?.useCount90d ?: 0) + 1,
                completedCount = (current?.completedCount ?: 0) + 1,
                rerollCount = current?.rerollCount ?: 0,
                affinity = ((current?.affinity ?: 0f) + 0.15f).coerceAtMost(1f),
            ),
        )
    }

    private suspend fun recordReroll(exerciseId: String) {
        val exercise = catalogDao.getExercises().find { it.id == exerciseId } ?: return
        val muscleId = exercise.primaryMuscleGroupId
        val current = selectionHistoryDao.get(SeedCatalog.LOCAL_USER_ID, exerciseId, muscleId)
        selectionHistoryDao.upsert(
            ExerciseSelectionHistoryEntity(
                userId = SeedCatalog.LOCAL_USER_ID,
                exerciseId = exerciseId,
                muscleGroupId = muscleId,
                lastUsedAtEpochMs = current?.lastUsedAtEpochMs ?: 0L,
                useCount30d = current?.useCount30d ?: 0,
                useCount90d = current?.useCount90d ?: 0,
                completedCount = current?.completedCount ?: 0,
                rerollCount = (current?.rerollCount ?: 0) + 1,
                affinity = ((current?.affinity ?: 0f) - 0.2f).coerceAtLeast(-1f),
            ),
        )
    }
}
